#!/usr/bin/env python3
"""Exercise the updater actually shipped in an older native APK against release metadata.

Raw manifest unavailable or stale must still offer the exact public APK through
Read's original GitHub latest-release recovery. The browser models bridge calls;
it does not establish popup presentation on a physical Android device.
"""
import argparse
import json
from pathlib import Path
from urllib.parse import urlsplit
from zipfile import ZipFile
from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--installed-apk', type=Path, required=True)
parser.add_argument('--installed-version', required=True)
parser.add_argument('--release-json', type=Path, required=True)
args = parser.parse_args()
release = json.loads(args.release_json.read_text())
version = release['tag_name'].removeprefix('v')
assert not release['draft'] and not release['prerelease']
assert tuple(map(int, version.split('.'))) > tuple(map(int, args.installed_version.split('.')))
asset = next(a for a in release['assets'] if a['name'] == f'IG-Calma-{version}.apk')
with ZipFile(args.installed_apk) as apk:
    assets = {'/' + n.removeprefix('assets/'): apk.read(n) for n in apk.namelist()
              if n.startswith('assets/updates/') and not n.endswith('/')}
assert b'/native-instagram/native/android-update.json' in assets['/updates/updater.js']
origin = 'https://appassets.androidplatform.net'
latest = 'https://api.github.com/repos/miguelcoxcaballero/ig-calma/releases/latest'

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])
    for quiet in (False, True):
        for failure in ('unreachable', 'stale'):
            context = browser.new_context()
            context.add_init_script('''
                window.offers=[];window.installs=[];
                window.InhouseNative={getAppVersion(){return VERSION},installAppUpdate(url,sha){window.installs.push({url,sha})}};
                window.InhouseUpdateHost={offer(value){window.offers.push(JSON.parse(value))}};
            '''.replace('VERSION', json.dumps(args.installed_version)))
            requests = []
            def route(r):
                u = urlsplit(r.request.url)
                bare = u._replace(query='', fragment='').geturl()
                requests.append(bare)
                if u.netloc == 'raw.githubusercontent.com':
                    if failure == 'unreachable': r.abort()
                    else:
                        r.fulfill(content_type='application/json', headers={'Access-Control-Allow-Origin': '*'},
                                  body=json.dumps({'version': args.installed_version, 'required': True,
                                      'apkUrl': asset['browser_download_url'], 'apkSha256': asset['digest'].removeprefix('sha256:')}))
                elif bare == latest:
                    r.fulfill(content_type='application/json', headers={'Access-Control-Allow-Origin': '*'}, body=json.dumps(release))
                elif u.netloc == 'appassets.androidplatform.net' and u.path in assets:
                    mime = 'application/javascript' if u.path.endswith('.js') else 'text/css' if u.path.endswith('.css') else 'text/html'
                    r.fulfill(content_type=mime, body=assets[u.path])
                else: r.abort()
            context.route('**/*', route)
            page = context.new_page()
            page.goto(origin + '/updates/index.html?inhouse_app=1' + ('&quiet=1' if quiet else ''))
            page.wait_for_selector('#android-update-gate', state='attached', timeout=5000)
            assert latest in requests
            if quiet:
                page.wait_for_function('window.offers.length > 0', timeout=3000)
                offer = page.evaluate('window.offers[0]')
                assert offer['version'] == version and offer['apkUrl'] == asset['browser_download_url']
                assert offer['apkSha256'] == asset['digest'].removeprefix('sha256:')
            else:
                page.locator('#android-update-gate [data-update-install]').click()
                page.wait_for_function('window.installs.length > 0', timeout=3000)
                assert page.evaluate('window.installs[0]') == {'url': asset['browser_download_url'], 'sha': asset['digest'].removeprefix('sha256:')}
            print(f'PASS: shipped {args.installed_version}, {failure} manifest, {"startup offer" if quiet else "manual popup and install"}')
            context.close()
    browser.close()
