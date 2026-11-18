# Navegación nativa y demo en pantalla dividida

Actualizado el 2026‑11‑06.

## La decisión

Pleos es Android Automotive (AAOS). En el coche, **Secret World no dibuja mapa**:
la app es un panel, el equivalente a la caja de la izquierda de la versión
actual, y la ruta la lleva el **navegador nativo** del vehículo. La app le pasa
el destino y las paradas por **NaviHelper** y, mientras se conduce, cuenta lo que
hay alrededor.

Consecuencias:

- **Mapbox no va en el coche.** La licencia de automoción deja de importar.
- La versión actual pasa a ser la **prueba de concepto** y la demo en teléfono.
  *Actualizado el 2026‑11‑17: sin Mapbox tampoco ahí, porque no tenemos licencia;
  todas las versiones usan ya el mapa propio de OpenStreetMap.*
- Lo que más se reaprovecha es justo lo que ya está en el panel: lugares con
  foto, búsquedas, Mis itinerarios, la línea de tiempo del itinerario y la voz.

## Lo que dice la investigación y lo que hemos medido

Contrastado con lo medido en la imagen de Connect‑W (caas 4.2.9) y en la
v2.1.5. Detalle en [PLEOS-SDK-SURFACE.md](PLEOS-SDK-SURFACE.md).

| Afirmación | Medido | Conclusión |
|---|---|---|
| Existe un **NaviHelper SDK** para hablar con el navegador sin abrirlo | `ai.pleos.playground.service.navi.NaviService` está en la imagen y devuelve binder vivo. La librería cliente expone `requestRoute`, `addWaypoint`, `removeWaypoint`, `startRouteGuiding`, `getRouteStateInfo`, `getCurrentLocationInfo`… y 26 eventos (`onTBTInfo`, `onDestinationArrived`, `onRouteStarted`…). Permisos de nivel `normal` | **Cierto.** Es el camino |
| Se lanza el navegador con `google.navigation:q=…` y `setPackage("com.pleos.navi")` | `google.navigation:` es el esquema de Google Maps. El navegador nativo es `ai.umos.maps.android.navigation.app`, y **no atiende `geo:`** | **Falso para Pleos.** El traspaso va por NaviHelper (`requestRoute` + `startRouteGuiding`), no por intents |
| Declarar `android.intent.category.APP_MAPS` permite convivir con el navegador | Sin verificar. Esa categoría presenta la app como **aplicación de mapas**, es decir, como alternativa al navegador | **No declararla** sin confirmación de Pleos: somos una app de lugares que acompaña al navegador |
| El inicio tiene un área fija `NaviContainer` para el mapa | Sin verificar; no aparece en nada de lo medido | Preguntar a Pleos |
| Pantalla dividida / multiventana | AAOS la admite; en Pleos, sin verificar | Preguntar a Pleos qué categoría y qué tamaños |

## La demo para clientes: pantalla dividida simulada

Mientras no haya coche ni librería, la mejor forma de enseñarlo es **una
pantalla partida en dos**:

| Mitad | Qué es | En el coche |
|---|---|---|
| Navegador | Un mapa que **hace de navegador nativo**: ruta, destino y paradas | Lo dibuja el navegador de Pleos |
| Secret World | El panel actual: lugares, búsquedas, itinerarios, voz | Igual |

Las dos mitades se coordinan como lo harán en el coche: elegir un sitio y pulsar
«Llévame» le pasa el destino al navegador; iniciar un itinerario le pasa las
paradas; al llegar a una parada, el navegador avisa y la app la cuenta.

**La regla que hace honesta la demo:** la mitad del navegador solo acepta
**lo que NaviHelper sabe hacer**. Todo lo que el panel le pida tiene que tener
su llamada real:

| Lo que se ve en la demo | Llamada de NaviHelper que lo hará en el coche |
|---|---|
| «Llévame» a un sitio | `requestRoute` + `startRouteGuiding` |
| Las paradas del itinerario en el mapa | `requestRoute` (destino) + `addWaypoint` (cada parada) |
| Quitar o reordenar una parada | `removeWaypoint` / `addWaypoint` / `requestReRoute` |
| La app se calla antes de un cruce | evento `onTBTInfo` |
| «Has llegado a la parada» | evento `onDestinationArrived` |
| Dónde estoy, cuánto falta | `getCurrentLocationInfo`, `getRouteStateInfo` |

Así la demo no promete nada que el coche no haga, y el código de la mitad
simulada se convierte, sin cambiar el panel, en el cliente real de NaviHelper.

## La pregunta abierta: varios puntos en el mapa nativo

Hoy, con Mapbox, el mapa enseña bastante más que una ruta:

- **Decenas de puntos**: lo de «Qué ver» y los resultados de cada búsqueda.
- El **anillo** del radio de acción y las **ondas** del rastreo.
- **Marcadores con la foto** de cada parada del itinerario.

Del navegador nativo sabemos esto:

- **Las paradas del itinerario sí se verían**, como paradas de la ruta
  (`addWaypoint`). Falta saber **cuántas** admite.
- El servicio declara permisos de **personalización del mapa y de la ruta**
  (`NAVI_CUSTOM_MAP`, `NAVI_CUSTOM_ROUTE`, `NAVI_CUSTOM_ETC`). Sugieren que se
  pueden dibujar cosas propias, pero **en la API cliente que vimos no hay ningún
  método de marcadores**. Puede que esa parte esté reservada a apps del sistema.
