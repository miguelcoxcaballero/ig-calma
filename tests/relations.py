"""Authenticated-session behavior using mock same-origin responses; no real account."""
import json
from pathlib import Path
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright

SCRIPT = Path(__file__).resolve().parents[1].joinpath('app/assets/relations.js').read_text()
FILTER = Path(__file__).resolve().parents[1].joinpath('app/assets/filter.js').read_text()

with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    calls=[]
    context=browser.new_context()
    page=context.new_page()
    def route(r):
        u=urlparse(r.request.url)
        if not u.path.startswith('/api/'):
            r.fulfill(content_type='text/html',body='<main><article><header><a href="/alice/">alice</a></header><a href="/p/a/">Time</a></article><article id="charlie"><header><a href="/charlie/">charlie</a></header><a href="/p/c/">Time</a></article></main>')
            return
        calls.append(r.request.url)
        assert r.request.headers.get('x-ig-app-id')=='936619743392459'
        if '/9/' in u.path:
            r.fulfill(status=429,content_type='application/json',body='{}');return
        if '/8/' in u.path:
            data={'status':'ok','users':[{'pk':'8','username':'newfriend'}]}
        elif '/following/' in u.path:
            cursor=parse_qs(u.query).get('max_id')
            data={'status':'ok','users':[{'pk':'2','username':'ALICE'}],'next_max_id':'cursor=+/'} if not cursor else {'status':'ok','users':[{'pk':'3','username':'charlie'}]}
            if cursor: assert cursor==['cursor=+/']
        else:
            data={'status':'ok','users':[{'pk':'2','username':'alice'},{'pk':'4','username':'bob'}]}
        r.fulfill(content_type='application/json',body=json.dumps(data))
    context.route('https://www.instagram.com/**',route)
    page.goto('https://www.instagram.com/')
    page.evaluate('window.CALMA_CONFIG={owner:"1",mode:2,reels:true,limit:20}')
    page.evaluate(SCRIPT)
    page.evaluate(FILTER)
    page.wait_for_function('window.__calmaRelations.followingReady')
    assert page.evaluate('window.__calmaRelations.phase')=='syncing', 'Following should become available before all pagination/followers finish'
    page.wait_for_function('window.__calmaRelations.phase==="ready"')
    state=page.evaluate('window.__calmaRelations')
    assert state['following']==['alice','charlie'] and state['friends']==['alice']
    assert not page.locator('#charlie').is_visible(), 'Mutual mode must use intersection, not union'
    assert len(calls)==3
    page.evaluate('window.__calmaRelationsController.refreshIdentity("8")')
    page.wait_for_function('window.__calmaRelations.phase==="ready" && window.__calmaRelations.owner==="8"')
    assert page.evaluate('window.__calmaRelations.friends')==['newfriend'], 'Account switch must clear previous graph'
    assert page.evaluate('window.__calma.count()')==0, 'Account switch must reset the post session'
    page.evaluate('window.__calmaRelationsController.refreshIdentity("")')
    assert page.evaluate('window.__calmaRelations.followingReady') is False
    assert page.evaluate('window.__calmaRelations.following')==[]
    count=len(calls)
    page.wait_for_timeout(800)
    assert len(calls)==count, 'Logout must stop querying'
    page.evaluate('window.__calmaRelationsController.refreshIdentity("1")')
    assert page.evaluate('window.__calmaRelations.friends')==['alice']
    assert len(calls)==count, 'Fresh per-account cache should avoid extra API calls'
    page.evaluate('window.__calmaRelations.updated=0;window.__calmaRelationsController.refreshIdentity("1")')
    page.wait_for_function('window.__calmaRelations.phase==="ready" && window.__calmaRelations.updated>0')
    assert len(calls)==count+3, 'An open document must refresh a stale snapshot automatically'
    page.evaluate('window.__calmaRelationsController.refreshIdentity("9")')
    page.wait_for_function('window.__calmaRelations.phase==="error"')
    assert page.evaluate('window.__calmaRelations.followingReady') is False
    count=len(calls)
    page.wait_for_timeout(1700)
    assert len(calls)==count, 'Rate limit must not cause a retry loop'
    assert 'No se pudo sincronizar' in page.locator('#calma-end').inner_text()
    context.close()
    # Followers denied after following completed: never expose a partial mutual set.
    context=browser.new_context()
    page=context.new_page()
    def reject_followers(r):
        if '/api/' not in r.request.url:r.fulfill(body='<main></main>');return
        if '/followers/' in r.request.url:r.fulfill(status=403,body='{}');return
        r.fulfill(content_type='application/json',body=json.dumps({'status':'ok','users':[{'pk':'2','username':'alice'}]}))
    context.route('https://www.instagram.com/**',reject_followers)
    page.goto('https://www.instagram.com/')
    page.evaluate('window.CALMA_CONFIG={owner:"1"}')
    page.evaluate(SCRIPT)
    page.wait_for_function('window.__calmaRelations.phase==="error"')
    state=page.evaluate('window.__calmaRelations')
    assert state['followingReady'] and not state['friendsReady'] and state['friends']==[]
    print('PASS: automatic pagination, numeric-ID mutual intersection, filter integration, account isolation, logout, cache, rate-limit stop and partial-sync rejection.')
    browser.close()
