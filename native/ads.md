# Publicidad: Instagram 439.0.0.37.89

Los puntos de integración se han identificado en el APK original fijado en
`stock.lock.json`. No se reutilizan parches de otras aplicaciones.

| Superficie | Señal nativa | Aplicación de la regla |
| --- | --- | --- |
| Feed | `Media.EKS()` llama a `X.05ul.A00`, que comprueba el subárbol `injected` (hash `283678192`). `X.05qw.A0C` utiliza esa misma comprobación para construir la publicación patrocinada. | Se excluye antes de comprobar autor, fecha u orden. También se excluye si el anunciante es seguido o mutuo. |
| Tarjetas del feed | `X.05qw.A0x` es `Ad4adDictImpl`; `A0y` es `X.02lY`. El único constructor de `X.02lY` fija su booleano final `A1H=true`, devuelto por `EKS()`. | Se eliminan las tarjetas promocionales aunque no tengan todavía un objeto `Media`. |
| Inserciones del feed | `X.02px` lee `disable_client_insertions` en `X.07do.A0E` y `max_num_possible_ad_insertions` en `A0J`. | Se fijan a `true` y `0`, respectivamente, en todos los modos. |
| Explorar y resultados de búsqueda | `X.032D.A09` es el contenido del tipo `X.031N.AD` (ordinal 32); `A08` contiene el medio normal. | Se descarta el contenido de `A09`; el medio de `A08` también debe superar `Media.EKS()`. |
| Reels | `X.0Es9.Bd0(UserSession)` identifica `ClipsFeedOfAdsResponse_getClipsAdItems`, una fuente separada del feed orgánico. | Devuelve una lista vacía para impedir que esa fuente entregue anuncios al visor nativo. |
| Historias | `X.03sn.EKS()` comprueba el tipo `X.03st.A04`, definido como `ADS_REEL` / `ads_reel`. `X.08S0.E1e` lo inserta en el adaptador de historias. | Se rechaza antes de modificar el adaptador o los registros de inserción. |

El rechazo de historias devuelve `Integer(8)`, la condición nativa
`content_invalid`: `X.0NU2.A00(8)` devuelve `A06`. El llamador `X.01fL.A02`
invalida y retira ese candidato mediante `BF1` y `F0a`; no lo cuenta como una
inserción correcta. Las historias normales y los módulos nativos no patrocinados
continúan por el método original.

Las páginas formadas solo por anuncios conservan los cursores. La fecha de una
creatividad publicitaria no demuestra el final del feed de 48 horas. Los anuncios
tampoco provocan consultas adicionales de amistad. No se eliminan publicaciones
orgánicas por mencionar una marca o por llevar una etiqueta de colaboración.

Validación: `native/tests/feed.py`, `native/tests/discover.py` y
`native/tests/ads_dex.py APK --morphe MORPHE` comprueban los modelos, la continuidad
de paginación y el salto real de la APK. `hook_dex_analysis.py` comprueba también
los registros y la accesibilidad del nuevo método. La validación sin una sesión
iniciada no comprueba anuncios reales de una cuenta, ni permite garantizar
cobertura de todas las campañas o formatos que Instagram pueda activar después.
