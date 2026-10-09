# Instagram Calma 0.2.0 — experimental

Cliente Android independiente basado en **Instagram web**, con detección automática de seguidos, amigos por seguimiento mutuo, feed finito y bloqueo de Reels. No es una modificación del APK oficial ni está afiliado a Meta. Requiere Android 8 o posterior y Android System WebView actualizado.

**[Descargar APK 0.2.0](https://github.com/miguelcoxcaballero/ig-calma/raw/refs/heads/main/downloads/IG-Calma-0.2.0.apk)** · [Archivos y SHA-256](downloads/)

## Instalación y uso

1. Descarga y abre el APK. Si Android lo pide, permite instalar aplicaciones desde el navegador o gestor de archivos utilizado. La actualización conserva el mismo paquete y certificado de firma que la versión anterior.
2. Inicia sesión directamente en Instagram web. Ya no se introducen usuarios ni se importan listas. La app detecta tu propia sesión e intenta consultar tus relaciones.
3. Abre **⚙** para elegir «Solo cuentas que sigo», «Solo amigos (seguimiento mutuo)» o «Todas las cuentas», desactivar Reels y fijar entre 1 y 100 publicaciones por sesión.
4. Se conserva la interfaz de Instagram web, sin la barra verde ni el panel de estado anterior. El botón discreto ⚙ abre los ajustes adicionales. La apariencia y funciones siguen siendo las de la web, no las de la app nativa.
5. «Inicio / Nueva sesión» empieza otra tanda. Guardar ajustes, recargar o abrir un nuevo documento también reinician el límite; no es un límite diario ni un historial permanente de publicaciones vistas.

## Seguidos y amigos automáticos

Se detecta el identificador de tu cuenta mediante las cookies de sesión de WebView. No se pide la contraseña en formularios propios ni se suben cookies a un servidor de esta app.

Se consultan mediante GET, con tu sesión en instagram.com, `/api/v1/friendships/{tu_id}/following/` y `/followers/`. Son **interfaces internas sin garantía pública de compatibilidad**: pueden exigir comprobaciones, rechazar WebView o cambiar. La consulta pagina secuencialmente, con una pausa de 650 ms entre páginas, máximo 100 páginas por lista y 15 segundos por petición. No intenta saltarse comprobaciones ni bloqueos.

«Amigos» es la intersección de identificadores de cuenta: tú sigues a esa persona y ella te sigue. No equivale a Mejores amigos. La lista mutua solo se publica cuando terminan ambas consultas; no se usa una lista parcial como completa. Si solo termina la lista de seguidos, ese modo puede funcionar aunque falle el de seguidores.

Las relaciones completas se guardan localmente por cuenta. Se actualizan al usar la app si tienen más de 24 horas; «Sincronizar ahora» fuerza una consulta. Un cambio de cuenta limpia el estado en memoria y separa las cachés. Cerrar sesión y borrar datos web elimina las cachés.

Si una consulta se rechaza, se informa en los ajustes y no se reintenta automáticamente en ese documento; abrir una página nueva puede volver a intentarlo. Puede seguir disponible una caché anterior. Mientras no existe la lista necesaria, las publicaciones filtradas se ocultan. Los autores no identificados también se ocultan.

## Avisos de DM en segundo plano

**No hay un canal push propio de Instagram.** Se incluye una integración local opcional que copia avisos de DM publicados por la app oficial, mediante [NotificationListenerService de Android](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

1. Mantén **Instagram oficial instalado**, con la cuenta correcta y sus notificaciones de mensajes habilitadas. Esta app no verifica qué cuenta utiliza la app oficial: los avisos proceden de su sesión.
2. En ⚙, pulsa **Activar permisos de notificaciones**. En Android 13 o posterior, concede permiso para mostrar avisos; después, activa el acceso a notificaciones de Instagram Calma en los ajustes del sistema.
3. Se reconocen avisos marcados como mensajes, con estilo de conversación o canales que indican mensajes/direct. No se copian avisos de otras apps ni actividad general. Si Instagram etiqueta un DM de otra forma, podría no reconocerse.
4. Pulsar un aviso copiado abre la bandeja de DM aquí; no garantiza abrir el hilo exacto ni incluye respuestas rápidas. Puede haber tanto un aviso original como una copia. Desactivar los originales impide copiarlos.
5. Puedes desactivar la integración y revocar su acceso desde Android. Android concede un permiso amplio de acceso a notificaciones; el código descarta desde el inicio todos los paquetes salvo `com.instagram.android`. Los avisos no se envían a un servidor propio.

La integración depende de que Android permita el servicio y de que la app oficial reciba el aviso. Dispositivos con restricciones de notificaciones o perfiles de trabajo pueden impedirlo. Los filtros de feed y Reels afectan solo a Instagram Calma.

## Otros límites

- El filtro de cuentas se aplica al **inicio**, donde también se ocultan historias y paneles de descubrimiento. Perfiles, mensajes y búsqueda siguen accesibles; no se filtran todas las pantallas.
- Reels: se bloquean rutas `/reel/` y `/reels/` y publicaciones reconocidas por enlaces o etiquetas. Cambios del marcado pueden impedir la detección. No se eliminan todos los vídeos normales.
- Los filtros dependen de los elementos y enlaces de la web. Durante cargas o cambios de pantalla podría aparecer contenido brevemente. Instagram podría seguir solicitando contenido en segundo plano aunque el feed esté cortado.
- Instagram puede bloquear el acceso desde WebView. Se bloquean enlaces externos, incluido el acceso mediante Facebook. No se implementan carga de archivos, cámara ni micrófono.
- El nombre de instalación es Instagram Calma; el icono incorpora cámara y pausa. Los ajustes identifican el cliente como independiente. No se presenta como una aplicación oficial.
- Cookies en WebView y relaciones en su almacenamiento local, por cuenta. No hay analítica, servidor propio ni puente JavaScript con acceso nativo. No se imprimen credenciales ni notificaciones.

## Verificación realizada

APK para API 35, mínimo 26, versión de paquete 2. Firma v2/v3 verificada; mismo certificado que 0.1.0. Permisos: Internet y mostrar notificaciones. El servicio está protegido por `BIND_NOTIFICATION_LISTENER_SERVICE` y requiere activación del usuario.

Pruebas en Chromium con respuestas simuladas: paginación, intersección por identificadores, integración con filtros, separación por cuenta, reinicio de sesión al cambiar de cuenta, cierre de sesión, caché, detención ante limitación de consultas y rechazo de listas mutuas incompletas. Pruebas de filtros: autores desconocidos, Reels, límite, carga dinámica, navegación y acceso. Pruebas de clasificación de avisos: DM, exclusión de actividad general, otras apps y avisos de la propia app.

**No se ha instalado el APK en Android ni probado con una cuenta real. La sincronización y la entrega real de avisos necesitan esa validación; las pruebas simuladas no garantizan que los endpoints internos sigan disponibles.**

## Compilar y probar

En Linux x86_64 con Java 17 o posterior y Python 3:

```sh
python3 setup-tools.py
bash build.sh
```

Salida: `dist/IG-Calma-0.2.0.apk`. Conserva `build/local-signing.p12` para firmar actualizaciones con la misma clave. Su contraseña `localbuild` es solo para compilación local, no una credencial de Instagram. La clave no se incluye en el repositorio.

Con Playwright para Python y Chromium en `/usr/bin/chromium`:

```sh
python3 tests/filters.py
python3 tests/relations.py
mkdir -p build/rules-test
java -jar tools/ecj.jar -source 1.8 -target 1.8 -proc:none -d build/rules-test app/src/es/calma/instagram/DmRules.java tests/DmRulesTest.java
java -cp build/rules-test DmRulesTest
```
