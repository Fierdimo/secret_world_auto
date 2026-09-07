# La superficie real del SDK de Pleos

Medido sobre el emulador `Pleos_Connect_v2.1.5` el 2026‑09‑03, cruzando el portal
de Playground con lo que declara la propia imagen. Complementa
[../spikes/probe/RESULTS.md](../spikes/probe/RESULTS.md), que midió las APIs
estándar de Android.

> **La conclusión que lo cambia todo**: casi nada de lo que necesitamos va por las
> APIs estándar de Android. Va por una capa propia de Pleos, con su propio espacio
> de permisos `pleos.car.permission.*` y sus propios servicios vinculables. Buscar
> por el camino de AOSP daba falsos negativos.
>
> **Y la que ordena el calendario**: lo que existe en la imagen no es lo mismo que
> lo que está publicado. Los permisos de Gleo están declarados en el emulador, pero
> en el portal figuran como **Request Required** y **coming soon**. Ver
> [§ Disponibilidad](#disponibilidad-lo-que-se-puede-usar-hoy).

## Espacio de permisos

Todo lo relevante vive bajo **`pleos.car.permission.*`**, y el nivel de
protección es la buena noticia: **casi todo es `normal`**, es decir, se concede
al instalar, sin diálogo al usuario ni fricción de consentimiento.

| Permiso | Protección | Lo declara | Para qué |
|---|---|---|---|
| `NAVI_ROUTE` | normal | `service.navi` | Leer destino, paradas, ruta activa, TBT, favoritos, destinos recientes, cargadores, ubicación |
| `NAVI_ROUTE_SEARCH` | normal | `service.navi` | **Fijar destino**, añadir/quitar paradas, recalcular, buscar ruta, iniciar guiado |
| `NAVI_CUSTOM_MAP` / `NAVI_CUSTOM_ROUTE` / `NAVI_CUSTOM_ETC` / `NAVI_NOTIFY` | normal | `service.navi` | Personalización de mapa y ruta, notificaciones |
| `TTS_SERVICE` | **normal** | `caas` | **Síntesis de voz de la plataforma** |
| `STT_SERVICE` | **normal** | `caas` | **Reconocimiento de voz de la plataforma** |
| `LLM_SERVICE` | **normal** | `caas` | **Modelo de lenguaje de la plataforma** |
| `FUSED_LOCATION` | **dangerous** | `vehiclebridge.servicemeta` | Ubicación del vehículo. El único que pide consentimiento |
| `CAR_INFO`, `CAR_DRIVE`, `CAR_ENERGY`, `CAR_SAFETY`, `CAR_LIGHTS`, `CAR_DOORS`, `CAR_WINDOWS`, `CAR_SEATS`, `CAR_MIRRORS`, `CAR_CLIMATE` | normal | `service.vehicle` | Lectura de estado del vehículo |
| `CONTROL_CAR_*` (los mismos) | normal | `service.vehicle` | Control del vehículo |
| `ADAS_*` | normal | `vehiclebridge.servicemeta` | Objetos detectados, mapa local, avisos de conducción |
| `SYNC_DATA` | normal | `sync.syslog` | Sincronización |

## Servicios vinculables

Verificado con `pm query-services` contra el emulador: los tres resuelven.

| Servicio | Acción para vincular |
|---|---|
| `ai.pleos.playground.caas.service.llm.LLMService` | `ai.pleos.playground.caas.intent.action.LLM_START` |
| `ai.pleos.playground.caas.service.speech.SpeechService` | `ai.pleos.playground.caas.intent.action.SPEECH_START` |
| `ai.pleos.playground.service.navi.NaviService` | `ai.pleos.playground.intent.action.NAVI_START` |

`caas` es el paquete `ai.pleos.playground.caas`, instalado como
`/system_ext/app/AmbientAiService/AmbientAiService.apk`. Declara también las
acciones `intent.action.ai.pleos.connect.platform.tts` y `...stt`.

## Navegación: el API que necesita el MVP

Del portal de Playground, ambos permisos marcados **Ready to Use** y **sin
restricción de llamada durante la conducción**:

**`NAVI_ROUTE`** — lectura: `getDestinationInfo`, `getWaypointInfo`,
`getRouteStateInfo`, `getTBTInfo`, `getBookmarkInfo`,
`getRecentDestinationInfo`, `getChargerOperatorInfo`, `getCurrentLocationInfo`.

**`NAVI_ROUTE_SEARCH`** — escritura: **`requestRoute`** (fija un destino),
`cancelRoute`, `addWaypoint`, `removeWaypoint`, `changeRouteOption`,
`requestReRoute`, `findRoute`, `startRouteGuiding`.

Tres cosas que esto habilita y que no estaban en el plan:

1. **"Llévame ahí" está resuelto**: `requestRoute` + `startRouteGuiding`, con un
   permiso de nivel `normal`. No hace falta `geo:` — que además nadie atiende.
2. **`getTBTInfo` es un regalo para la narración.** Saber la maniobra que viene
   permite callarse justo antes de un cruce complicado en vez de narrar encima.
   Eso es exactamente la diferencia entre copiloto y ruido.
3. **`getRouteStateInfo` da la ruta activa** (RF‑3.2) y `getBookmarkInfo` con
   `getRecentDestinationInfo` dan señal de intención del usuario para recomendar.

## Disponibilidad: lo que se puede usar hoy

El portal de Playground distingue dos estados, y la diferencia decide qué entra en
la v1.

| Capacidad | Estado en el portal | ¿Hay alternativa hoy? |
|---|---|---|
| `NAVI_ROUTE` · `NAVI_ROUTE_SEARCH` | **Ready to Use** | — |
| `TTS_SERVICE` | **Request Required · coming soon** | Sí: audio pre-generado nuestro |
| `STT_SERVICE` | **Request Required · coming soon** | **Sí: `SpeechRecognizer` de Android, verificado vivo** |
| `LLM_SERVICE` | **Request Required · coming soon** | No |

**El reconocimiento de voz ya funciona sin ellos.** Verificado en el emulador: al
llamar a `SpeechRecognizer.startListening()`, el sistema arranca
`ai.umos.ambientai/.AmbRecognitionService`, recibe nuestro intent y **activa el
micrófono** (`onMicActivated: true`). No es un stub. No llegó callback porque el
emulador headless no tiene entrada de audio.

### Qué se puede construir hoy, sin esperar a nadie

| Pieza del nivel 1 | Cómo |
|---|---|
| Fijar destino y guiar | `NAVI_ROUTE_SEARCH`, Ready to Use |
| Ruta activa y maniobras | `NAVI_ROUTE`, Ready to Use |
| Estado del vehículo | `android.car` estándar, verificado funcionando |
| Ubicación | `fused` / `gps` de AOSP, verificados habilitados |
| Escuchar órdenes | `SpeechRecognizer` de AOSP, verificado vivo |
| Hablar | Audio pre-generado nuestro |

**El nivel 1 completo no depende de ninguno de los tres servicios de Gleo.** El
nivel 2, entero, sí.

### Verificado por vinculación: qué está vivo y qué no

Prueba directa desde una app propia — declarar los permisos, vincularse a cada
servicio y mirar qué devuelve. El resultado separa con nitidez lo publicado de lo
anunciado.

| Servicio | `bindService` | Callback | Interfaz |
|---|---|---|---|
| `NaviService` | `true` | **`onServiceConnected`** | **`ai.pleos.playground.navi.INaviService`**, binder vivo |
| `caas` `SpeechService` (STT/TTS) | `true` | **`onNullBinding`** | — |
| `caas` `LLMService` | `true` | **`onNullBinding`** | — |

Cómo se lee esto:

- **La navegación funciona hoy.** El servicio devuelve un binder vivo y su interfaz
  AIDL se llama `ai.pleos.playground.navi.INaviService`. *Ready to Use* es real.
- **Speech y LLM son "coming soon" en el código, no solo en el portal.** No es un
  problema de permisos ni de empaquetado: `bindService` devuelve `true` y no hay
  `SecurityException`. El componente está instalado y acepta la conexión, pero su
  `onBind` **devuelve null**. La implementación no está.

Y un dato que sorprende: **los permisos `pleos.car.permission.*` se conceden solos
al instalar**, incluidos `STT_SERVICE`, `TTS_SERVICE` y `LLM_SERVICE`. Son de nivel
`normal`. El único denegado fue `FUSED_LOCATION`, que es `dangerous` y pide
consentimiento.

Es decir: **el "Request Required" del portal es una puerta de negocio, no una
barrera técnica en esta imagen.** La barrera real es que el servicio devuelve null.

### Sobre reconstruir el contrato por nuestra cuenta

Descartado por la vía práctica: el APK de navegación está ofuscado con R8 —solo
`NaviService`, `NaviApplication` y su receiver conservan nombre— y en un binder los
métodos AIDL son códigos numéricos, no nombres. Además sería un cliente frágil,
sin garantías de compatibilidad y con implicaciones de licencia que no compensan.

**La vía es pedirles la librería.** Y ahora se les puede pedir con precisión: la
interfaz que necesitamos es `ai.pleos.playground.navi.INaviService`.

### El API de STT, para cuando llegue

De la ficha de `STT_SERVICE`, sin restricción de llamada durante la conducción:

| API | Qué hace |
|---|---|
| `request` | Reconocimiento en tiempo real |
| `stop` | Corta el reconocimiento en curso |
| `setSpeechLanguageCode` | Fija el idioma de reconocimiento |
| `sendAudio` | **Convierte PCM ya almacenado en texto** |
| `completeAudioSend` | Avisa de que terminó el envío del PCM |

`sendAudio` y `completeAudioSend` son el detalle interesante: no es solo micrófono
en vivo, se le puede pasar audio grabado. Y `setSpeechLanguageCode` da control
explícito de idioma, que por la API de AOSP no está garantizado.

### El API de TTS, para cuando llegue

De la ficha de `TTS_SERVICE`, sin restricción de llamada durante la conducción:

| API | Qué hace |
|---|---|
| `speak` | Entrega texto para que lo reproduzca |
| `requestSynthesize` | Pide síntesis **para streaming** en la app |
| `setAudioAttributes` | Fija los atributos de audio de la reproducción |
| `stop` | Corta lo que está sonando |

Dos detalles que importan: `requestSynthesize` sugiere que se puede **obtener el
audio** en lugar de solo dispararlo, y `setAudioAttributes` nos deja controlar la
categoría de audio, que es lo que hace falta para convivir con el centro
multimedia y con el foco de audio.

## Lo que sigue faltando

**Las librerías cliente.** Los servicios están exportados y sus acciones
resuelven, así que vincularse es posible; lo que no es público es el **protocolo
AIDL** que hablan. Sin el AAR o el repositorio Maven de Pleos, podemos conectar
pero no entendernos.

Es hoy **el único bloqueante técnico de la v1** — para la navegación, que es lo
único *Ready to Use* que necesitamos. Ver [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md).

Y para el nivel 2, además de la librería hace falta **que el servicio se publique**
y **que nos aprueben la solicitud**. Son dos esperas encadenadas y ninguna está en
nuestra mano: por eso el nivel 2 no lleva fecha en el roadmap.
