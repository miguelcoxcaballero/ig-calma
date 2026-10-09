"""Check literal provenance and publication metadata, not a reimplementation of the updater."""
from pathlib import Path
import json,hashlib,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
original=(root/'vendor/inhouse-read/updater-native.methods.txt').read_text()
expected=original.replace('{{','{').replace('}}','}').replace('MainActivity.this','UpdateActivity.this').replace('getBridge().getWebView()','web').replace('inhouse-read-update.apk','ig-calma-update.apk').replace('Allow Inhouse Read','Allow Instagram Calma').replace('InhouseReadUpdate','InhouseCalmaUpdate')
assert expected in (root/'app/src/es/calma/instagram/UpdateActivity.java').read_text(), 'Installer source must remain copied, not rewritten'
assert (root/'vendor/inhouse-read/android-update.css').read_bytes()==(root/'app/assets/updates/android-update.css').read_bytes()
info=json.loads((root/'android-update.json').read_text())
apk=root/'downloads'/f'IG-Calma-{info["version"]}.apk'
assert info['apkSha256']==hashlib.sha256(apk.read_bytes()).hexdigest()
assert info['apkSizeBytes']==apk.stat().st_size
manifest=ET.parse(root/'app/AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
assert info['version']==manifest.get(ns+'versionName') and info['versionCode']==int(manifest.get(ns+'versionCode'))
assert info['apkUrl'].endswith('/'+apk.name) and info['required'] is True
main=(root/'app/src/es/calma/instagram/MainActivity.java').read_text()
assert 'web.addJavascriptInterface' not in main, 'Never expose an installer bridge to Instagram'
print('PASS: native installer matches upstream fragment, CSS byte-identical, manifest version/hash/size match the signed APK, and no installer bridge on Instagram WebView.')
