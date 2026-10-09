# Compilación de Instagram Calma nativo

Este directorio adapta el APK Android original de Instagram con cambios propios de
Calma. El feed, las historias y los mensajes los ejecuta Instagram. El código
propio de Calma usa WebView únicamente para el actualizador local de Inhouse.

Los cambios funcionales están en [`patches/`](patches/) y [`src/`](src/). No se
incluyen paquetes de parches de Piko ni de otros proyectos. Morphe se utiliza como
herramienta genérica para unir APK, modificar DEX y reconstruir recursos. Kotlin,
AndroidX y los componentes Inhouse conservan sus respectivas procedencias.

## Entrada y versiones

La entrada aceptada está fijada en [`stock.lock.json`](stock.lock.json):

| Propiedad | Valor |
| --- | --- |
| Instagram original | `439.0.0.37.89`, versionCode `384510827` |
| Arquitectura | `arm64-v8a` |
| Android mínimo | Android 9, API 28 |
| Formato de entrada | APKM con `base.apk` y `split_config.xxhdpi.apk` |
| Procedencia | [Variante de APKMirror](https://www.apkmirror.com/apk/instagram/instagram-instagram/instagram-439-0-0-37-89-release/instagram-439-0-0-37-89-4-android-apk-download/) |
| Versión propia de Calma | `0.4.1`, definida en [`CalmaBuild.java`](src/es/calma/instagram/nativeapp/CalmaBuild.java) |
| Paquete resultante | `es.calma.instagram` |
| versionCode resultante | `384510829` |

El manifiesto del APK conserva `versionName=439.0.0.37.89` para Instagram. La
comparación de actualizaciones utiliza `CalmaBuild.VERSION`, actualmente `0.4.1`.
Son versiones distintas con funciones distintas.

El build verifica el SHA-256 del APKM y de cada split, además de la cadena de firma
original registrada en el archivo de bloqueo. No acepta automáticamente otra
variante, arquitectura o revisión de Instagram. Las comprobaciones de firmas usan
Build Tools 36 para reconocer también la rotación de certificados según la versión
de Android. El APK original no se guarda en el repositorio.

## Preparar las herramientas

Ejecutar los comandos desde la raíz del repositorio. El entorno utilizado es Linux
con Java 21 y Python 3.11 o posterior. El proceso Morphe reserva hasta 8 GiB de heap;
la máquina necesita memoria adicional para el sistema y los otros compiladores.

La instalación completa del entorno todavía no está automatizada. Los scripts
reutilizan estas rutas del proyecto anterior:

| Ruta | Contenido utilizado |
| --- | --- |
| `tools/ecj.jar` | Eclipse Java Compiler `3.39.0` |
| `tools/android-35/android.jar` | Plataforma Android SDK 35 |
| `tools/android-15/` | Build Tools `35.0.0`, incluidos `d8` y su directorio `lib/` |
| `tools/androidx-core.jar` | `classes.jar` de AndroidX Core `1.13.1` |
| `tools/kotlin/` | Compilador y runtime preparados por `scripts/setup-kotlin.py` |

Además se necesita un directorio completo de Build Tools `36.0.0` o posterior para
verificar las firmas, alinear y firmar el APK final. La ruta histórica
`tools/android-15` sigue siendo la que usa `compile-extension.py`; pasar
`--build-tools` al build principal no cambia esa ruta interna.

Si esas rutas ya están preparadas, conservarlas. En un checkout limpio, con las
herramientas de línea de comandos del SDK instaladas, puede prepararse así:

```sh
# Cambiar esta ruta por el SDK local; debe ser absoluta.
CALMA_SDK=/ruta/absoluta/al/android-sdk
sdkmanager --sdk_root="$CALMA_SDK" \
  'platforms;android-35' 'build-tools;35.0.0' 'build-tools;36.0.0'
mkdir -p tools
ln -s "$CALMA_SDK/platforms/android-35" tools/android-35
ln -s "$CALMA_SDK/build-tools/35.0.0" tools/android-15

python3 - <<'PY'
from io import BytesIO
from pathlib import Path
from urllib.request import urlopen
from zipfile import ZipFile

root = Path('tools')
ecj = 'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar'
core = 'https://dl.google.com/dl/android/maven2/androidx/core/core/1.13.1/core-1.13.1.aar'
(root / 'ecj.jar').write_bytes(urlopen(ecj).read())
with ZipFile(BytesIO(urlopen(core).read())) as archive:
    (root / 'androidx-core.jar').write_bytes(archive.read('classes.jar'))
PY

python3 scripts/setup-kotlin.py
```

Los enlaces del ejemplo requieren que las rutas de destino todavía no existan.
El SDK usa su instalador oficial; ECJ, AndroidX y Kotlin se obtienen de sus
repositorios Maven. Sus versiones están indicadas arriba; el script de preparación
de Kotlin fija las versiones de sus dependencias, pero no implementa una
verificación de SHA-256 de esas descargas.

Morphe y las herramientas opcionales de análisis están fijados, con URL y SHA-256,
en [`tools.lock.json`](tools.lock.json). Descargar el Morphe oficial y comprobarlo:

```sh
python3 - <<'PY'
import hashlib
import json
from pathlib import Path
from urllib.request import urlopen

tool = json.loads(Path('native/tools.lock.json').read_text())['morphe']
target = Path('native/downloads') / tool['url'].rsplit('/', 1)[-1]
target.parent.mkdir(parents=True, exist_ok=True)
data = urlopen(tool['url']).read()
assert hashlib.sha256(data).hexdigest() == tool['sha256'], 'SHA-256 de Morphe incorrecto'
target.write_bytes(data)
print(target)
PY
```

JADX `1.5.6` y Apktool `3.0.3` sirven para investigar el APK; no son requisitos del
build normal. Si se utilizan, descargar los archivos de sus URLs oficiales en el
mismo archivo de bloqueo y verificar sus hashes. No descargar un bundle de parches
para sustituir el código propio.

## Construir y firmar

Se necesita la clave privada original de Calma, normalmente
`build/local-signing.p12`, con alias `calma`. No está incluida en Git. El pipeline
rechaza su ausencia y no crea otra clave. La verificación final exige el certificado
original para que el APK pueda actualizar instalaciones anteriores de Calma.
`CALMA_KEYSTORE_PASSWORD` permite suministrar su contraseña mediante el entorno.

Guardar localmente el APKM exacto registrado en `stock.lock.json`. Después:

```sh
python3 native/scripts/build.py \
  --stock-apkm /ruta/al/instagram-439.0.0.37.89.apkm \
  --morphe native/downloads/morphe-desktop-1.18.1-all.jar \
  --build-tools "$CALMA_SDK/build-tools/36.0.0" \
  --keystore build/local-signing.p12
```

`--merged /ruta/al/instagram-stock-merged.apk` permite reutilizar una unión previa
solo si su hash coincide con `verifiedMergedSha256`. El APKM original sigue siendo
obligatorio para comprobar sus splits y firmas.

[`scripts/build.py`](scripts/build.py) verifica la entrada, une los splits, compila
la extensión y el bundle propio y aplica los parches con Morphe en modo `FULL`.
Después restaura las 20 particiones DEX originales, añade Calma en `classes21.dex`,
regenera los metadatos del cargador, alinea a **16 KB**, firma y verifica el resultado. Solo carga
`native/build/calma-patches.jar`. No descarga ni aplica parches funcionales externos.

Los resultados quedan en:

- `native/build/dist/IG-Calma-0.4.1.apk`.
- `native/build/dist/SHA256SUMS.txt`, `build-info.json` y `verification.json`.
- `native/build/patch-result.json` y `native/build/extension/build-report.json`.
- `android-update.json` y `native/android-update.json`, idénticos y generados a partir del APK firmado.

El build genera los archivos locales; no publica una release. El manifiesto de
actualización apunta al APK de la release `v0.4.1` del repositorio configurado en el
script. La publicación debe adjuntar exactamente ese APK y mantener su versión,
tamaño y SHA-256 sincronizados con el manifiesto. Los dos manifiestos usan
`required: true`; con `false`, Inhouse Read no ofrece la actualización. Publicar
el manifiesto raíz en `main` y el nativo en `native-instagram` para alcanzar las
dos URLs que consultan las APK ya instaladas.

Para depurar solo la compilación, sin firmar ni reconstruir Instagram:

```sh
python3 native/scripts/compile-extension.py --stock /ruta/al/base.apk
python3 native/scripts/compile-patches.py \
  --morphe native/downloads/morphe-desktop-1.18.1-all.jar \
  --extension native/build/classes.dex
```

El bundle puede listar sus parches con:

```sh
java -jar native/downloads/morphe-desktop-1.18.1-all.jar \
  list-patches --patches native/build/calma-patches.jar
```

## Procedencia de los cambios

| Código propio | Responsabilidad |
| --- | --- |
| `patches/CalmaBase.kt`, `CloneNames.kt` | Paquete separado, autoridades y permisos, componentes propios, recursos y extensión DEX. |
| `patches/FeedHooks.kt`, `src/.../NativeFeed.java` y políticas relacionadas | Adaptación de las reglas de feed, relaciones y descubrimiento de Calma a los modelos nativos identificados en el APK fijado. |
| `patches/ReelsPatch.kt`, `ReelsBlockedFragment.kt`, `src/.../CalmaReels.java` | Controles de Reels sobre los puntos nativos documentados en [`reels-map.json`](reels-map.json). |
| `patches/SettingsPatch.kt`, `src/.../CalmaSettings*.java` | Fila dentro de los ajustes de Instagram y pantalla propia; símbolos descritos en [`settings-hooks.md`](settings-hooks.md). |
| `src/.../NativeInitProvider.java`, `NativeLifecycle.java`, `NativeUpdates.java` | Ciclo de vida y alojamiento del actualizador existente. |
| `tools/MergeSplits.kt`, `DexSubset.java`, `DexInspect.java` | Utilidades genéricas propias para unir, extraer e inspeccionar el APK. |
| `tools/RestoreDexLayout.java`, `scripts/repair-dex-layout.py` | Conservan la distribución de clases de Instagram y sincronizan sus índices y hashes de arranque. |

Los símbolos de Instagram se identificaron sobre la entrada verificada. Los parches
comprueban la versión y las firmas de los métodos que modifican; no se debe ampliar
la compatibilidad cambiando únicamente un número de versión.

El actualizador reutiliza Inhouse Read y Photos según
[`updater.md`](updater.md) y [`vendor/UPSTREAM.md`](../vendor/UPSTREAM.md).
`compile-extension.py` compila el instalador Kotlin existente sin reescribir su
algoritmo. Adapta la versión del anfitrión en `UpdateActivity`, y el empaquetado
cambia la URL de consulta al canal nativo. Los originales de `vendor/` se conservan.

Las dependencias Kotlin, AndroidX y anotaciones de la extensión se trasladan,
después de compilar, a `es.calma.vendor.*` mediante ASM incluido en el compilador
Kotlin. El build verifica que no quedan referencias a sus namespaces originales ni
clases que sobrescriban las de Instagram. El FileProvider usa el nombre trasladado
y conserva la clave de metadatos `android.support.FILE_PROVIDER_PATHS`.

Los estilos propios se incorporan a `styles.xml` de día y noche sin reemplazar los
de Instagram. Los recursos nuevos reciben IDs explícitos. Esto evita que ARSCLib
interprete un nombre de archivo personalizado como un tipo de recurso distinto.

## Comprobaciones y límites

Desde la raíz:

```sh
python3 native/tests/package_clone.py
python3 native/tests/feed.py
python3 native/tests/discover.py
python3 native/tests/reels.py
python3 native/tests/settings.py
python3 native/tests/updater.py
python3 tests/updater.py
python3 tests/upstream_updater.py
```

Las pruebas del actualizador web necesitan Playwright para Python y Chromium en la
ruta utilizada por los tests. Las pruebas nativas ejecutan políticas y adaptadores
con fixtures JVM; no ejecutan la aplicación completa de Instagram.

Para verificar otra vez el APK firmado:

```sh
python3 native/scripts/verify-apk.py \
  --apk native/build/dist/IG-Calma-0.4.1.apk \
  --stock /ruta/al/base.apk \
  --build-tools "$CALMA_SDK/build-tools/36.0.0"
python3 native/tests/updater.py --apk native/build/dist/IG-Calma-0.4.1.apk
```

La verificación del artefacto comprueba firma, alineación de 16 KB, versiones,
autoridades, permisos, componentes, clases nativas conservadas, bibliotecas `.so`
sin cambios y recursos Inhouse empaquetados. También comprueba que no se ha incluido
el `MainActivity` del cliente web anterior.

Desde la 0.4.1, `tests/dex_layout.py` exige las mismas clases originales en cada
DEX y comprueba los hashes y canarios que consulta el cargador de Instagram. La
0.4.0 tenía 19 DEX reconstruidos, pero conservaba metadatos de 20 DEX con hashes
antiguos; el nuevo verificador rechaza ese APK. Se eliminan los perfiles de
optimización originales, ligados a checksums e índices que ya no corresponden.
`tests/hook_dex_analysis.py` analiza además los registros y enlaces de los métodos
modificados sobre todos los DEX del APK firmado. Ambas comprobaciones forman
parte del build y siguen siendo pruebas estáticas.

El actualizador omite `IgSplashScreenActivity`: esa pantalla provisional puede
reanudarse mientras Instagram sigue preparando su proveedor WebView. La primera
pantalla real inicia la comprobación de Inhouse sin añadir un temporizador.

El workflow `native-startup.yml` instala la APK publicada y actualiza a la candidata
con la misma firma en emuladores oficiales de Android 14, 15 y 16. Comprueba el
proceso durante 45 segundos, los errores y la pantalla alcanzada sin iniciar sesión.
Los informes en `validation/` vinculan cada resultado al SHA-256 del APK probado.
`record-runtime-check.py` rechaza resultados de otro APK antes de incorporarlos a
los archivos de verificación de una publicación.

La imagen disponible de Android 13 no traduce ARM64. En la de Android 11, también
el APK original firmado por Meta se cierra en el inicializador nativo Lacrima;
se conserva esa comparación en `validation/emulator-compatibility.json`. Esos dos
entornos no permiten afirmar que la aplicación funcione o falle en un teléfono
ARM64 de esas versiones.

**No se han probado teléfonos físicos ni una sesión real de Instagram.** Las
pruebas de arranque sin sesión no verifican la navegación, las notificaciones DM,
la reproducción, la fluidez ni la instalación desde el popup en un teléfono.
