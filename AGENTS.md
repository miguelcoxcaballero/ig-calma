# Publicar actualizaciones Android

- Reutiliza los componentes Inhouse guardados en `vendor/`; no reimplementes el actualizador. Consulta `vendor/UPSTREAM.md` antes de cambiarlo.
- Para cada nueva publicación, incrementa `versionName` y `versionCode` en el manifiesto. Mantén la versión de `getAppVersion` y el marcador de la pantalla local sincronizados.
- Compila con la clave existente en `build/local-signing.p12`. Nunca publiques esa clave. Una clave distinta no puede actualizar la instalación existente.
- `build.sh` genera `android-update.json` a partir del APK firmado. Publica APK y JSON en el mismo commit; no apuntes a una descarga con otra versión, tamaño o SHA-256.
- Ejecuta `tests/updater.py` y `tests/upstream_updater.py`, verifica la firma y comprueba la descarga pública tras publicar.
- El puente del instalador solo se admite en la pantalla local de actualizaciones. Nunca lo añadas al WebView de Instagram.
- Las APK instaladas consultan dos rutas: `main/android-update.json` y `native-instagram/native/android-update.json`. Publica el mismo manifiesto en ambas; usa `required: true`, porque Inhouse Read interpreta `false` como no mostrar el aviso. No retrocedas la versión ni el versionCode al ejecutar el build web antiguo.

# Inhouse services updater — mandatory shared updater policy

Canonical repository: https://github.com/miguelcoxcaballero/inhouse-services-updater

Every AI/developer working in this app must read this section even without opening the updater repository:

- **B is the default update channel.** A requires an English development warning, explicit acknowledgement and slide confirmation. Legacy implicit A preferences migrate to B; choosing B clears A consent. This does not authorize publishing or promoting a B release.
- All new changes and automatic releases go to **A** (rapid changes/testing).
- **B** (reviewed releases) requires the user's **explicit request**. Tests passing is not permission to promote.
- Every release needs a tiny changelog: preferably 2–10 words, maximum 120 characters.
- Never independently edit updater copies. Change the canonical repository first, test, commit, and sync the SAME revision into YouTube, Instagram, Notes, Photos and Read using its `tools/sync.py`.
- Commit `inhouse-updater.lock.json` and shared files together. Build checks must reject drift. App identity and host integration are the only permitted differences.
- Updates are optional. Later snoozes automatic prompts for 14 days; manual updates remain available in settings. The popup and settings both expose A/B selection.
- Keep APK package and signing key; increment Android versionCode globally across A/B. Switching channels never downgrades or deletes user data.
- Publish each exact signed APK before its matching channel catalog. Use `updates/A.json` and `updates/B.json`; never mix channels using generic GitHub latest. B initially has no releases until explicitly requested.
- See the central `AGENTS.md`, `docs/PROTOCOL.md` and `tools/catalog.py` before changing publication logic.

This section supersedes older instructions that mandate blocking update gates, independently copied updaters, or unconditional latest/stable publication. Existing signature and integrity requirements remain in force.
