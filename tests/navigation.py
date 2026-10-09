"""Tab handoffs preserve Instagram's own same-tab router and private message controls."""
from pathlib import Path
from playwright.sync_api import sync_playwright
script=Path(__file__).resolve().parents[1].joinpath('app/assets/navigation.js').read_text()
html='''<meta charset="utf-8"><main><a id="home" href="/">Home</a><a id="dm" href="/direct/inbox/">DM</a><a id="thread" href="/direct/t/123/">Thread</a><a id="profile" href="/alice/">Alice</a><textarea id="draft">Unsent</textarea><video id="clip"></video></main>'''
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    context=browser.new_context()
    commands=[]
    def route(r):
        if 'calma_tab=' in r.request.url:commands.append(r.request.url);r.fulfill(status=204,body="");return
        r.fulfill(content_type='text/html; charset=utf-8',body=html)
    context.route('https://www.instagram.com/**',route)
    page=context.new_page();page.goto('https://www.instagram.com/')
    page.evaluate('window.CALMA_CONFIG={owner:"1"};window.CALMA_TAB="home"')
    page.evaluate(script)
    page.evaluate('window.routerClicks=[];document.addEventListener("click",e=>{const a=e.target.closest("a");if(a){window.routerClicks.push(a.id);e.preventDefault();history.pushState({},"",a.href)}})')
    page.locator('#profile').click()
    assert page.url.endswith('/alice/') and page.evaluate('routerClicks')==['profile']
    page.locator('#home').click()
    assert page.url.endswith('/') and page.evaluate('routerClicks')==['profile','home']
    page.locator('#dm').click()
    page.wait_for_timeout(100)
    assert len(commands)==1 and 'calma_tab=direct' in commands[0]
    assert page.locator('#draft').input_value()=='Unsent'
    page.evaluate('window.CALMA_TAB="direct";history.replaceState({},"","/direct/inbox/")')
    page.locator('#thread').click()
    assert page.url.endswith('/direct/t/123/')
    page.locator('#home').click();page.wait_for_timeout(100)
    assert len(commands)==2 and 'calma_tab=home' in commands[1]
    page.evaluate('window.pauses=0;document.querySelector("video").pause=()=>window.pauses++;document.querySelector("textarea").focus();window.CALMA_ACTIVE=false;document.dispatchEvent(new CustomEvent("calma-visibility",{detail:{active:false}}))')
    assert page.evaluate('window.pauses')==1
    assert page.evaluate('document.activeElement.tagName')!='TEXTAREA'
    assert page.locator('#draft').input_value()=='Unsent', 'Pause must retain drafts'
    page.evaluate('window.__calmaRelations={owner:""}')
    page.locator('#home').click()
    assert len(commands)==2, 'Logged-out SPA must not issue a retained-tab command'
    print('PASS: retained-tab handoff, same-tab SPA routing, thread links, draft preservation, inactive media pause and logout gate.')
    browser.close()
