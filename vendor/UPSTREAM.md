# Actualizador Inhouse reutilizado

El actualizador desplegado procede de **Inhouse Read**, commit `e575b76bbf19e379b639bbfa43b2d48bb9efac26`:

- [Interfaz y lógica de comprobación](https://github.com/miguelcoxcaballero/inhouse-read/blob/e575b76bbf19e379b639bbfa43b2d48bb9efac26/src/js/android-update.js).
- [CSS original](https://github.com/miguelcoxcaballero/inhouse-read/blob/e575b76bbf19e379b639bbfa43b2d48bb9efac26/src/css/android-update.css), copiado sin cambios.
- [Carga diferida](https://github.com/miguelcoxcaballero/inhouse-read/blob/e575b76bbf19e379b639bbfa43b2d48bb9efac26/src/js/idle-startup.js).
- [Instalador Java generado por el proyecto](https://github.com/miguelcoxcaballero/inhouse-read/blob/e575b76bbf19e379b639bbfa43b2d48bb9efac26/android/html_to_apk_builder.py), bloque `installAppUpdate`, `sha256Hex` y notificaciones de progreso. El fragmento original está en `updater-native.methods.txt`.

Se revisó también [UpdateInstaller de Inhouse Photos](https://github.com/miguelcoxcaballero/Inhouse-Photos/blob/dc97ccef18f2a327a5fcdae4c6727268039f2b3c/mobile/android/app/src/main/kotlin/app/alextran/immich/UpdateInstaller.kt). Su fuente queda como referencia, pero su adaptador Flutter no se compila en esta app Java/WebView. No se afirma que los dos adaptadores sean idénticos: esta integración usa el de Read.

Los componentes originales y el fragmento nativo están en `vendor/inhouse-read`; `scripts/vendor-updater.py` genera el bundle local mediante sustituciones mecánicas de importaciones/exportaciones, URLs, nombres de archivo y marca. La carga diferida apunta al primer fotograma de la pantalla de actualización en lugar del canvas de la biblioteca de Read.

En el bloque Java solo se sustituyen los nombres de la actividad, el acceso a WebView, el nombre del APK temporal y textos de marca; se desescapan las llaves de la plantilla Python. Se conservan la descarga, sus tiempos de espera, la comprobación SHA-256, el tamaño mínimo y el envío al instalador Android. No se ha reimplementado ese algoritmo.

El código nuevo es la conexión con los ajustes, el alojamiento aislado de los recursos locales y el manifiesto de esta app. El puente del instalador está únicamente en la pantalla local de actualizaciones; no se expone a páginas de Instagram. Se usa AndroidX FileProvider 1.13.1, el mismo tipo de proveedor que usan ambos originales.

El aviso de actualización conserva el diálogo bloqueante de Inhouse Read. Se muestra en una pantalla interna de actualización, sin añadir un botón flotante al feed. Android sigue exigiendo autorizar esta fuente y confirmar la instalación.
