"""Exercise the original Inhouse Read update UI, with native installer callbacks mocked."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
assets=root/'app/assets/updates'
manifest={'version':'0.3.3','versionCode':7,'required':True,'apkUrl':'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/main/downloads/IG-Calma-0.3.3.apk','apkSha256':'a'*64,'apkSizeBytes':500000}
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    context=browser.new_context()
    context.add_init_script('window.installs=[];window.offers=0;window.InhouseNative={getAppVersion(){return "0.3.2"},installAppUpdate(url,sha){window.installs.push({url,sha});window.handleInhouseUpdateResult({status:"permission_required"})}};window.InhouseUpdateHost={offer(){window.offers++}}')
    def route(r):
        if 'android-update.json' in r.request.url:
            r.fulfill(content_type='application/json',headers={'Access-Control-Allow-Origin':'*'},body=json.dumps(manifest));return
        if 'api.github.com' in r.request.url:r.fulfill(status=404,body='{}');return
        name=r.request.url.split('/updates/')[-1].split('?')[0]
        path=assets/name
        r.fulfill(content_type='application/javascript' if name.endswith('.js') else 'text/css' if name.endswith('.css') else 'text/html',body=path.read_text())
    context.route('**/*',route)
    page=context.new_page();page.goto('https://appassets.androidplatform.net/updates/index.html?inhouse_app=1')
    page.wait_for_selector('#android-update-gate')
    assert 'Versión 0.3.2' in page.locator('[data-update-message]').inner_text()
    page.locator('[data-update-install]').click()
    assert page.evaluate('window.installs[0]')=={'url':manifest['apkUrl'],'sha':manifest['apkSha256']}
    assert 'Permitir desde esta fuente' in page.locator('[data-update-status]').inner_text()
    assert page.locator('[data-update-install]').is_enabled()
    page.evaluate('window.handleInhouseUpdateResult({status:"downloading",downloadedBytes:250000,totalBytes:500000})')
    assert page.locator('[data-update-percent]').inner_text()=='50%'
    assert page.locator('[data-update-install]').is_disabled()
    assert page.locator('[data-update-progressbar]').get_attribute('aria-valuenow')=='50'
    page.evaluate('window.handleInhouseUpdateResult({status:"ready",downloadedBytes:500000,totalBytes:500000})')
    assert 'Confirma la instalación' in page.locator('[data-update-status]').inner_text()
    page.evaluate('window.handleInhouseUpdateResult({status:"error",message:"Checksum inválido"})')
    assert page.locator('[data-update-install]').inner_text()=='Reintentar'
    assert 'Checksum inválido' in page.locator('[data-update-status]').inner_text()
    assert page.evaluate('InhouseUpdater.compareSemanticVersions("0.10.0","0.9.9")')>0
    assert page.evaluate('(()=>{try{InhouseUpdater.validateManifest({version:"1.0.0",apkUrl:"https://evil.example/app.apk"});return false}catch(e){return true}})()')
    page.goto('https://appassets.androidplatform.net/updates/index.html?inhouse_app=1&quiet=1')
    page.wait_for_function('() => window.offers>0')
    manifest['version']='0.3.2'
    page.goto('https://appassets.androidplatform.net/updates/index.html?inhouse_app=1')
    page.wait_for_function('() => document.querySelector("#check-status").textContent.includes("última versión")')
    assert page.locator('#android-update-gate').count()==0
    assert (assets/'android-update.css').read_bytes()==(root/'vendor/inhouse-read/android-update.css').read_bytes()
    print('PASS: reused Inhouse UI, version detection, exact download URL/hash, installation permission, download progress, ready/error states, host validation, automatic offers and current-version detection; original CSS unchanged.')
    browser.close()
