# IG Calma 0.1.0 — APK experimental

Cliente Android independiente que abre **Instagram web** y aplica filtros locales. No es el APK oficial modificado ni una aplicación afiliada a Meta. Requiere Android 8 o posterior, conexión a Internet y Android System WebView actualizado.

## Instalación y uso

1. Descarga `IG-Calma-0.1.0.apk` y ábrelo en tu Android. Si Android lo pide, permite instalar aplicaciones desde el navegador o gestor de archivos que utilizaste.
2. Abre IG Calma y configura sus **Ajustes**. Por defecto usa «Solo cuentas que sigo», desactiva Reels y limita cada sesión a 20 publicaciones.
3. Introduce tus cuentas seguidas o importa `following.json` desde una exportación de Instagram. Para obtenerlo: Centro de cuentas → Tu información y permisos → Descargar o exportar tu información → seguidores y seguidos → formato JSON. Descomprime la descarga antes de importar. La ubicación y los nombres del menú pueden cambiar.
4. Para «Solo mis amigos», introduce una lista independiente de usuarios. Los nombres admiten `@` y se separan por espacios, comas o saltos de línea. Guarda los ajustes e inicia sesión en Instagram desde la app.
5. Elige un límite entre 1 y 100 publicaciones. Al alcanzarlo, no se muestran nuevas publicaciones del inicio. En Ajustes, **Nueva sesión** reinicia la tanda; **Inicio**, guardar ajustes, recargar o volver a abrir el proceso también reinician la sesión.

## Alcance y limitaciones

- **Listas locales:** la app no consulta automáticamente a quién sigues ni quién te sigue de vuelta. Vuelve a importar o editar la lista cuando cambie. «Amigos» no sincroniza Mejores amigos de Instagram.
- **Inicio:** el filtro de cuentas se aplica al feed de inicio. También oculta historias y paneles de descubrimiento en ese feed, para evitar contenido ajeno a las listas. Perfiles, mensajes y búsqueda siguen accesibles; el filtro no se aplica a todas las pantallas de Instagram.
- **Reels:** oculta enlaces a `/reel/` y `/reels/`, bloquea su navegación y oculta publicaciones reconocidas como Reels. No garantiza reconocer un Reel cuyo marcado web no lo identifique, ni elimina todos los vídeos normales. Instagram puede cambiar sus rutas y etiquetas.
- **Feed finito:** limita publicaciones distintas reconocidas por su enlace. No lleva un historial permanente de lo ya visto ni establece un límite diario. Instagram podría seguir solicitando contenido en segundo plano aunque no se muestre.
- **Estructura web:** los filtros dependen de `main`, `article`, enlaces de autores y enlaces de publicaciones. Autores desconocidos se ocultan en los modos de lista. Si Instagram cambia estos elementos, los filtros pueden dejar de funcionar; durante cambios o cargas de página también podría aparecer contenido brevemente.
- **Acceso:** Instagram podría bloquear WebView o exigir comprobaciones adicionales. No se ha probado el inicio de sesión con una cuenta real. Los enlaces fuera de instagram.com, incluido el inicio de sesión mediante Facebook, se bloquean. No se implementan carga de archivos, cámara ni micrófono.
- **Datos:** ajustes y listas se guardan en el almacenamiento privado de la app. Las cookies de sesión permanecen en WebView; el botón para cerrar sesión las borra junto con los datos web. No hay servidor propio, analítica ni puente JavaScript con acceso nativo. Las conexiones de Instagram y sus recursos web funcionan según su propia política.

## Verificación realizada

APK compilado para API 35, mínimo API 26. Firma v2/v3 verificada con `apksigner`; manifiesto inspeccionado con `aapt`. Único permiso declarado: Internet.

Pruebas en Chromium con páginas controladas: listas de seguidos/amigos, autores desconocidos, bloqueo de enlaces/publicaciones de Reels, límite de publicaciones, carga dinámica, cambio de pantalla, lista vacía, preservación del formulario de acceso y desactivación de filtros. **Estas pruebas no equivalen a una prueba con una cuenta de Instagram ni a instalar el APK en un dispositivo Android.**

## Compilar desde el código

En Linux x86_64 con Java 17 o posterior y Python 3:

```sh
python3 setup-tools.py
bash build.sh
```

El script descarga herramientas oficiales de Google y el compilador Eclipse ECJ. La salida está en `dist/IG-Calma-0.1.0.apk`. La primera compilación genera una clave local en `build/local-signing.p12`; consérvala para que futuras versiones puedan actualizar tu instalación. Una clave diferente exige desinstalar primero y perder los datos locales. La contraseña `localbuild` es únicamente para esa clave de compilación local.

Para las pruebas, instala Playwright para Python y Chromium; ajusta la ruta de Chromium en `tests/filters.py` si tu distribución usa otra:

```sh
python3 tests/filters.py
```
