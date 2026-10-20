# Instagram Calma 0.4.21 — A

Esta versión parte del APK original de Instagram **439.0.0.37.89** y aplica parches propios de Calma. Inicio, historias, mensajes y reproducción usan la aplicación nativa. No incluye paquetes de parches de terceros.

**[Descargar APK nativo](https://github.com/miguelcoxcaballero/ig-calma/releases/download/v0.4.21/IG-Calma-0.4.21.apk)** · [Publicación y comprobaciones](https://github.com/miguelcoxcaballero/ig-calma/releases/tag/v0.4.21) · [Código de los parches](native/patches/)

Requiere **Android 9 o posterior y ARM64**. No necesita root. Usa el paquete y la firma de Calma; puede coexistir con Instagram oficial. La sesión nativa requiere iniciar sesión de nuevo al venir del cliente web.

## Iconos Inhouse (0.4.14)

El tejado blanco de Inhouse sustituye al objetivo del icono de Instagram. Se conservan el degradado y el marco de la cámara. El pie de ajustes usa el mismo icono. [Recursos y adaptación al launcher](native/branding.md).

En la 0.4.15, pulsar el logo de los ajustes abre [Inhouse Software](https://inhouse.miguelcoxcaballero.com/), donde están los dos proyectos.

## Ajustes Inhouse

**Inhouse Instagram settings** aparece justo debajo del Centro de cuentas de Meta. **Activar cambios** enciende o apaga nuestras modificaciones de feed, anuncios, Reels, Explorar y saldo. Al cambiarlo se guardan las preferencias y se reinicia Instagram; el actualizador sigue disponible. Al pie aparecen el logo original de Inhouse, la versión, la fecha de publicación y el enlace a este repositorio.

La 0.4.15 está publicada y supera las pruebas de actualización, arranque y ajustes en emuladores Android 14, 15 y 16.

## Actividad de la cuenta (0.4.19)

Se han retirado la precarga automática continua del feed y las consultas adicionales de relaciones de Explorar. Las páginas nuevas se cargan mediante el desplazamiento nativo; se conserva lo ya recibido. Explorar usa las relaciones conocidas localmente y puede mostrar menos contenido o quedar vacío. Una página filtrada vacía detiene su continuación automática. La búsqueda escrita sigue admitiendo cualquier cuenta.

Estos cambios reducen peticiones innecesarias, pero no demuestran qué causó el bloqueo comunicado ni garantizan que Meta acepte una APK modificada. [Revisión, fuentes oficiales y pruebas](native/account-security.md).

## Feed

El selector superior ofrece **For you, Favourites y Following** usando el menú nativo de Instagram. **Following es la opción inicial** y muestra las cuentas que sigues, sin exigir seguimiento mutuo. Guarda las publicaciones de **48 horas**, ordenadas de más nuevo a más antiguo, sin duplicados y con las historias del encabezado. Al final aparece **That's it** con una cara sonriente. Desliza hacia abajo para actualizarlo.

For you conserva el orden del algoritmo, sus publicaciones y Reels, sin anuncios; Favourites conserva el feed de favoritos original, sin aplicar la ventana o el filtro de amistades. **For you comienza bloqueado**: gana 1 minuto al comenzar cada nueva hora, incluso con la app cerrada. Cada crédito caduca 24 horas después de ganarlo. El selector muestra minutos y segundos disponibles; en **Inhouse Instagram settings** se puede configurar de 0 a 60 minutos por hora. Sólo se cobra el uso en primer plano de For you y de sus Reels; los DMs, Explorar y el tiempo fuera de la app no gastan saldo. Al agotarse, vuelve a Following. No abre For you automáticamente ni entrega saldo retroactivo al instalar.

La 0.4.12 elimina Friends del selector y de los ajustes. Las selecciones guardadas de Friends pasan automáticamente a Following. [Funcionamiento y límites](native/feed.md).

En los ajustes habituales de Instagram aparece **Inhouse Instagram settings**. Permite ajustar los minutos por hora de For you y activar **Bloquear scroll de Reels**. Los Reels siguen apareciendo en el feed y se pueden abrir; el bloqueo impide pasar al siguiente o al anterior. Durante el uso de For you con minutos disponibles, el visor mantiene su navegación normal. Los ajustes adicionales siguen el modo claro u oscuro del dispositivo.

Explorar muestra contenido de cuentas que sigues, aunque no te sigan de vuelta. Al escribir en Buscar puedes encontrar cualquier cuenta; sin texto no aparecen sugerencias ni historial. Los Reels enviados por mensaje se abren directamente y, con el bloqueo activo, solo permiten ver ese clip. No esperan a comprobar la relación con el remitente. Los modelos de relaciones proceden de la sesión nativa; no se introducen listas manualmente.

La publicidad se bloquea incluso cuando sigues al anunciante. Se filtran los anuncios del feed y de las cuadrículas de Explorar/búsqueda y se rechazan las inserciones de anuncios entre historias y la fuente específica de anuncios de Reels. En el feed y las búsquedas escritas, una página compuesta solo por anuncios conserva su cursor. En Explorar se detiene una página filtrada vacía para evitar descargas automáticas sucesivas. [Implementación y límites](native/ads.md).

## Actualizaciones

**Inhouse Instagram settings → Actualizaciones · A/B** abre [Inhouse services updater](https://github.com/miguelcoxcaballero/inhouse-services-updater), el mismo código de YouTube, Notes, Photos y Read. **A: cambios recientes. B: versiones revisadas.** B solo se publica por petición expresa. A incluye todas las versiones A y B y ofrece la de mayor `versionCode`; B recibe solo versiones B. El canal A ofrece la 0.4.21, con colores neutros en el actualizador compartido. El canal B conserva la [0.4.20](https://github.com/miguelcoxcaballero/ig-calma/releases/tag/v0.4.20), también incluida en A. Ambas mantienen el gestor de descargas integrado, los ajustes A/B y el comportamiento pasivo de peticiones de la 0.4.19.

El popup es opcional, muestra versión instalada/nueva, versiones saltadas y hasta tres cambios breves. «Recordármelo más tarde» aplaza 14 días; puedes comprobar manualmente desde ajustes. El comprobador se inicia tras la pantalla provisional nativa. El instalador conserva la descarga y verificaciones de Inhouse Photos y usa un proveedor privado independiente.

Los manifiestos anteriores se conservan para migrar instalaciones antiguas. Desde esta versión se consultan exclusivamente los catálogos de canal `updates/A.json` y `updates/B.json`. [Reglas de sincronización y publicación](UPDATER.md).

## Estado de la prueba

La 0.4.21 verifica la APK firmada, los recursos compartidos y el popup real en Chromium en modo claro y oscuro, con colores neutros. El cambio se limita al CSS y la identidad de la versión; no se repite la matriz de emuladores anterior. [Comprobaciones de la 0.4.21](native/validation/0.4.21.json).

La 0.4.20 supera la actualización desde la 0.4.19, el arranque y las pruebas de modelos y ajustes en Android 14, 15 y 16. Se comprueban el WebView real del actualizador, la versión instalada, la confirmación de A, el popup opcional y el aplazamiento de 14 días. Las pruebas de peticiones siguen verificando que Calma no inicia paginación automática ni consultas de relaciones; no se ha probado con una cuenta real ni se puede garantizar ausencia de alertas de Meta. [Resultados vinculados a esta APK](native/validation/0.4.20.json).

La 0.4.16 supera las comprobaciones del actualizador compartido y las pruebas nativas en Android 14, 15 y 16: apertura real de ajustes, versión instalada, persistencia de A/B y aplazamiento de 14 días. [Resultados de esta versión](native/validation/0.4.16.json) · [Ejecución](https://github.com/miguelcoxcaballero/ig-calma/actions/runs/38152220446).


La APK 0.4.15 se valida como actualización sobre la 0.4.14 y en el arranque sin sesión en emuladores oficiales de **Android 14, 15 y 16**. La instrumentación resuelve y dibuja el icono adaptativo de Inhouse, verifica el logo interior negro y abre la actividad real de ajustes, comprueba el pie de Inhouse y el interruptor, la conservación de preferencias individuales, las tres opciones nativas, la migración a Following y el retorno a Following al agotarse el saldo. Comprueba también que pulsar el logo de los ajustes abre la web de Inhouse Software. Incluye pruebas del feed y Reels. No reproduce respuestas de una cuenta autenticada. [Resultados vinculados al SHA-256](native/validation/0.4.15.json) · [Ejecución de las pruebas](https://github.com/miguelcoxcaballero/ig-calma/actions/runs/38141574853).

La instrumentación crea los tres modelos del selector y las filas IGDS originales, verifica el saldo y el estado bloqueado, y comprueba el final del timeline en ambos temas. Las pruebas locales cubren caducidad, persistencia, cambios de hora y migración de las preferencias antiguas a Following.

La compilación y las comprobaciones de firma, recursos, clases nativas, destinos de los parches y pruebas automatizadas son reproducibles. **No se ha probado el inicio de sesión, la fluidez, la reproducción ni las notificaciones push con una cuenta real en un teléfono.** Se mantienen los servicios nativos de mensajes; eso no demuestra que Meta acepte las notificaciones de una aplicación con otro paquete y firma.

Explorar puede quedar vacío cuando los resultados recibidos no contienen cuentas seguidas verificadas. La búsqueda escrita no exige una relación de seguimiento.

[Compilar y verificar](native/README.md) · [APK original y hashes](native/stock.lock.json) · [Clonado del paquete](native/package-clone.md) · [Documentación del antiguo cliente web](docs/web-0.3.7.md)

Los ajustes de actualización usan B por defecto. Para activar A se acepta una advertencia en inglés y se desliza para confirmar. Al pie aparecen Inhouse y GitHub. [Pruebas Android 14–16](native/validation/0.4.18.json).