- No hay nada que apunte a anillos, ondas ni marcadores con imagen.

Según lo que responda Pleos, la demo debería enseñar uno de estos dos casos:

| Caso | Qué permite el navegador | Qué enseña la demo |
|---|---|---|
| **A** | Marcadores propios (POI de terceros) | Como hoy: los puntos de búsqueda y de «Qué ver» en el mapa del navegador |
| **B** | Solo destino y paradas | El mapa enseña solo la ruta y las paradas; los resultados de búsqueda viven en el panel, cada uno con su «Llévame» |

**Hasta tener respuesta, la demo debería mostrar el caso B**, y el A solo como
posibilidad a confirmar. Enseñar puntos en el mapa que luego el coche no pueda
pintar sería prometer de más.

## Y en otros navegadores

Pintar puntos hará falta también fuera de Pleos, y ahí el panorama es
igual o más cerrado:

| Plataforma | Qué puede hacer una app de lugares en el mapa del navegador |
|---|---|
| Google Maps / Waze (Android Auto, AAOS con Google) | **Nada que pintar.** Solo pasarles un destino (y en Waze, alguna parada) |
| Car App Library, plantilla de lugares con mapa (`PlaceListMapTemplate`) | El **sistema dibuja un mapa con nuestros puntos**: pocos (del orden de seis, a confirmar con la versión de la librería) y **sin rutas** |
| Pleos | Depende de `NAVI_CUSTOM_MAP` (arriba) |

**Conclusión: no se puede contar con que el navegador pinte nuestros puntos.**
Lo imprescindible es **ver los puntos en un mapa** mientras se explora y
mientras se arma un itinerario; eso lo tiene que dibujar la app, y sin Mapbox.
Las rutas no: las traza el navegador al iniciar. El reparto queda así:

| Momento | Quién dibuja | Qué se ve |
|---|---|---|
| **Explorar** («Qué ver», búsquedas) | La app, en su mapa | Los lugares como puntos, el radio de acción, el elegido destacado |
| **Armar el itinerario** | La app, en su mapa | Las paradas como puntos **numerados en orden**, y lo de alrededor para añadir. Sin ruta trazada |
| **Iniciar** | El navegador del coche | Destino y paradas que le pasa la app; la ruta, la guía y la voz de las maniobras son suyas |

### Un mapa propio sin Mapbox

*Hecho el 2026‑11‑17, con MapLibre y los estilos de OpenFreeMap: ver
[MAP-OPTIONS.md](MAP-OPTIONS.md). Ya corre en el emulador local de Pleos.*

Al no tener que trazar rutas, basta con un mapa que pinte puntos:

| Pieza | Opción | Por qué |
|---|---|---|
| Motor | **MapLibre Native** (Android) | Continuación de código abierto del motor de Mapbox, licencia BSD. Funciona en AAOS. Su API es casi la misma: el cambio es contenido |
| Teselas | **OpenStreetMap** en vectorial: un archivo PMTiles de Protomaps alojado por nosotros, o un proveedor como MapTiler | Sin contrato por vehículo: solo la atribución de OSM (ODbL). Alojadas por nosotros, el coste es el del almacenamiento |
| Estilo | Uno propio, día y noche, a juego con el tema de la app | Lo que ya hacemos con el estilo monocromo de Mapbox |
| Sin red | El lienzo propio (`MapCanvas`) que ya existe | Sigue valiendo de respaldo: pinta puntos sin teselas |

Lo que hoy dibuja la versión con Mapbox y que **desaparece**: la línea de la
ruta entre paradas y el recorrido de la presentación cinematográfica. La
presentación puede seguir volando de parada en parada, sin trazar el camino.

Pendiente de comprobar antes de decidir: que MapLibre aguante en el hardware
de Pleos, el tamaño de las teselas de las zonas que cubra el catálogo y si a
Pleos le parece bien una app de lugares con mapa propio junto al navegador.

## Qué preguntar a Pleos

1. La librería cliente **NaviHelper ≥ 2.0.2** (versionCode 42), ya pedida (ver
   [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md), 1b).
2. **`NAVI_CUSTOM_MAP`**: ¿permite a una app de terceros poner marcadores o POI
   propios en el mapa del navegador? ¿Cuántos, con qué icono o imagen, y si son
   tocables con aviso a nuestra app?
3. **Paradas**: ¿cuántos `addWaypoint` admite una ruta?
4. **Pantalla dividida**: ¿puede la app quedarse junto al navegador? ¿Qué
   categoría hay que declarar, y existe el `NaviContainer`?
5. ¿Atiende el navegador algún intent (`geo:` u otro) para quien no use
   NaviHelper?

## Lo que queda para después

Cuando toque:

- Una composición «solo panel» para la versión de Pleos. *Empezada: la app ya
  se adapta a los dos huecos de Pleos, y en el pequeño enseña la lista a todo
  el ancho, sin mapa.*
- La demo en pantalla dividida para el teléfono, con la mitad del navegador
  limitada a las operaciones de la tabla de arriba.
- El cliente de NaviHelper detrás del mismo contrato, cuando llegue la librería.
