# Instagram Calma 0.3.5 — experimental

Cliente Android independiente basado en **Instagram web**, con detección automática de seguidos, amigos por seguimiento mutuo, feed finito y bloqueo de Reels. No es una modificación del APK oficial ni está afiliado a Meta. Requiere Android 8 o posterior y Android System WebView actualizado.

**[Descargar APK 0.3.5](https://github.com/miguelcoxcaballero/ig-calma/raw/refs/heads/main/downloads/IG-Calma-0.3.5.apk)** · [Archivos y SHA-256](downloads/)

## Instalación y uso

1. Descarga y abre el APK. Si Android lo pide, permite instalar aplicaciones desde el navegador o gestor de archivos utilizado. La actualización conserva el mismo paquete y certificado de firma que la versión anterior.
2. Inicia sesión directamente en Instagram web. Ya no se introducen usuarios ni se importan listas. La app detecta tu propia sesión e intenta consultar tus relaciones.
3. En los ajustes de Instagram web, entra en **Editar perfil → Tu feed** para elegir «Solo cuentas que sigo», «Solo amigos (seguimiento mutuo)» o «Todas las cuentas», desactivar Reels y fijar entre 1 y 100 publicaciones por sesión.
4. Se conserva la interfaz de Instagram web, sin la barra verde ni el panel de estado anterior. Los controles adicionales se insertan en el contenido de la página de ajustes, sin botones flotantes, barras ni superposiciones. La apariencia y funciones siguen siendo las de la web, no las de la app nativa.
5. «Empezar otra sesión» inicia otra tanda. Cambiar entre Inicio y DM conserva la tanda actual. Guardar ajustes, recargar o abrir un nuevo documento también reinician el límite; no es un límite diario ni un historial permanente de publicaciones vistas.

## Buscar y Discover en 0.3.5

Las páginas `/explore/` y sus subrutas muestran únicamente publicaciones de cuentas seguidas, independientemente del filtro elegido para Inicio. Los resultados de perfiles también se limitan a cuentas seguidas. La búsqueda sigue disponible; los perfiles abiertos desde otras pantallas no se filtran. Los paneles de búsqueda identificados por sus campos Buscar/Search y roles dialog/search reciben el mismo filtro.

Las miniaturas sin autor visible se mantienen ocultas hasta comprobarlo desde la sesión mediante `/api/v1/media/{id}/info/`. Las comprobaciones se hacen solo cerca del área visible, de una en una, con una pausa de 450 ms, un máximo de 80 por documento y sin reintentos si Instagram rechaza la consulta. Se reutilizan los resultados verificados en memoria y se limpian al cambiar de cuenta. No se muestra contenido cuyo autor no pueda verificarse. Se conservan los elementos de paginación de Instagram y el bloqueo de Reels.

Este filtro limita el contenido que Instagram entrega a Discover; no crea un catálogo de todas las publicaciones de las cuentas seguidas. Puede haber pocos resultados o huecos mientras se verifican las miniaturas. Los cambios de estructura web y las restricciones de las interfaces internas necesitan validación con una cuenta real.

## Fluidez y ajustes en 0.3.4

Inicio y Mensajes mantienen dos WebViews en memoria. Cambiar de pestaña conserva la posición, la conversación abierta y los campos de texto; la notificación de un DM abre la bandeja. La bandeja se precarga después de preparar Inicio cuando hay una sesión iniciada y memoria disponible. No se precarga en dispositivos que Android identifica como de poca RAM. Al cambiar de cuenta se descarta la pantalla inactiva; Android también puede liberarla por presión de memoria. La primera carga, un proceso cerrado o una pantalla liberada siguen necesitando conexión a Instagram.

Los filtros, el bloqueo de Reels y las animaciones responden a cambios de página y contenido; ya no recorren periódicamente el documento. Su trabajo se suspende en la pestaña inactiva. El estado del feed no se reescribe si no cambia, y los mensajes entrantes no animan de nuevo toda la conversación. La precarga reutiliza la lista de amigos completa de Inicio, sin repetir sus consultas desde la pestaña oculta.

«Tu feed» usa opciones inline, switches y un contador con −/+, adaptados al tema del dispositivo. Se han reducido las explicaciones, eliminado el selector nativo y conservado los cambios pendientes si Instagram reemplaza el panel. Los permisos de notificaciones y la confirmación de instalación siguen siendo pantallas del sistema Android.

También se corrigen las respuestas tardías de una página anterior que podían revelar contenido durante otra carga, la pérdida de pestañas al pasar normalmente al segundo plano, la restauración de un Reel al pulsar Inicio y el cambio accidental al siguiente vídeo si desaparecía el clip compartido. Se maneja la pérdida del proceso de WebView para reconstruir la pantalla afectada. Estas rutas nativas se han compilado y revisado; aún requieren validación en un dispositivo Android real.

## Correcciones del feed en 0.3.3

El filtro conserva los elementos de paginación y los espaciadores que utiliza Instagram para cargar y reciclar publicaciones. El estado de la tanda se coloca fuera de su lista de posts; solo indica que la tanda ha terminado cuando se alcanza el límite configurado. Se reconocen autores en enlaces con nombre de usuario, publicaciones colaborativas y cambios de autor en nodos reutilizados. Los duplicados del mismo post, incluidos sus distintos formatos de enlace, aparecen una sola vez.

La sincronización conserva la lista mutua completa anterior mientras consulta una nueva y si la consulta falla. Su caché se revisa cada 15 minutos al usar la app. No se realizan peticiones en bucle si Instagram las rechaza. Los carruseles mixtos conservan las fotos aunque Reels esté desactivado; sus vídeos siguen ocultos.

Estas correcciones permiten continuar cargando los posts que Instagram entrega al inicio. El filtro no construye un archivo de todas las publicaciones de cada amigo ni puede garantizar que Instagram incluya todas ellas en el feed. Se mantiene el límite por sesión elegido en ajustes.

## Actualización dentro de la app

En **Ajustes → Editar perfil → Tu feed → Actualizar aplicación**, puedes buscar y descargar una versión nueva sin abrir el navegador. El actualizador consulta `android-update.json`, compara la versión instalada y muestra el aviso, botón Instalar y barra de progreso originales de **Inhouse Read**. La descarga y la verificación SHA-256 también son su código reutilizado, no una implementación nueva. [Procedencia y adaptaciones mínimas](vendor/UPSTREAM.md). Se revisó el adaptador de Inhouse Photos; esta app usa el de Read por ser Java/WebView.

La comprobación automática comienza 12 segundos después de preparar el documento principal, sin añadir una vista al feed; el código original vuelve a consultar cada 15 minutos mientras su WebView siga ejecutándose. Cuando detecta una versión nueva con `required: true`, abre la pantalla interna de actualización. No se ofrece como un servicio de actualización mientras Android haya cerrado la app.

Android exige habilitar «Permitir desde esta fuente» para Instagram Calma y confirmar la instalación. No instala paquetes silenciosamente. Usa un APK con el mismo paquete y certificado para conservar datos y sesión. La versión 0.3.1 no contenía el actualizador: instala 0.3.2 o una versión posterior manualmente una vez para disponer de esta función en futuras versiones.

El instalador tiene su puente nativo únicamente en una pantalla local aislada; no se añade a Instagram. AndroidX FileProvider comparte únicamente la carpeta privada de descargas de actualización. El aviso bloqueante de Inhouse Read aparece en esa pantalla interna, sin botón flotante sobre el feed.

Cada compilación genera el manifiesto con la versión, tamaño y SHA-256 del APK firmado y copia el archivo a `downloads/`. Al publicar una versión, se deben subir juntos APK y manifiesto, preservando la misma clave de firma.

## Tema, movimiento y carga

El modo claro/oscuro sigue la configuración del dispositivo. Cambia el fondo de la ventana, las barras del sistema y la paleta de Instagram web y de los controles adicionales. Se actualiza sin recargar la página al cambiar `uiMode`; volver a la app también actualiza el tema. No se invierten fotos ni vídeos.

Se añaden transiciones de opacidad de 110 ms y respuesta táctil a botones y enlaces, sin superposiciones. Se respetan las animaciones desactivadas en Android y `prefers-reduced-motion`. Se ocultan las barras de desplazamiento de WebView. La apariencia y el motor siguen siendo web; no se reproduce la app nativa.

Los filtros se instalan al estar disponible el primer documento visible, antes del evento de carga completa. WebView se mantiene con el fondo del tema hasta terminar esa instalación. Las cuentas seguidas verificadas pueden mostrarse desde la primera página de resultados; la sincronización completa continúa y solo se guardan cachés completas. Esto reduce la espera del filtrado, pero no acelera los servidores o la conexión de Instagram.

El inicio mantiene oculto el contenido no aprobado mediante CSS antes de clasificarlo. La protección se activa de forma síncrona al navegar a Inicio dentro de la web. Con Reels desactivados, los vídeos del feed se ocultan incluso si llegan después de una foto. Para evitar clips que la web no identifica como Reel, se ocultan las publicaciones del inicio compuestas solo por vídeo; los carruseles mixtos conservan sus fotos, con los vídeos ocultos y pausados.

## Ubicación de los controles

Abre los ajustes de Instagram desde su menú habitual y entra en **Editar perfil** (`/accounts/edit/`). Al final de esa página aparece **Tu feed**, con filtros, límite de publicaciones, sincronización y permisos de DM. Se mantienen los formularios y controles existentes de Instagram. Si cambia la estructura de la página, la integración puede necesitar ajustes. No se modifica el APK oficial ni se añaden controles al feed o a los mensajes.

## Seguidos y amigos automáticos

Se detecta el identificador de tu cuenta mediante las cookies de sesión de WebView. No se pide la contraseña en formularios propios ni se suben cookies a un servidor de esta app.

Se consultan mediante GET, con tu sesión en instagram.com, `/api/v1/friendships/{tu_id}/following/` y `/followers/`. Son **interfaces internas sin garantía pública de compatibilidad**: pueden exigir comprobaciones, rechazar WebView o cambiar. La consulta pagina secuencialmente, con una pausa de 650 ms entre páginas, máximo 100 páginas por lista y 15 segundos por petición. No intenta saltarse comprobaciones ni bloqueos.

«Amigos» es la intersección de identificadores de cuenta: tú sigues a esa persona y ella te sigue. No equivale a Mejores amigos. La lista mutua solo se sustituye cuando terminan ambas consultas. Durante una actualización o si esta falla, se conserva la última lista completa verificada para evitar que desaparezcan los posts. Si solo termina la lista de seguidos, ese modo puede funcionar aunque falle el de seguidores.

Las relaciones completas se guardan localmente por cuenta. Se actualizan al usar la app si tienen más de 15 minutos; «Sincronizar ahora» fuerza una consulta. Un cambio de cuenta limpia el estado en memoria y separa las cachés. Cerrar sesión y borrar datos web elimina las cachés.

Si una consulta se rechaza, se informa en los ajustes y no se reintenta automáticamente en ese documento; abrir una página nueva puede volver a intentarlo. Puede seguir disponible una caché anterior. Mientras no existe la lista necesaria, las publicaciones filtradas se ocultan. Los autores no identificados también se ocultan.

## Avisos de DM en segundo plano

**No hay un canal push propio de Instagram.** Se incluye una integración local opcional que copia avisos de DM publicados por la app oficial, mediante [NotificationListenerService de Android](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

1. Mantén **Instagram oficial instalado**, con la cuenta correcta y sus notificaciones de mensajes habilitadas. Esta app no verifica qué cuenta utiliza la app oficial: los avisos proceden de su sesión.
2. En **Editar perfil → Tu feed**, pulsa **Activar permisos de notificaciones**. En Android 13 o posterior, concede permiso para mostrar avisos; después, activa el acceso a notificaciones de Instagram Calma en los ajustes del sistema.
3. Se reconocen avisos marcados como mensajes, con estilo de conversación o canales que indican mensajes/direct. No se copian avisos de otras apps ni actividad general. Si Instagram etiqueta un DM de otra forma, podría no reconocerse.
4. Pulsar un aviso copiado abre la bandeja de DM aquí; no garantiza abrir el hilo exacto ni incluye respuestas rápidas. Puede haber tanto un aviso original como una copia. Desactivar los originales impide copiarlos.
5. Puedes desactivar la integración y revocar su acceso desde Android. Android concede un permiso amplio de acceso a notificaciones; el código descarta desde el inicio todos los paquetes salvo `com.instagram.android`. Los avisos no se envían a un servidor propio.

La integración depende de que Android permita el servicio y de que la app oficial reciba el aviso. Dispositivos con restricciones de notificaciones o perfiles de trabajo pueden impedirlo. Los filtros de feed y Reels afectan solo a Instagram Calma.

## Reels compartidos por amigos

Con «Desactivar Reels» activado, se pueden abrir enlaces a un Reel concreto desde un hilo de DM (`/direct/t/`) cuando la app puede verificar al remitente o identificar una conversación individual con una cuenta de seguimiento mutuo. La lista de amigos debe haberse sincronizado. Si el remitente o el contexto son ambiguos, se bloquea la apertura; no se presume que sea un amigo. Esto puede limitar los grupos o mensajes cuyo marcado no permite reconocer al autor.

La autorización es de un solo uso y caduca en 30 segundos. El visor queda limitado al identificador de ese Reel: se bloquean desplazamiento táctil, rueda y teclas de paginación, se pausan otros vídeos cargados y no se autoriza abrir el Reel siguiente. Los controles de reproducción permanecen disponibles. Volver a los DM permite abrir otro enlace compartido verificado. La identificación del vídeo inicial y los controles dependen de la estructura web de Instagram.

## Otros límites

- El filtro de cuentas se aplica al **inicio**, donde también se ocultan historias y paneles de descubrimiento. Perfiles, mensajes y búsqueda siguen accesibles; Buscar/Discover tiene además el filtro obligatorio de cuentas seguidas descrito arriba; no se filtran todas las pantallas.
- Reels: se bloquean rutas `/reel/` y `/reels/` y publicaciones reconocidas por enlaces o etiquetas. Cambios del marcado pueden impedir la detección. Para evitar fotogramas de clips no identificados, se ocultan las publicaciones compuestas solo por vídeo en el inicio cuando este bloqueo está activo. Los carruseles que contienen fotos conservan esas fotos, con los vídeos ocultos y pausados; los DM verificados tienen la excepción descrita arriba.
- Los filtros dependen de los elementos y enlaces de la web. Durante cargas o cambios de pantalla podría aparecer contenido brevemente. Instagram podría seguir solicitando contenido en segundo plano aunque el feed esté cortado.
- Instagram puede bloquear el acceso desde WebView. Se bloquean enlaces externos, incluido el acceso mediante Facebook. No se implementan carga de archivos, cámara ni micrófono.
- El nombre de instalación es Instagram Calma; el icono incorpora cámara y pausa. Los ajustes identifican el cliente como independiente. No se presenta como una aplicación oficial.
- Cookies en WebView y relaciones en su almacenamiento local, por cuenta. No hay analítica ni servidor propio. Instagram no tiene un puente JavaScript nativo; el instalador tiene uno aislado en su página local. No se imprimen credenciales ni notificaciones.

## Verificación realizada

APK para API 35, mínimo 26, versión de paquete 9. Firma v2/v3 verificada; mismo certificado que 0.1.0. Permisos: Internet, mostrar notificaciones e instalar paquetes con autorización del usuario. El servicio está protegido por `BIND_NOTIFICATION_LISTENER_SERVICE` y requiere activación del usuario.

Pruebas del actualizador original: detección de versión, aviso, URL y SHA-256 enviados al instalador, permiso de instalación, progreso, estados de listo/error, validación de origen y detección automática; CSS idéntico al original y bloque nativo idéntico salvo las sustituciones documentadas. Manifiesto comprobado frente al APK firmado. Pruebas de tema y movimiento: cambio claro/oscuro en el mismo documento, colores heredados por los controles, conservación de campos y fotos, respuesta táctil, transiciones y movimiento reducido, ausencia de superposiciones y de bucles de cambios del tema. Pruebas de Reels: enlaces de DM accesibles, amigos mutuos verificados, rechazo de cuentas no mutuas, un único vídeo, bloqueo de desplazamiento, ocultación inmediata de fotogramas al volver a Inicio y de vídeos añadidos tarde a una publicación ya aprobada. Prueba de carga: las primeras cuentas seguidas están disponibles antes de terminar la paginación. Pruebas de integración en la página de ajustes: se conserva el formulario original, controles en el flujo del documento sin superposición, guardado mediante navegación validada, sustitución dinámica del panel y ausencia de controles sobre inicio, DM y acceso. Pruebas en Chromium con respuestas simuladas: paginación, intersección por identificadores, integración con filtros, separación por cuenta, reinicio de sesión al cambiar de cuenta, cierre de sesión, caché, detención ante limitación de consultas y rechazo de listas mutuas incompletas. Pruebas de filtros: autores desconocidos, Reels, límite, carga dinámica, navegación y acceso. Pruebas de clasificación de avisos: DM, exclusión de actividad general, otras apps y avisos de la propia app.

**No se ha instalado el APK en Android ni probado con una cuenta real. La sincronización y la entrega real de avisos necesitan esa validación; las pruebas simuladas no garantizan que los endpoints internos sigan disponibles.**

## Compilar y probar

En Linux x86_64 con Java 17 o posterior y Python 3:

```sh
python3 setup-tools.py
bash build.sh
```

Salida: `dist/IG-Calma-0.3.5.apk`. Conserva `build/local-signing.p12` para firmar actualizaciones con la misma clave. Su contraseña `localbuild` es solo para compilación local, no una credencial de Instagram. La clave no se incluye en el repositorio.

Con Playwright para Python y Chromium en `/usr/bin/chromium`:

```sh
python3 tests/discover.py
python3 tests/filters.py
python3 tests/feed_regressions.py
python3 tests/relations.py
python3 tests/retained_relations.py
python3 tests/navigation.py
python3 tests/settings.py
python3 tests/appearance.py
python3 tests/reels.py
python3 tests/updater.py
python3 tests/upstream_updater.py
mkdir -p build/rules-test
java -jar tools/ecj.jar -source 1.8 -target 1.8 -proc:none -d build/rules-test app/src/es/calma/instagram/DmRules.java tests/DmRulesTest.java
java -cp build/rules-test DmRulesTest
```
