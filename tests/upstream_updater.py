"""Check literal provenance and publication metadata, not a reimplementation of the updater."""
from pathlib import Path
import json,hashlib,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
original=(root/'vendor/inhouse-read/updater-native.methods.txt').read_text()
expected=original.replace('{{','{').replace('}}','}').replace('MainActivity.this','UpdateActivity.this').replace('getBridge().getWebView()','web').replace('inhouse-read-update.apk','ig-calma-update.apk').replace('Allow Inhouse Read','Allow Instagram Calma').replace('InhouseReadUpdate','InhouseCalmaUpdate')
assert expected[expected.index('        private void notifyAppUpdateResult'):] in (root/'app/src/es/calma/instagram/UpdateActivity.java').read_text(), 'Read native progress callbacks must remain copied'
photos=(root/'vendor/inhouse-photos/UpdateInstaller.kt').read_text()
adapted=(root/'app/src/es/calma/instagram/PhotosUpdateInstaller.kt').read_text()
assert photos[photos.index('  private fun download('):photos.index('  private fun publishDownloadProgress(')] in adapted, 'Photos downloader must remain verbatim'
assert photos[photos.index('  private fun validateUrl('):] in adapted, 'Photos verification must remain verbatim'
install=photos[photos.index('  private fun installUpdate('):photos.index('  private fun download(')].replace('private fun installUpdate(', 'fun installUpdate(').replace('MethodChannel.Result','Result').replace('.update-provider','.fileprovider')
assert install in adapted, 'Photos install flow must remain copied with host-only adaptations'
assert 'photosInstaller.installUpdate(url,hash' in (root/'app/src/es/calma/instagram/UpdateActivity.java').read_text()
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
print('PASS: Read callbacks and Photos download/install/verification match upstream sources, CSS byte-identical, manifest version/hash/size match the signed APK, and no installer bridge on Instagram WebView.')
