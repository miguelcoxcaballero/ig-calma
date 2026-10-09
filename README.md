# Instagram Calma 0.3.1 — experimental

Cliente Android independiente basado en **Instagram web**, con detección automática de seguidos, amigos por seguimiento mutuo, feed finito y bloqueo de Reels. No es una modificación del APK oficial ni está afiliado a Meta. Requiere Android 8 o posterior y Android System WebView actualizado.

**[Descargar APK 0.3.1](https://github.com/miguelcoxcaballero/ig-calma/raw/refs/heads/main/downloads/IG-Calma-0.3.1.apk)** · [Archivos y SHA-256](downloads/)

## Instalación y uso

1. Descarga y abre el APK. Si Android lo pide, permite instalar aplicaciones desde el navegador o gestor de archivos utilizado. La actualización conserva el mismo paquete y certificado de firma que la versión anterior.
2. Inicia sesión directamente en Instagram web. Ya no se introducen usuarios ni se importan listas. La app detecta tu propia sesión e intenta consultar tus relaciones.
3. En los ajustes de Instagram web, entra en **Editar perfil → Ajustes adicionales** para elegir «Solo cuentas que sigo», «Solo amigos (seguimiento mutuo)» o «Todas las cuentas», desactivar Reels y fijar entre 1 y 100 publicaciones por sesión.
4. Se conserva la interfaz de Instagram web, sin la barra verde ni el panel de estado anterior. Los controles adicionales se insertan en el contenido de la página de ajustes, sin botones flotantes, barras ni superposiciones. La apariencia y funciones siguen siendo las de la web, no las de la app nativa.
5. «Inicio / Nueva sesión» empieza otra tanda. Guardar ajustes, recargar o abrir un nuevo documento también reinician el límite; no es un límite diario ni un historial permanente de publicaciones vistas.

## Tema, movimiento y carga

El modo claro/oscuro sigue la configuración del dispositivo. Cambia el fondo de la ventana, las barras del sistema y la paleta de Instagram web y de los controles adicionales. Se actualiza sin recargar la página al cambiar `uiMode`; volver a la app también actualiza el tema. No se invierten fotos ni vídeos.

Se añaden transiciones de navegación de 170 ms y respuesta táctil a botones y enlaces, sin superposiciones. Se respetan las animaciones desactivadas en Android y `prefers-reduced-motion`. Se ocultan las barras de desplazamiento de WebView. La apariencia y el motor siguen siendo web; no se reproduce la app nativa.

Los filtros se instalan al estar disponible el primer documento visible, antes del evento de carga completa. WebView se mantiene con el fondo del tema hasta terminar esa instalación. Las cuentas seguidas verificadas pueden mostrarse desde la primera página de resultados; la sincronización completa continúa y solo se guardan cachés completas. Esto reduce la espera del filtrado, pero no acelera los servidores o la conexión de Instagram.

El inicio mantiene oculto el contenido no aprobado mediante CSS antes de clasificarlo. La protección se activa de forma síncrona al navegar a Inicio dentro de la web. Con Reels desactivados, los vídeos del feed se ocultan incluso si llegan después de una foto. Para evitar clips que la web no identifica como Reel, se ocultan también publicaciones del inicio que contengan vídeos, no solo las etiquetadas como Reels.

## Ubicación de los controles

Abre los ajustes de Instagram desde su menú habitual y entra en **Editar perfil** (`/accounts/edit/`). Al final de esa página aparece **Ajustes adicionales**, con filtros, límite de publicaciones, sincronización y permisos de DM. Se mantienen los formularios y controles existentes de Instagram. Si cambia la estructura de la página, la integración puede necesitar ajustes. No se modifica el APK oficial ni se añaden controles al feed o a los mensajes.

## Seguidos y amigos automáticos

Se detecta el identificador de tu cuenta mediante las cookies de sesión de WebView. No se pide la contraseña en formularios propios ni se suben cookies a un servidor de esta app.

Se consultan mediante GET, con tu sesión en instagram.com, `/api/v1/friendships/{tu_id}/following/` y `/followers/`. Son **interfaces internas sin garantía pública de compatibilidad**: pueden exigir comprobaciones, rechazar WebView o cambiar. La consulta pagina secuencialmente, con una pausa de 650 ms entre páginas, máximo 100 páginas por lista y 15 segundos por petición. No intenta saltarse comprobaciones ni bloqueos.

«Amigos» es la intersección de identificadores de cuenta: tú sigues a esa persona y ella te sigue. No equivale a Mejores amigos. La lista mutua solo se publica cuando terminan ambas consultas; no se usa una lista parcial como completa. Si solo termina la lista de seguidos, ese modo puede funcionar aunque falle el de seguidores.

Las relaciones completas se guardan localmente por cuenta. Se actualizan al usar la app si tienen más de 24 horas; «Sincronizar ahora» fuerza una consulta. Un cambio de cuenta limpia el estado en memoria y separa las cachés. Cerrar sesión y borrar datos web elimina las cachés.

Si una consulta se rechaza, se informa en los ajustes y no se reintenta automáticamente en ese documento; abrir una página nueva puede volver a intentarlo. Puede seguir disponible una caché anterior. Mientras no existe la lista necesaria, las publicaciones filtradas se ocultan. Los autores no identificados también se ocultan.

## Avisos de DM en segundo plano

**No hay un canal push propio de Instagram.** Se incluye una integración local opcional que copia avisos de DM publicados por la app oficial, mediante [NotificationListenerService de Android](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

1. Mantén **Instagram oficial instalado**, con la cuenta correcta y sus notificaciones de mensajes habilitadas. Esta app no verifica qué cuenta utiliza la app oficial: los avisos proceden de su sesión.
2. En **Editar perfil → Ajustes adicionales**, pulsa **Activar permisos de notificaciones**. En Android 13 o posterior, concede permiso para mostrar avisos; después, activa el acceso a notificaciones de Instagram Calma en los ajustes del sistema.
3. Se reconocen avisos marcados como mensajes, con estilo de conversación o canales que indican mensajes/direct. No se copian avisos de otras apps ni actividad general. Si Instagram etiqueta un DM de otra forma, podría no reconocerse.
4. Pulsar un aviso copiado abre la bandeja de DM aquí; no garantiza abrir el hilo exacto ni incluye respuestas rápidas. Puede haber tanto un aviso original como una copia. Desactivar los originales impide copiarlos.
5. Puedes desactivar la integración y revocar su acceso desde Android. Android concede un permiso amplio de acceso a notificaciones; el código descarta desde el inicio todos los paquetes salvo `com.instagram.android`. Los avisos no se envían a un servidor propio.

La integración depende de que Android permita el servicio y de que la app oficial reciba el aviso. Dispositivos con restricciones de notificaciones o perfiles de trabajo pueden impedirlo. Los filtros de feed y Reels afectan solo a Instagram Calma.

## Reels compartidos por amigos

Con «Desactivar Reels» activado, se pueden abrir enlaces a un Reel concreto desde un hilo de DM (`/direct/t/`) cuando la app puede verificar al remitente o identificar una conversación individual con una cuenta de seguimiento mutuo. La lista de amigos debe haberse sincronizado. Si el remitente o el contexto son ambiguos, se bloquea la apertura; no se presume que sea un amigo. Esto puede limitar los grupos o mensajes cuyo marcado no permite reconocer al autor.

La autorización es de un solo uso y caduca en 30 segundos. El visor queda limitado al identificador de ese Reel: se bloquean desplazamiento táctil, rueda y teclas de paginación, se pausan otros vídeos cargados y no se autoriza abrir el Reel siguiente. Los controles de reproducción permanecen disponibles. Volver a los DM permite abrir otro enlace compartido verificado. La identificación del vídeo inicial y los controles dependen de la estructura web de Instagram.

## Otros límites

- El filtro de cuentas se aplica al **inicio**, donde también se ocultan historias y paneles de descubrimiento. Perfiles, mensajes y búsqueda siguen accesibles; no se filtran todas las pantallas.
- Reels: se bloquean rutas `/reel/` y `/reels/` y publicaciones reconocidas por enlaces o etiquetas. Cambios del marcado pueden impedir la detección. Para evitar fotogramas de clips no identificados, también se ocultan las publicaciones con vídeo en el feed de inicio cuando este bloqueo está activo; los DM verificados tienen la excepción descrita arriba.
- Los filtros dependen de los elementos y enlaces de la web. Durante cargas o cambios de pantalla podría aparecer contenido brevemente. Instagram podría seguir solicitando contenido en segundo plano aunque el feed esté cortado.
- Instagram puede bloquear el acceso desde WebView. Se bloquean enlaces externos, incluido el acceso mediante Facebook. No se implementan carga de archivos, cámara ni micrófono.
- El nombre de instalación es Instagram Calma; el icono incorpora cámara y pausa. Los ajustes identifican el cliente como independiente. No se presenta como una aplicación oficial.
- Cookies en WebView y relaciones en su almacenamiento local, por cuenta. No hay analítica, servidor propio ni puente JavaScript con acceso nativo. No se imprimen credenciales ni notificaciones.

## Verificación realizada

APK para API 35, mínimo 26, versión de paquete 5. Firma v2/v3 verificada; mismo certificado que 0.1.0. Permisos: Internet y mostrar notificaciones. El servicio está protegido por `BIND_NOTIFICATION_LISTENER_SERVICE` y requiere activación del usuario.

Pruebas de tema y movimiento: cambio claro/oscuro en el mismo documento, colores heredados por los controles, conservación de campos y fotos, respuesta táctil, transiciones y movimiento reducido, ausencia de superposiciones y de bucles de cambios del tema. Pruebas de Reels: enlaces de DM accesibles, amigos mutuos verificados, rechazo de cuentas no mutuas, un único vídeo, bloqueo de desplazamiento, ocultación inmediata de fotogramas al volver a Inicio y de vídeos añadidos tarde a una publicación ya aprobada. Prueba de carga: las primeras cuentas seguidas están disponibles antes de terminar la paginación. Pruebas de integración en la página de ajustes: se conserva el formulario original, controles en el flujo del documento sin superposición, guardado mediante navegación validada, sustitución dinámica del panel y ausencia de controles sobre inicio, DM y acceso. Pruebas en Chromium con respuestas simuladas: paginación, intersección por identificadores, integración con filtros, separación por cuenta, reinicio de sesión al cambiar de cuenta, cierre de sesión, caché, detención ante limitación de consultas y rechazo de listas mutuas incompletas. Pruebas de filtros: autores desconocidos, Reels, límite, carga dinámica, navegación y acceso. Pruebas de clasificación de avisos: DM, exclusión de actividad general, otras apps y avisos de la propia app.

**No se ha instalado el APK en Android ni probado con una cuenta real. La sincronización y la entrega real de avisos necesitan esa validación; las pruebas simuladas no garantizan que los endpoints internos sigan disponibles.**

## Compilar y probar

En Linux x86_64 con Java 17 o posterior y Python 3:

```sh
python3 setup-tools.py
bash build.sh
```

Salida: `dist/IG-Calma-0.3.1.apk`. Conserva `build/local-signing.p12` para firmar actualizaciones con la misma clave. Su contraseña `localbuild` es solo para compilación local, no una credencial de Instagram. La clave no se incluye en el repositorio.

Con Playwright para Python y Chromium en `/usr/bin/chromium`:

```sh
python3 tests/filters.py
python3 tests/relations.py
python3 tests/settings.py
python3 tests/appearance.py
python3 tests/reels.py
mkdir -p build/rules-test
java -jar tools/ecj.jar -source 1.8 -target 1.8 -proc:none -d build/rules-test app/src/es/calma/instagram/DmRules.java tests/DmRulesTest.java
java -cp build/rules-test DmRulesTest
```
