#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
bt="$PWD/tools/android-15"
platform="$PWD/tools/android-35/android.jar"
rm -rf build/classes build/dex
mkdir -p build/classes build/dex dist
python3 scripts/vendor-updater.py
"$bt/aapt" package -f -M app/AndroidManifest.xml -S app/res -A app/assets -I "$platform" -F build/resources.apk
java -jar tools/ecj.jar -source 1.8 -target 1.8 -proc:none -classpath "$platform:tools/androidx-core.jar" -d build/classes app/src/es/calma/instagram/*.java
python3 - <<'PY'
import pathlib,zipfile
with zipfile.ZipFile('build/classes.jar','w') as archive:
 for path in pathlib.Path('build/classes').rglob('*.class'):
  archive.write(path,path.relative_to('build/classes'))
PY
"$bt/d8" --min-api 26 --lib "$platform" --output build/dex build/classes.jar tools/androidx-core.jar
cp build/resources.apk build/unsigned.apk
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as archive:
 archive.write('build/dex/classes.dex','classes.dex')
PY
"$bt/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f build/local-signing.p12 ]; then
 keytool -genkeypair -keystore build/local-signing.p12 -storepass localbuild -keypass localbuild -alias calma -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=IG Calma local build" -storetype PKCS12
fi
"$bt/apksigner" sign --ks build/local-signing.p12 --ks-pass pass:localbuild --key-pass pass:localbuild --out dist/IG-Calma-0.3.3.apk build/aligned.apk
"$bt/apksigner" verify --verbose dist/IG-Calma-0.3.3.apk
"$bt/aapt" dump badging dist/IG-Calma-0.3.3.apk
(cd dist && sha256sum IG-Calma-0.3.3.apk > SHA256SUMS.txt)

python3 scripts/publish-update-manifest.py
