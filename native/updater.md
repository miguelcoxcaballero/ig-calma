# Actualizaciones de la aplicación nativa

El actualizador conserva los componentes de Inhouse Read y Photos documentados en
[`vendor/UPSTREAM.md`](../vendor/UPSTREAM.md): Read comprueba GitHub, compara versiones
y presenta su popup; Photos descarga y verifica el APK y abre el instalador Android.
Los archivos originales permanecen en `vendor/`. Sus adaptadores existentes generan
los mismos JavaScript, CSS y Kotlin que utiliza el cliente anterior.

`NativeInitProvider` registra el ciclo de vida de la aplicación en el proceso
principal. Al reanudarse la primera Activity de Instagram, `NativeUpdates` carga
inmediatamente los recursos locales del comprobador en un WebView invisible de un
píxel. El feed, historias y DM siguen siendo pantallas nativas de Instagram.
Cambiar entre Activities conserva ese comprobador. Su contexto y listener de foco
se transfieren al nuevo anfitrión; al destruirlo o liberar memoria en segundo plano
se eliminan ambos puentes y se destruye el WebView.

Una oferta recibida sin foco queda pendiente hasta que la Activity esté reanudada
y tenga foco. La oferta ya validada se pasa a `UpdateActivity`, evitando otra consulta
para mostrar el popup. El puente de instalación solo existe en esa pantalla local.
El comprobador ofrece únicamente la versión y la entrega del manifiesto.

La versión de Calma procede de `CalmaBuild.VERSION`, actualmente `0.4.0`.
`compile-extension.py` adapta únicamente ese valor y el identificador de versión
del agente de usuario en `UpdateActivity`. El instalador Kotlin de Photos se compila
sin cambios adicionales. Sus dependencias se trasladan a `es.calma.vendor` para
coexistir con las bibliotecas de Instagram. La comprobación de paquete, versionCode,
firma y SHA-256 de Photos permanece intacta.

El canal nativo consulta
`https://raw.githubusercontent.com/miguelcoxcaballero/ig-calma/native-instagram/native/android-update.json`.
Durante el empaquetado solo cambia esa URL dentro del JavaScript generado de Read;
los recursos de `app/assets/updates` conservan el canal anterior sin modificaciones.
Ambas rutas deben publicar el mismo manifiesto del APK firmado, también para que
la versión web instalada pueda ofrecer el paso a la nativa. `required: true` es
necesario: el código original de Read interpreta `false` como desactivar el aviso,
incluso al comprobar manualmente. La versión nativa requiere Android 9 y ARM64.
Una publicación marcada como prerelease no aparece en el respaldo
`releases/latest`; los manifiestos directos permiten detectarla igualmente.

La integración requiere el provider de inicialización no exportado, la Activity
translúcida de actualización no exportada, el permiso `REQUEST_INSTALL_PACKAGES` y
el FileProvider con autoridad `${applicationId}.fileprovider`. Android exige que el
usuario autorice esa fuente y confirme la instalación.

## Verificación

Ejecutar desde la raíz:

```sh
python native/tests/updater.py
python tests/updater.py
python tests/upstream_updater.py
python native/tests/updater.py --apk /ruta/al/IG-Calma-0.4.0.apk
```

La prueba nativa compila y ejecuta las clases reales de alojamiento contra fixtures
de Android. Comprueba arranque, foco diferido, pausa, transferencia entre Activities,
callbacks de un renderizador obsoleto, liberación de recursos, recuperación y registro
único del provider. También fija los hashes de los originales y regenera sus
adaptaciones en un directorio temporal para compararlas byte a byte. Si existe una
compilación nativa, comprueba las fuentes del actualizador que recibió el compilador.
Las pruebas existentes ejercitan el JavaScript de Read en Chromium y verifican las
funciones copiadas de Photos.
La opción `--apk` compara los recursos empaquetados del APK con los existentes,
permitiendo únicamente el cambio de URL al canal nativo.

Estas pruebas no sustituyen una instalación Android: no miden el tiempo real de
WebView, la presentación del popup sobre Instagram ni el resultado de PackageInstaller
en un teléfono. Deben verificarse con el APK firmado en un dispositivo.
