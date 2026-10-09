"""Inline themed settings preserve Instagram's form and avoid native pop-up controls."""
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
    def install(url='https://www.instagram.com/', config=None):
        page.goto(url)
        page.evaluate('(config)=>{window.CALMA_CONFIG=config;window.__calmaRelations={phase:"ready",following:["alice"],friends:[]};window.syncCalls=0;window.__calmaRelationsController={sync(){window.syncCalls++}}}', config or {'mode':1,'reels':True,'limit':20,'dmMirror':False})
        page.evaluate(script)
    def navigate(path):
        page.evaluate('(path)=>{history.pushState({},"",path);document.dispatchEvent(new CustomEvent("calma-route",{bubbles:true}));}',path)
        page.wait_for_timeout(100)
    install()
    assert page.locator('#calma-settings').count()==0, 'No custom controls on home feed'
    navigate('/accounts/edit/')
    assert page.locator('#calma-settings').count()==1
    assert page.locator('#original').is_visible()
    assert not page.evaluate('document.querySelector("#original").contains(document.querySelector("#calma-settings"))'), 'Do not nest settings inside Instagram form'
    page.locator('#name').fill('Original form still works')
    assert page.locator('#name').input_value()=='Original form still works'
    assert page.evaluate('getComputedStyle(document.querySelector("#calma-settings")).position')=='static'
    assert page.evaluate('[...document.querySelectorAll("#calma-settings *")].every(e=>!["fixed","absolute"].includes(getComputedStyle(e).position))')
    assert page.locator('#calma-settings select,#calma-settings input[type=number]').count()==0, 'No platform picker or number controls'
    assert page.get_by_role('switch').count()==2
    assert page.get_by_role('radio').count()==3
    assert page.locator('#calma-permission-row').is_hidden()
    assert page.locator('[data-action="save"]').is_disabled()
    page.evaluate(script)
    assert page.locator('#calma-settings').count()==1, 'Reinjection must not duplicate section'
    page.locator('[data-action="sync"]').click()
    assert page.evaluate('window.syncCalls')==1
    page.get_by_role('radio',name='Amigos').check()
    page.locator('#calma-limit').fill('17')
    page.locator('#calma-reels').uncheck()
    page.locator('#calma-dm').check()
    assert page.locator('#calma-permission-row').is_visible()
    assert page.locator('[data-action="permissions"]').is_visible()
    # Status refresh/reinjection and React replacing the host must not discard unsaved choices.
    page.evaluate('window.__calmaSettings.refresh();document.dispatchEvent(new Event("calma-relations"));')
    assert page.get_by_role('radio',name='Amigos').is_checked()
    assert page.locator('#calma-limit').input_value()=='17'
    page.evaluate('document.querySelector("main").outerHTML="<main><h1>Editar perfil</h1></main>"')
    page.wait_for_timeout(160)
    assert page.locator('#calma-limit').input_value()=='17'
    assert page.get_by_role('radio',name='Amigos').is_checked()
    # Invalid limits use a local inline message, and steppers stop at each boundary.
    page.locator('#calma-limit').fill('0')
    page.locator('[data-action="save"]').click()
    assert not commands and page.locator('#calma-limit-error').is_visible()
    assert page.locator('#calma-limit').get_attribute('aria-invalid')=='true'
    page.locator('#calma-limit').fill('1')
    assert page.locator('[data-step="-1"]').is_disabled()
    page.locator('[data-step="1"]').click()
    assert page.locator('#calma-limit').input_value()=='2'
    page.locator('#calma-limit').fill('100')
    assert page.locator('[data-step="1"]').is_disabled()
    page.locator('[data-step="-1"]').click()
    assert page.locator('#calma-limit').input_value()=='99'
    page.locator('#calma-limit').fill('17')
    page.locator('[data-action="save"]').click()
    page.wait_for_timeout(200)
    assert commands[-1]=={'calma_action':['save'],'mode':['2'],'limit':['17'],'reels':['0'],'dm':['1']}
    # Chrome navigates to an error after the mocked abort; Android intercepts before navigation.
    install('https://www.instagram.com/accounts/edit/', {'mode':2,'limit':17,'reels':False,'dmMirror':True,'notificationsEnabled':True,'listenerEnabled':True})
    page.evaluate('window.__calmaSettings.saved()')
    assert page.get_by_role('radio',name='Amigos').is_checked() and page.locator('#calma-limit').input_value()=='17'
    assert page.locator('#calma-saved').inner_text()=='Guardado'
    assert page.locator('[data-action="save"]').is_disabled()
    assert page.locator('[data-action="permissions"]').is_hidden()
    assert page.locator('#calma-permissions').inner_text()=='Permiso activado'
    # Repeating the same status must not replace text nodes or reset focused inputs.
    page.evaluate('window.statusMutations=0;window.statusObserver=new MutationObserver(r=>window.statusMutations+=r.length);window.statusObserver.observe(document.querySelector("#calma-settings"),{childList:true,subtree:true});window.__calmaSettings.updateStatus();window.__calmaSettings.updateStatus();')
    page.wait_for_timeout(100)
    assert page.evaluate('window.statusMutations')==0
    page.evaluate('window.statusObserver.disconnect()')
    # A retained background tab does no remount work until native marks it active again.
    page.evaluate('window.CALMA_ACTIVE=false;document.dispatchEvent(new CustomEvent("calma-visibility",{detail:{active:false},bubbles:true}));document.querySelector("main").innerHTML="<h1>Editar perfil</h1>"')
    page.wait_for_timeout(150)
    assert page.locator('#calma-settings').count()==0
    page.evaluate('window.CALMA_ACTIVE=true;document.dispatchEvent(new CustomEvent("calma-visibility",{detail:{active:true},bubbles:true}))')
    page.wait_for_timeout(150)
    assert page.locator('#calma-settings').count()==1
    # Every control fits a narrow screen; theme colors stay readable in either mode.
    page.set_viewport_size({'width':320,'height':700})
    assert page.evaluate('document.querySelector("#calma-settings").scrollWidth<=document.querySelector("#calma-settings").clientWidth')
    page.evaluate('document.documentElement.style.setProperty("--calma-text","#f5f5f5");document.documentElement.style.setProperty("--calma-background","#000");document.documentElement.style.setProperty("--calma-border","#363636")')
    assert page.evaluate('getComputedStyle(document.querySelector("#calma-settings")).backgroundColor')=='rgb(0, 0, 0)'
    page.get_by_role('radio',name='Amigos').focus()
    page.keyboard.press('ArrowUp')
    assert page.get_by_role('radio',name='Cuentas que sigues').is_checked(), 'Radio choices remain keyboard accessible'
    navigate('/direct/inbox/')
    assert page.locator('#calma-settings').count()==0, 'No settings controls over DMs'
    navigate('/accounts/login/')
    assert page.locator('#calma-settings').count()==0
    navigate('/accounts/edit/')
    page.locator('[data-action="update"]').click()
    page.wait_for_timeout(200)
    assert commands[-1]=={'calma_action':['update']}
    print('PASS: inline accessible controls, no pickers/overlays, typed navigation, limit validation, draft preservation, route mounting, inactive pause, stable status, themes and updater action.')
    browser.close()
