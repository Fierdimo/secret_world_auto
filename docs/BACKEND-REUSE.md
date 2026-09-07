# Reutilización del backend de Secret World

Inventario de lo que ya existe en `../secret_world` y sirve tal cual, lo que hay
que adaptar y lo que hay que construir. Todas las referencias son al código del
proyecto hermano.

## Hosts y autenticación

| Host | Uso | Referencia |
|---|---|---|
| `https://api.sworld.co.uk/api/v1` · `/v2` | API principal | [apiClient.ts:110](../../secret_world/src/core/api/apiClient.ts#L110) |
| `https://ttsapi.sworld.co.uk` | Texto a voz | [apiClient.ts:115](../../secret_world/src/core/api/apiClient.ts#L115) |
| `https://aiapi.sworld.co.uk` | Itinerarios IA | [apiClient.ts:116](../../secret_world/src/core/api/apiClient.ts#L116) |
| `https://sworld.co.uk` | Pasaporte, fotos, compartidos | [passportService.ts:31](../../secret_world/src/core/api/passportService.ts#L31) |

Dos esquemas de autenticación conviven, y ambos son adaptables al coche:

- **v1** — `Authorization: Bearer <token>` de la cuenta de usuario.
- **v2** — `device-token` + `build_number` + `platform`, sin login.

El esquema v2 encaja perfectamente con RF‑4.1 (app usable sin cuenta): el
vehículo se identifica con un device‑token propio y sube a v1 cuando el usuario
vincula su cuenta. **La cabecera `platform` necesita un valor nuevo** para el
coche, para poder segmentar tráfico y analítica.

Todas las llamadas ya mandan `lang`, así que la multi‑idioma del catálogo
funciona sin cambios de contrato.

## Reutilizable tal cual

| Capacidad | Endpoint | Notas para el coche |
|---|---|---|
| Lugares por posición y radio | `POST /places` con `lat`, `long`, `page`, `per_page`, `content_type`, `min_range`, `max_range`, `rating`, `lang` | Es exactamente lo que necesita la superficie de descubrimiento. Ver [PlacesApi.ts](../../secret_world/src/services/PlacesApi.ts) |
| Contenido de un lugar | `getContentInfo(id, story_content_type, language)` | Fuente del texto que se convierte en audio |
| Itinerarios del usuario | `POST /itineraries`, `GET /itinerary-detail/{id}`, `POST /itinerary-places/{oid}` | Solo lectura en el coche; ver [ItineraryService.ts](../../secret_world/src/core/api/ItineraryService.ts) |
| Favoritos | `FavoriteService` en [userServices.ts](../../secret_world/src/core/api/userServices.ts) | Alimenta el nodo "Mis favoritos" del árbol de media |
| Traducciones de interfaz | `locales/` (17 idiomas) | Se migran a recursos Android; el trabajo es de formato, no de contenido |

## Adaptable con trabajo

### Texto a voz
Hoy: `POST /text-to-speech` con `{ language, text, voice: "alloy" }` contra
`ttsapi.sworld.co.uk`, **bajo demanda**, disparado desde
[ListenButton.tsx](../../secret_world/src/components/ListenButton.tsx).

Para el coche no vale: latencia impredecible, dependencia de red en carretera y
coste por reproducción. Hace falta un **pipeline de pre‑generación** que
produzca y persista un fichero de audio por (lugar × idioma) en CDN, con
`duration`, `bytes` y `etag` en los metadatos del lugar. La API de TTS actual
sirve como motor del pipeline; lo que cambia es cuándo se invoca.

### Suscripciones
Hoy el estado premium se resuelve en cliente con RevenueCat + tienda. Para que
una compra en el coche valga en el móvil y viceversa (RF‑5.4), hace falta un
endpoint servidor que devuelva el entitlement del usuario **con independencia de
la tienda**, con Pleos añadido como origen de compra junto a App Store, Play y
Huawei. El precedente existe: el proyecto ya soporta el ecosistema Huawei con
IAP propio ([android/app/build.gradle:180-188](../../secret_world/android/app/build.gradle#L180-L188)).

## La v1 no requiere cambios de backend

Bajo el modelo de acceso A de [MONETIZATION.md](MONETIZATION.md) (no se vende
dentro del coche; el desbloqueo llega al vincular una cuenta con suscripción
activa), **todo lo que necesita la v1 ya existe y está en producción**.

Esto estuvo en duda mientras la voz dependía de pre-generar audio. Con la decisión
de llevar **TTS embebido en la app** ([VOICE-OPTIONS.md](VOICE-OPTIONS.md)), la
síntesis ocurre en el dispositivo a partir del texto que el catálogo ya sirve, y
la dependencia desaparece: ni pipeline de generación, ni ficheros en CDN, ni
metadatos nuevos. **El contenido vuelve a ser texto.**

Las siguientes piezas se resuelven en el cliente, y por eso no hacen falta
endpoints nuevos:

| Necesidad | Cómo se resuelve sin tocar backend |
|---|---|
| Contenido a lo largo de la ruta | Muestreo de puntos sobre la polilínea contra `POST /places/cards`. Más llamadas, mismo endpoint |
| Audio de la narración | **Motor TTS embebido en la app**, sintetizando por delante desde el texto del catálogo. Sin backend |
| Vinculación de itinerarios | Código corto ya existente y `GET /trip/{code}.json`, que es público |
| Cuenta sin login tecleado | Esquema v2 por `device-token`, que ya funciona sin credenciales |
| Estado de suscripción | Consulta al servidor de entitlements actual |

## Optimizaciones posteriores a la v1

Ninguna bloquea el lanzamiento. Se abordan cuando haya uso real que las justifique.

1. **`POST /places/corridor`** — lugares sobre una polilínea en una sola llamada,
   en lugar de muestrear. Ahorra tráfico y batería, no habilita nada nuevo.
2. **Audio pre-generado en CDN** — vuelve a ser opcional, y es el **plan B** si
   el motor embebido no pasa el listón de calidad de [SPEC.md §9](../SPEC.md).
   La tubería ya existe (`POST /text-to-speech` contra `ttsapi.sworld.co.uk`); lo
   que cambiaría es invocarla al publicar contenido en vez de al pulsar play.
3. **Flag `car_ready` en el catálogo** — filtrar lugares aptos para coche en
   servidor en vez de en cliente.
4. **Ingesta de analítica con `platform: pleos`** — hoy se puede mandar como
   plataforma genérica; conviene separarla cuando haya volumen.

## El único cambio que sí sería obligatorio

Si Pleos **exige** que todo desbloqueo de contenido de pago pase por su pasarela
(pregunta 2 de [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md)), entonces hay que:

- Integrar su SDK de facturación en el cliente.
- Añadir un endpoint que registre y valide la compra hecha en el coche.
- Unificar entitlements entre cuatro orígenes: App Store, Play, Huawei y Pleos.

**Es el único punto de todo el proyecto donde el backend tendría que cambiar**, y
depende por completo de la respuesta a esa consulta.

## Lo que NO se toca

Pasaporte, subida de fotos, referidos, competiciones, blog, vídeo, booking de
experiencias, publicidad móvil. Nada de eso entra en la v1 del coche, y el
backend correspondiente se queda como está.
