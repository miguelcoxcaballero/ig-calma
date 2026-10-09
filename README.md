# Instagram Calma 0.4.9 — prueba nativa

Esta versión parte del APK original de Instagram **439.0.0.37.89** y aplica parches propios de Calma. Inicio, historias, mensajes y reproducción usan la aplicación nativa. No incluye paquetes de parches de terceros.

**[Descargar APK nativo](https://github.com/miguelcoxcaballero/ig-calma/releases/download/v0.4.9/IG-Calma-0.4.9.apk)** · [Publicación y comprobaciones](https://github.com/miguelcoxcaballero/ig-calma/releases/tag/v0.4.9) · [Código de los parches](native/patches/)

Requiere **Android 9 o posterior y ARM64**. No necesita root. Usa el paquete y la firma de Calma; puede coexistir con Instagram oficial. La sesión nativa requiere iniciar sesión de nuevo al venir del cliente web.

## Feed

El selector superior ofrece **For you, Favourites, Friends y Following** usando el menú nativo de Instagram. Friends es la opción inicial: cuentas que sigues y que también te siguen. Following muestra cuentas que sigues. Ambos guardan timelines independientes de **48 horas**, ordenados de más nuevo a más antiguo, sin duplicados y con las historias del encabezado. Al final aparece **That's it** con una cara sonriente. Desliza hacia abajo para actualizarlos.

For you conserva el orden del algoritmo, sus publicaciones y Reels, sin anuncios; Favourites conserva el feed de favoritos original, sin aplicar la ventana o el filtro de amistades. **For you comienza bloqueado**: gana 1 minuto al comenzar cada nueva hora, incluso con la app cerrada. Cada crédito caduca 24 horas después de ganarlo. El selector muestra minutos y segundos disponibles; en **Tu feed** se puede configurar de 0 a 60 minutos por hora. Sólo se cobra el uso en primer plano de For you y de sus Reels; los DMs, Explorar y el tiempo fuera de la app no gastan saldo. Al agotarse, vuelve a Friends. No abre For you automáticamente ni entrega saldo retroactivo al instalar.

La 0.4.8 corrigió la entrega de Friends y Following: enlaza cada respuesta con su petición nativa, fija la fuente Following en los parámetros HTTP finales y muestra los amigos recién confirmados mediante la entrega local de Instagram, sin descargar otra cabecera. La precarga puede reintentar una petición rechazada por estar ocupado el controlador. [Funcionamiento y límites](native/feed.md).

En los ajustes habituales de Instagram aparece **Tu feed**. Permite ajustar los minutos por hora de For you y activar **Bloquear scroll de Reels**. Los Reels siguen apareciendo en el feed y se pueden abrir; el bloqueo impide pasar al siguiente o al anterior. Durante el uso de For you con minutos disponibles, el visor mantiene su navegación normal. Los ajustes adicionales siguen el modo claro u oscuro del dispositivo.

Explorar muestra contenido de cuentas que sigues, aunque no te sigan de vuelta. Al escribir en Buscar puedes encontrar cualquier cuenta; sin texto no aparecen sugerencias ni historial. Los Reels enviados por mensaje se abren directamente y, con el bloqueo activo, solo permiten ver ese clip. No esperan a comprobar la relación con el remitente. Los modelos de relaciones proceden de la sesión nativa; no se introducen listas manualmente.

La publicidad se bloquea incluso cuando sigues al anunciante. Se filtran los anuncios del feed y de las cuadrículas de Explorar/búsqueda y se rechazan las inserciones de anuncios entre historias y la fuente específica de anuncios de Reels. Una página compuesta solo por anuncios conserva su cursor para seguir cargando contenido normal. [Implementación y límites](native/ads.md).

## Actualizaciones

Se reutilizan el comprobador y popup de **Inhouse Read** y el instalador Kotlin de **Inhouse Photos**, con sus fuentes originales conservadas en `vendor/`. El comprobador se inicia con la primera pantalla nativa real, después de la pantalla provisional de arranque, y conserva el aviso hasta que puede mostrarlo. **Tu feed → Actualizar aplicación** abre el mismo actualizador.

El [manifiesto anterior](android-update.json) y el [nativo](native/android-update.json) anuncian el mismo APK firmado, para que las instalaciones anteriores también reciban el aviso de la 0.4.9. Se publica con `required: true`, como exige el comprobador original de Read para mostrarlo. Los cambios al actualizador se limitan a la conexión con Android y a la configuración de marca, versión y URL; [procedencia y pruebas](native/updater.md).

## Estado de la prueba

La APK 0.4.9 se valida como actualización sobre la 0.4.8 y en el arranque sin sesión en emuladores oficiales de **Android 14, 15 y 16**. La instrumentación usa las clases originales de Instagram para comprobar la entrega del feed, el bloqueo del ViewPager2, el clip seleccionado y la recuperación de la paginación al usar minutos de For you. No prueba reproducción ni respuestas de una cuenta autenticada. [Resultados vinculados al SHA-256](native/validation/0.4.9.json) · [Ejecución de las pruebas](https://github.com/miguelcoxcaballero/ig-calma/actions/runs/38004528156).

La instrumentación crea los cuatro modelos del selector y las filas IGDS originales, verifica el saldo y el estado bloqueado, y comprueba el final del timeline en ambos temas. Las pruebas locales cubren caducidad, persistencia, cambios de hora y separación entre Friends y Following.

La compilación y las comprobaciones de firma, recursos, clases nativas, destinos de los parches y pruebas automatizadas son reproducibles. **No se ha probado el inicio de sesión, la fluidez, la reproducción ni las notificaciones push con una cuenta real en un teléfono.** Se mantienen los servicios nativos de mensajes; eso no demuestra que Meta acepte las notificaciones de una aplicación con otro paquete y firma.

La comprobación de amistades debe completarse antes de cerrar el timeline. Las respuestas lentas ya no se descartan a los 2,5 segundos. Explorar puede quedar vacío cuando los resultados recibidos no contienen cuentas seguidas verificadas. La búsqueda escrita no exige una relación de seguimiento.

[Compilar y verificar](native/README.md) · [APK original y hashes](native/stock.lock.json) · [Clonado del paquete](native/package-clone.md) · [Documentación del antiguo cliente web](docs/web-0.3.7.md)
