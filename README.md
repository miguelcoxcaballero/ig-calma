# Instagram Calma 0.4.3 — prueba nativa

Esta versión parte del APK original de Instagram **439.0.0.37.89** y aplica parches propios de Calma. Inicio, historias, mensajes y reproducción usan la aplicación nativa. No incluye paquetes de parches de terceros.

**[Descargar APK nativo](https://github.com/miguelcoxcaballero/ig-calma/releases/download/v0.4.3/IG-Calma-0.4.3.apk)** · [Publicación y comprobaciones](https://github.com/miguelcoxcaballero/ig-calma/releases/tag/v0.4.3) · [Código de los parches](native/patches/)

Requiere **Android 9 o posterior y ARM64**. No necesita root. Usa el paquete y la firma de Calma; puede coexistir con Instagram oficial. La sesión nativa requiere iniciar sesión de nuevo al venir del cliente web.

## Feed

El feed muestra únicamente publicaciones de amigos: cuentas que sigues y que también te siguen. Reúne el intervalo de **48 horas**, comprueba las amistades y guarda un timeline completo, ordenado de más nuevo a más antiguo y sin duplicados. Conserva las historias del encabezado. Al recorrer ese timeline no se solicitan más páginas; al final aparece **That's it** con una cara sonriente, dentro del feed. Desliza hacia abajo para actualizarlo.

La primera sincronización necesita conexión. Las fotos y vídeos siguen usando la caché nativa de Instagram. Si falla una página o la comprobación de amigos, conserva el timeline anterior y permite reintentar; no confunde un error con el final. [Caché, límites y funcionamiento](native/feed.md).

En los ajustes habituales de Instagram aparece **Tu feed**. Muestra la regla de seguimiento mutuo y permite ocultar Reels. Los ajustes adicionales siguen el modo claro u oscuro del dispositivo.

Explorar muestra contenido de cuentas que sigues, aunque no te sigan de vuelta. Al escribir en Buscar puedes encontrar cualquier cuenta; sin texto no aparecen sugerencias ni historial. Con Reels ocultos, los enviados por amigos mutuos se autorizan desde el mensaje y se bloquean el avance y la carga de otros clips. Los modelos de relaciones proceden de la sesión nativa; no se introducen listas manualmente.

La publicidad se bloquea incluso cuando sigues al anunciante. Se filtran los anuncios del feed y de las cuadrículas de Explorar/búsqueda y se rechazan las inserciones de anuncios entre historias. Una página compuesta solo por anuncios conserva su cursor para seguir cargando contenido normal. [Implementación y límites](native/ads.md).

## Actualizaciones

Se reutilizan el comprobador y popup de **Inhouse Read** y el instalador Kotlin de **Inhouse Photos**, con sus fuentes originales conservadas en `vendor/`. El comprobador se inicia con la primera pantalla nativa real, después de la pantalla provisional de arranque, y conserva el aviso hasta que puede mostrarlo. **Tu feed → Actualizar aplicación** abre el mismo actualizador.

El [manifiesto anterior](android-update.json) y el [nativo](native/android-update.json) anuncian el mismo APK firmado, para que las instalaciones anteriores también reciban el aviso de la 0.4.3. Se publica con `required: true`, como exige el comprobador original de Read para mostrarlo. Los cambios al actualizador se limitan a la conexión con Android y a la configuración de marca, versión y URL; [procedencia y pruebas](native/updater.md).

## Estado de la prueba

La APK 0.4.3 supera la actualización sobre la 0.4.2 y el arranque sin sesión en emuladores oficiales de **Android 14, 15 y 16**, sin cierres ni ANR registrados. [Resultados vinculados al SHA-256](native/validation/0.4.3.json) · [Ejecución de las pruebas](https://github.com/miguelcoxcaballero/ig-calma/actions/runs/37988421107).

La prueba de instrumentación también crea el modelo original del final, dibuja el smiley y el texto en ambos temas y comprueba la accesibilidad y la reutilización de la fila en las tres versiones de Android.

La compilación y las comprobaciones de firma, recursos, clases nativas, destinos de los parches y pruebas automatizadas son reproducibles. **No se ha probado el inicio de sesión, la fluidez, la reproducción ni las notificaciones push con una cuenta real en un teléfono.** Se mantienen los servicios nativos de mensajes; eso no demuestra que Meta acepte las notificaciones de una aplicación con otro paquete y firma.

La comprobación de amistades debe completarse antes de cerrar el timeline. Las respuestas lentas ya no se descartan a los 2,5 segundos. Explorar puede quedar vacío cuando los resultados recibidos no contienen cuentas seguidas verificadas. La búsqueda escrita no exige una relación de seguimiento.

[Compilar y verificar](native/README.md) · [APK original y hashes](native/stock.lock.json) · [Clonado del paquete](native/package-clone.md) · [Documentación del antiguo cliente web](docs/web-0.3.7.md)
