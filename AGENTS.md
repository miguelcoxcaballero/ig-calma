# Publicar actualizaciones Android

- Reutiliza los componentes Inhouse guardados en `vendor/`; no reimplementes el actualizador. Consulta `vendor/UPSTREAM.md` antes de cambiarlo.
- Para cada nueva publicación, incrementa `versionName` y `versionCode` en el manifiesto. Mantén la versión de `getAppVersion` y el marcador de la pantalla local sincronizados.
- Compila con la clave existente en `build/local-signing.p12`. Nunca publiques esa clave. Una clave distinta no puede actualizar la instalación existente.
- `build.sh` genera `android-update.json` a partir del APK firmado. Publica APK y JSON en el mismo commit; no apuntes a una descarga con otra versión, tamaño o SHA-256.
- Ejecuta `tests/updater.py` y `tests/upstream_updater.py`, verifica la firma y comprueba la descarga pública tras publicar.
- El puente del instalador solo se admite en la pantalla local de actualizaciones. Nunca lo añadas al WebView de Instagram.
- Las APK instaladas consultan dos rutas: `main/android-update.json` y `native-instagram/native/android-update.json`. Publica el mismo manifiesto en ambas; usa `required: true`, porque Inhouse Read interpreta `false` como no mostrar el aviso. No retrocedas la versión ni el versionCode al ejecutar el build web antiguo.
