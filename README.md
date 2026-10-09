# Instagram Calma 0.4.1 — prueba nativa

Esta versión parte del APK original de Instagram **439.0.0.37.89** y aplica parches propios de Calma. Inicio, historias, mensajes y reproducción usan la aplicación nativa. No incluye paquetes de parches de terceros.

**[Descargar APK nativo](https://github.com/miguelcoxcaballero/ig-calma/releases/download/v0.4.1/IG-Calma-0.4.1.apk)** · [Publicación y comprobaciones](https://github.com/miguelcoxcaballero/ig-calma/releases/tag/v0.4.1) · [Código de los parches](native/patches/)

Requiere **Android 9 o posterior y ARM64**. No necesita root. Usa el paquete y la firma de Calma; puede coexistir con Instagram oficial. La sesión nativa requiere iniciar sesión de nuevo al venir del cliente web.

## Feed

El modo inicial muestra cuentas que sigues. Solicita el feed nativo de «Siguiendo», ordena cada página de más nueva a más antigua y excluye publicaciones con más de **48 horas**. No hay límite por número de publicaciones. Conserva los controles nativos, las historias y la paginación hasta alcanzar un final comprobable. Una página filtrada vacía no fuerza el final.

En los ajustes habituales de Instagram aparece **Tu feed**. Permite elegir cuentas seguidas o amigos por seguimiento mutuo y ocultar Reels. Los ajustes adicionales siguen el modo claro u oscuro del dispositivo.

Buscar y Explorar filtran cuentas no seguidas. Con Reels ocultos, los enviados por amigos mutuos se autorizan desde el mensaje y se bloquean el avance y la carga de otros clips. Los modelos de relaciones proceden de la sesión nativa; no se introducen listas manualmente.

La publicidad se bloquea incluso cuando sigues al anunciante. Se filtran los anuncios del feed y de las cuadrículas de Explorar/búsqueda y se rechazan las inserciones de anuncios entre historias. Una página compuesta solo por anuncios conserva su cursor para seguir cargando contenido normal. [Implementación y límites](native/ads.md).

## Actualizaciones

Se reutilizan el comprobador y popup de **Inhouse Read** y el instalador Kotlin de **Inhouse Photos**, con sus fuentes originales conservadas en `vendor/`. El comprobador se inicia con la primera pantalla nativa real, después de la pantalla provisional de arranque, y conserva el aviso hasta que puede mostrarlo. **Tu feed → Actualizar aplicación** abre el mismo actualizador.

El [manifiesto anterior](android-update.json) y el [nativo](native/android-update.json) anuncian el mismo APK firmado, para que las instalaciones anteriores también reciban el aviso de la 0.4.1. Se publica con `required: true`, como exige el comprobador original de Read para mostrarlo. Los cambios al actualizador se limitan a la conexión con Android y a la configuración de marca, versión y URL; [procedencia y pruebas](native/updater.md).

## Estado de la prueba

La compilación y las comprobaciones de firma, recursos, clases nativas, destinos de los parches y pruebas automatizadas son reproducibles. **No se ha probado el inicio de sesión, la fluidez, la reproducción ni las notificaciones push con una cuenta real en un teléfono.** Se mantienen los servicios nativos de mensajes; eso no demuestra que Meta acepte las notificaciones de una aplicación con otro paquete y firma.

El orden entre páginas depende también de que Instagram respete su feed de Siguiendo. Si entrega una página fuera de orden, Calma evita cortar prematuramente a las 48 horas, pero no recoloca publicaciones que ya estaban renderizadas. El modo opcional Amigos puede omitir temporalmente publicaciones cuya relación todavía no se ha resuelto; una actualización del feed vuelve a evaluarlas. Buscar y Explorar pueden quedar vacíos cuando los resultados recibidos no contienen cuentas seguidas verificadas.

[Compilar y verificar](native/README.md) · [APK original y hashes](native/stock.lock.json) · [Clonado del paquete](native/package-clone.md) · [Documentación del antiguo cliente web](docs/web-0.3.7.md)
