"""Publish the signed APK and the static manifest consumed by the reused updater."""
import hashlib,json,shutil,xml.etree.ElementTree as ET
from pathlib import Path

root=Path(__file__).resolve().parents[1]
manifest=ET.parse(root/'app/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
version=manifest.get(ns+'versionName')
code=int(manifest.get(ns+'versionCode'))
apk=root/'dist'/f'IG-Calma-{version}.apk'
if not apk.is_file() or apk.stat().st_size<100000:
    raise SystemExit('The signed APK is missing or below the original Inhouse updater size minimum.')
out=root/'downloads';out.mkdir(exist_ok=True)
shutil.copy2(apk,out/apk.name)
info={'version':version,'versionCode':code,'required':True,'apkUrl':f'https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/main/downloads/{apk.name}',
      'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'apkSizeBytes':apk.stat().st_size,
      'releaseNotes':'Actualizador corregido: comprobación inmediata al abrir, popup original de Inhouse Read e instalador Kotlin reutilizado de Inhouse Photos.'}
(root/'android-update.json').write_text(json.dumps(info,indent=2,ensure_ascii=False)+'\n')
(out/'SHA256SUMS.txt').write_text('\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name for p in sorted(out.glob('*.apk')))+'\n')
