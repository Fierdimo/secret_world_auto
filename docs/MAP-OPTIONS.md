# El mapa: qué hay, qué falta y qué cuesta

Actualizado el 2026‑09‑04.

## Estado actual · implementado el 2026‑09‑04

**Decisión tomada**: Mapbox **solo para el MVP**; en el vehículo se usará el
servicio de mapas que traiga el dispositivo.

Para que eso no sea una reescritura el día que se cambie, el mapa vive detrás de
un hueco: `MapState` describe qué hay que dibujar y **cada sabor de compilación
aporta su implementación**.

| Sabor | Implementación | Mapbox en el APK |
|---|---|---|
| `bench` | Mapbox, estilo `dark-v11`, con los lugares del catálogo como marcadores | 51 entradas |
| `vehicle` | Lienzo propio, hasta que Pleos entregue el mapa nativo | **0 entradas** |

Verificado: el APK del vehículo **no lleva un byte de Mapbox**, porque el SDK se
enlaza solo en `benchImplementation`. Eso es lo que hace real la decisión, en vez
de una intención escrita en un documento.

La clave pública **no viaja en el APK**: se pide al backend en ejecución con
`POST /get-key?type=mapbox_api_key`, igual que hace la app móvil. Se puede rotar
sin publicar versión, y un APK descompilado no la regala. Si el backend no
responde, se dibuja el lienzo propio en lugar de una pantalla vacía.

El **token de descarga del SDK** vive en `~/.gradle/gradle.properties`, fuera del
repositorio.

## El lienzo propio, que sigue haciendo falta

El MVP dibuja el mapa **nosotros**, con `MapCanvas`: proyección plana centrada en
el vehículo, rotada según el rumbo, con ruta, marcadores y descarte de etiquetas
solapadas. Sin tiles, sin red y sin licencia.

Eso demuestra el principio que ordena la interfaz —**el mapa es el lienzo
permanente, no una subventana**— pero es un mapa abstracto: no hay calles, ni
edificios, ni relieve.

## Mapbox: lo que ya existe

La app móvil de Secret World **ya usa Mapbox**, así que no partimos de cero:

| Pieza | Dónde está |
|---|---|
| SDK | `@rnmapbox/maps` 10.2.6, que envuelve el **Maps SDK for Android** |
| Estilo | `mapbox://styles/mapbox/dark-v11` — oscuro, que es justo lo que quiere un coche |
| Clave pública | La sirve **vuestro propio backend**: `Credentials.getCredentials("mapbox_api_key")` |
| Rutas | Directions vía proxy propio: `/maps/directions/v5/mapbox/driving/` |

**El Maps SDK for Android funciona en AAOS**: es un SDK de Android corriente y el
vehículo es Android. Técnicamente no hay obstáculo.

## Las dos condiciones

### 1. Token de descarga del SDK — bloqueante técnico, resoluble hoy

El repositorio Maven de Mapbox está en
`https://api.mapbox.com/downloads/v2/releases/maven` y **responde 404 sin
credenciales** (Mapbox devuelve 404 en lugar de 401 cuando falta autenticación).

El proyecto móvil lo lee de `MAPBOX_DOWNLOADS_TOKEN` o
`RNMAPBOX_MAPS_DOWNLOAD_TOKEN`, y **no está en el repositorio**: vive en el
`gradle.properties` local de quien compila o en el entorno de CI.

Hace falta el **token secreto** (`sk.…`) de la cuenta de Mapbox, con el permiso
`DOWNLOADS:READ`. Con él, la integración es de un rato.

### 2. Licencia para uso en vehículo — la pregunta cara

Esta es la que importa, y **no es técnica**. Los términos estándar de Mapbox
cubren aplicaciones móviles y web. El uso **embarcado en un vehículo** —una
unidad de infotainment instalada de fábrica y distribuida con el coche— entra en
su oferta de automoción, que es un acuerdo comercial aparte.

Ya estaba anotado como riesgo en [../PLAN.md](../PLAN.md): *"Licencia de mapas
embebida (Mapbox automotive es contrato aparte y caro)"*. Sigue siéndolo.

**Que la app móvil tenga contrato con Mapbox no implica que ese contrato cubra el
coche.** Antes de construir producto encima hay que preguntárselo a Mapbox
directamente, con el caso de uso descrito: app publicada en el App Market de
Hyundai Motor Group, preinstalable en vehículo.

## La tercera vía, que sigue siendo la mejor si aparece

El **mapa nativo del vehículo**. Pleos tiene `ai.umos.maps.android.navigation.app`
y el servicio `NaviService`, y usar el mapa del coche evitaría la licencia entera
además de encajar visualmente con el resto del sistema. Depende de la librería
cliente que Pleos no ha entregado — el mismo bloqueante que "Llévame ahí".

## Recomendación

1. **Pedir el token de descarga** y montar Mapbox detrás del mismo hueco que hoy
   ocupa `MapCanvas`. Es un cambio contenido: la firma
   `MapCanvas(vehicle, route, places, highlighted)` es toda la superficie de
   contacto con el resto de la app.
2. **En paralelo, preguntar a Mapbox por la licencia de automoción** con el caso
   de uso real. Es una decisión de negocio con coste, y conviene conocerla antes
   de que el mapa se vuelva estructural.
3. **No descartar el mapa nativo**: si Pleos entrega NaviHelper, probablemente sea
   la mejor opción por integración y por coste.

Mientras tanto el lienzo propio sostiene la demostración sin comprometer nada.
