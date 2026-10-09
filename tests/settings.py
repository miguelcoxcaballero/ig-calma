"""Settings integrate into the page without replacing its original form or overlaying feed."""
from pathlib import Path
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright

script=Path(__file__).resolve().parents[1].joinpath('app/assets/settings.js').read_text()
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    page=browser.new_page(viewport={'width':390,'height':844})
    commands=[]
    original='<main><h1>Editar perfil</h1><form id="original"><label>Nombre<input id="name" value="Miguel"></label><button id="original-save" type="button">Enviar</button></form></main>'
    def route(r):
        query=parse_qs(urlparse(r.request.url).query)
        if 'calma_action' in query:
            commands.append(query); r.abort(); return
        r.fulfill(content_type='text/html',body=original)
    page.route('https://www.instagram.com/**',route)
    page.goto('https://www.instagram.com/')
    page.evaluate('window.CALMA_CONFIG={mode:1,reels:true,limit:20,dmMirror:false};window.__calmaRelations={phase:"ready",following:["alice"],friends:[]};window.syncCalls=0;window.__calmaRelationsController={sync(){window.syncCalls++}}')
    page.evaluate(script)
    assert page.locator('#calma-settings').count()==0, 'No custom controls on home feed'
    page.evaluate('history.pushState({},"","/accounts/edit/");window.__calmaSettings.refresh()')
    assert page.locator('#calma-settings').count()==1
    assert page.locator('#original').is_visible()
    assert not page.evaluate('document.querySelector("#original").contains(document.querySelector("#calma-settings"))'), 'Do not nest settings inside Instagram form'
    page.locator('#name').fill('Original form still works')
    assert page.locator('#name').input_value()=='Original form still works'
    assert page.evaluate('getComputedStyle(document.querySelector("#calma-settings")).position')=='static'
    assert page.evaluate('[...document.querySelectorAll("#calma-settings *")].every(e=>!["fixed","absolute"].includes(getComputedStyle(e).position))')
    page.evaluate(script)
    assert page.locator('#calma-settings').count()==1, 'Reinjection must not duplicate section'
    page.locator('[data-action="sync"]').click()
    assert page.evaluate('window.syncCalls')==1
    page.locator('#calma-mode').select_option('2')
    page.locator('#calma-limit').fill('17')
    page.locator('#calma-reels').uncheck()
    page.locator('#calma-dm').check()
    page.locator('[data-action="save"]').click()
    page.wait_for_timeout(200)
    assert commands[-1]=={'calma_action':['save'],'mode':['2'],'limit':['17'],'reels':['0'],'dm':['1']}
    # Chrome navigates to an error after the mocked abort; Android intercepts before navigation.
    # Restore the settings document with the preferences returned by the native handler.
    page.goto('https://www.instagram.com/accounts/edit/')
    page.evaluate('window.CALMA_CONFIG={mode:2,limit:17,reels:false,dmMirror:true};window.__calmaRelations={phase:"ready",following:["alice"],friends:[]}')
    page.evaluate(script)
    page.evaluate('window.__calmaSettings.saved()')
    assert page.locator('#calma-mode').input_value()=='2' and page.locator('#calma-limit').input_value()=='17'
    assert 'guardados' in page.locator('#calma-saved').inner_text()
    # React replacing the main panel must preserve access to additional settings.
    page.evaluate('document.querySelector("main").outerHTML="<main><h1>Editar perfil</h1></main>"')
    page.wait_for_timeout(250)
    assert page.locator('#calma-settings').count()==1
    page.evaluate('history.pushState({},"","/direct/inbox/");window.__calmaSettings.refresh()')
    assert page.locator('#calma-settings').count()==0, 'No settings controls over DMs'
    page.evaluate('history.pushState({},"","/accounts/login/");window.__calmaSettings.refresh()')
    assert page.locator('#calma-settings').count()==0
    print('PASS: settings-only integration, original form preservation, no overlay, typed save navigation, no duplicates, dynamic panel replacement and no controls over feed/DM/login.')
    browser.close()
