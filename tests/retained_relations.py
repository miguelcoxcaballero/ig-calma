"""A hidden prewarmed inbox reuses Home's account snapshot without duplicate API calls."""
from pathlib import Path
from playwright.sync_api import sync_playwright
import json
script=Path(__file__).resolve().parents[1].joinpath('app/assets/relations.js').read_text()
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    context=browser.new_context();calls=[]
    def route(r):
        if '/api/' not in r.request.url:r.fulfill(body='<main></main>');return
        calls.append(r.request.url)
        r.fulfill(content_type='application/json',body=json.dumps({'status':'ok','users':[{'pk':'2','username':'alice'}]}))
    context.route('https://www.instagram.com/**',route)
    inbox=context.new_page();inbox.goto('https://www.instagram.com/direct/inbox/')
    inbox.evaluate('window.CALMA_CONFIG={owner:"1"};window.CALMA_ACTIVE=false')
    inbox.evaluate(script);inbox.wait_for_timeout(150)
    assert calls==[], 'An inactive inbox must not duplicate relation requests'
    home=context.new_page();home.goto('https://www.instagram.com/')
    home.evaluate('window.CALMA_CONFIG={owner:"1"};window.CALMA_ACTIVE=true');home.evaluate(script)
    home.wait_for_function('window.__calmaRelations.friendsReady')
    inbox.wait_for_function('window.__calmaRelations.friendsReady')
    assert len(calls)==2
    inbox.evaluate('window.CALMA_ACTIVE=true;document.dispatchEvent(new CustomEvent("calma-visibility",{detail:{active:true}}))')
    inbox.wait_for_timeout(150)
    assert len(calls)==2 and inbox.evaluate('window.__calmaRelations.friends')==['alice']
    inbox.evaluate('window.__calmaRelationsController.refreshIdentity("3")')
    inbox.wait_for_function('window.__calmaRelations.owner==="3" && window.__calmaRelations.friendsReady')
    assert len(calls)==4, 'An account switch must obtain that account’s own graph'
    print('PASS: inactive prewarm performs no relation requests, shared complete cache adopted, activation stays warm, account changes synchronize separately.')
    browser.close()
