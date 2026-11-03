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
- ~~**Speech y LLM son "coming soon" en el código, no solo en el portal.**~~
  **Corregido el 2026‑11‑03**: lo más probable es que el null lo provocara la
  sonda. Vinculaba con `SPEECH_START` y `LLM_START`, las acciones del
  intent-filter, y en la caas 4.2.9 `onBind` no las reconoce: despacha por otras.
  Ver [§ Imagen Connect‑W de agosto](#imagen-connectw-de-agosto-lo-que-cambia).
  **Comprobado en la v2.1.5 (caas 2.2.7.1) el 2026‑11‑03**: con las acciones
  correctas y el componente explícito, los seis binders (STT, TTS y LLM, cada
  uno en servidor y on-device) devuelven binder vivo, con las mismas interfaces
  que en la 4.2.9. El log de la caas lo confirma:
  `onBind:: invalid intent(ai.pleos.playground.caas.intent.action.LLM_START)`.

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

## Imagen Connect‑W de agosto: lo que cambia

Análisis estático, hecho el 2026‑11‑03, de
`POIP_EMULATOR_33_2685.JG.CW.SYS.KOR.033.005.260810.030000-x86_64`: Connect‑W,
región KOR, AAOS 14 / API 34, `userdebug`, compilada el 2026‑08‑10. **No se ha
arrancado**: es x86_64 y en el Mac Apple Silicon no corre. Para ejecutarla hace
falta un host con KVM ([DEV-ENVIRONMENT.md](DEV-ENVIRONMENT.md), opción 1). Todo
lo que sigue sale de los manifiestos y del bytecode de las APKs.

### Versiones

| Paquete | v2.1.5 (septiembre) | Connect‑W (agosto) |
|---|---|---|
| `ai.pleos.playground.caas` | 2.2.7.1 | **4.2.9** |
| `ai.umos.ambientai` | 3.1.9.2 | 3.5.11 |
| `ai.pleos.playground.service.navi` | 2.0.1 | 2.0.1.3 |
| `ai.pleos.playground.service.vehicle` | — | 2.3.20 |

### `onBind` despacha por acciones que no están en el intent-filter

El manifiesto solo declara `SPEECH_START` y `LLM_START`, pero `onBind` devuelve
binder **solo** para estas acciones (todas con el prefijo
`ai.pleos.playground.caas.intent.action.`). Con cualquier otra, null:

| Acción | Servicio | Binder |
|---|---|---|
| `SPEECH_SERVER_START` / `SPEECH_HYBRID_START` | `SpeechService` | `SttBinder` → `IStt` |
| `SPEECH_ON_DEVICE_START` | `SpeechService` | `OnDeviceSttBinder` → `IOnDeviceStt` |
| `TTS_STREAM_SERVER_START` / `TTS_STREAM_HYBRID_START` | `SpeechService` | `ServerTtsStreamBinder` → `ITtsStream` |
| `TTS_STREAM_ON_DEVICE_START` | `SpeechService` | `OnDeviceTtsStreamBinder` → `ITtsOnDeviceStream` |
| `LLM_SERVER_START` | `LLMService` | `ServerLLMBinder` → `IServerLLM` |
| `LLM_ON_DEVICE_START` | `LLMService` | `OnDeviceLLMBinder` → `IOnDeviceLLM` |

Como esas acciones no están en el filtro, hay que vincular con el **componente
explícito** (`setClassName`), no con `setPackage`. La sonda ya lo hace.

### Interfaces que expone la caas

Las clases conservan su nombre; no las ofusca R8.

- **`IStt`**: `registerApp`, `registerCallback(ISttCallback)`, `request`,
  `requestWithoutRecording`, `sendRecognitionAudio(byte[], boolean)`,
  `setSpeechLanguageCode`, `setLocale`, `setMode`, `setWakeUpWordConfig`,
  `setLMBoostConfig`, `close`. Las respuestas llegan como `onResponse(String, String)`.
- **`ITtsStream`**: `registerApp`, `speak`, `speak2`, `speakWithAudioConfig`,
  **`requestSynthesize(AudioAttributesParcel, String)`**, `setLocale`,
  `setSpeaker`, `setSpeakerType`, `setEncodingType`, `stop`.
- **`IServerLLM`**: `registerApp`, `initialize`, `startChat`, `sendMessage`,
  `generateContent`, `setModelParameter`, más variantes `…WithUaToken`.
- **`IOnDeviceLLM`**: `initialize`, `startChat`, `sendMessageStream`,
  `generateContentStream`.

### Lo que no funciona, o seguramente no

- **`TextToSpeech` de Android no sirve.** `SpeechService` se anuncia ahora como
  motor (`android.intent.action.TTS_SERVICE`), pero su `onSynthesizeText` solo
  escribe un log y `onIsLanguageAvailable` devuelve 0. El motor existe y no
  produce audio.
- **No hay modelos on-device.** La caas lleva los motores nativos
  (`libairspeech_stt.so`, `libairspeech_tts.so`, onnxruntime), pero los modelos
  los entrega `ai.umos.gleo.provider` / `ai.pleos.gleoai.model.provider`, que
  **no está instalado** en la imagen. Las variantes on-device se vincularán, pero
  lo esperable es que no tengan modelo.
- **El modo servidor necesita red y credenciales.** Va por gRPC y las APIs piden
  `registerApp`, `clientId` y `uaToken`. Previsiblemente es lo que entrega la
  aprobación del "Request Required" del portal.
- **Idiomas sin confirmar.** Los recursos del asistente traen `ko`, `en`, `es` y
  `fr`; no `it`. Qué idiomas acepta el servidor solo se sabe ejecutando.
- **`AmbRecognitionService` ahora es `exported=false`.** Con `SpeechRecognizer`
  lo vincula el sistema y no debería afectar, pero hay que confirmarlo en
  ejecución.

### Navegación: el cliente viene en la imagen

`INaviService` es un protocolo genérico: `registerCallback`, `requestToNavi`,
`requestToNaviProvider` y `unregisterCallback`, con `onResponse` como callback.
La librería cliente **NaviHelper** está embebida en `AmbientAiApp`, `btphonehmi`
y otras apps del sistema, con la API visible:

- **Métodos**: `initialize`, `requestRoute`, `cancelRoute`, `addWaypoint`,
  `removeWaypoint`, `requestReRoute`, `changeRouteOption`, `getRouteStateInfo`,
  `getDestinationInfo`, `getCurrentLocationInfo`, `getBookmarkInfo`, `release`.
- **Eventos** de `NaviHelperEventListener` (26): `onTBTInfo`,
  `onDestinationArrived`, `onRouteStarted`, `onCurrentRoadSpeedLimit`,
  `onCameraAlert`, `onNavigationStatus`…

`NaviService` exige `sdk.navi.helper` 2.0.2 (versionCode 42) como mínimo; la caas
exige `sdk.tts` y `sdk.stt` 2.1.5.

### Qué hacer con esto

Sacar las AIDL de las APKs funcionaría técnicamente, pero sería un cliente sin
soporte, con el mismo problema de licencia de
[§ Sobre reconstruir el contrato](#sobre-reconstruir-el-contrato-por-nuestra-cuenta).
Lo útil es poder **pedir las librerías por su nombre exacto**: NaviHelper ≥ 2.0.2,
`sdk.tts` y `sdk.stt` ≥ 2.1.5 y el cliente de `IServerLLM`, junto con las
credenciales de `registerApp`.

## Llamadas reales en la v2.1.5 (2026‑11‑03)

Hechas desde la sonda contra el emulador `Pleos_Connect_v2.1.5` (caas 2.2.7.1), con
Parcel en crudo: los descriptores, los códigos de transacción y el orden de los
argumentos se leyeron del bytecode. Es una medición, **no un cliente para el
producto**.

En esta imagen el proveedor de modelos `ai.pleos.gleoai.model.provider` v0.0.5
pesa 1 GB y trae dentro `llm_latest.tar` (326 MB) y los modelos de voz de
AIRSpeech (700 MB).

### LLM on-device: funciona, pero no sirve para narrar

- `registerCallback(cb, appId)` → `initialize(appId)` →
  `generateContentStream(appId, requestId, prompt)`, códigos 1, 3 y 5. El texto
  llega por `ILLMCallback.onResponse(cmd, requestId, data)` como
  `GenerateContentStreamPartialResponse`, trozo a trozo.
- Modelo: **`chatbaker-rlvr-0.5b-16k-d0.6.5-m0.4.0-r250704`**, un GGUF de
  **0,5B** parámetros.
- **Calidad inaceptable para nuestro uso.** Se le pidió en italiano describir el
  Colosseo en dos frases como guía turística, y respondió **en inglés**, con
  "dos páginas" repetidas y una nota diciendo que el contenido no refleja la
  realidad. En inglés se inventó datos: lo llama "Horse Temple" y lo atribuye a
  un "Emperor Diocles the Great".
- Velocidad: unos 250 tokens en 5,7 min. No es representativo, porque el
  emulador va con render por software y el host saturado, pero tampoco anima.

### TTS on-device: solo coreano

- `registerCallback(cb, appId)` → `setLocale(code, appId)` →
  `speak(text, isFinal, appId, AudioAttributesParcel)`, códigos 1, 6 y 4.
  Eventos por `ITtsCallback.onResponse(cmd, data)`: `UpdateTtsReady`,
  `UpdateTtsStart`, `UpdateRms`, `UpdateTtsDone` y `UpdateTtsError`.
- **`ko-KR` sintetiza**: unos 6 s de audio, con eventos `UpdateRms`.
- **`it-IT`, `en-US` y `es-ES` fallan**:
  `RuntimeError: Unsupported locale: it-IT` (y lo mismo con los otros dos).
- Las ocho voces del enum `SpeakerType` son coreanas (por defecto `haeun`).
- El idioma se guarda **por app** y `speak` es asíncrono: hay que esperar a
  `UpdateTtsDone` antes de cambiar de idioma, o todo sale con el último.

### STT on-device: modelos para ko, en, es y fr; sin italiano

No se probó con audio: el emulador va sin entrada de micrófono. Los modelos que
trae el proveedor son `connect/{ko,en,es,fr}` (modelo acústico y de lenguaje).
**No hay italiano.**

### Efecto secundario a tener en cuenta

Con el sistema en `es-ES`, el asistente `ai.umos.ambientai` entra en un bucle de
`initializeWakeUpWordDetector error Unsupported locale: es-ES` que inunda
logcat. La palabra de activación del sistema no funciona en español.

### Conclusión para Secret World

Para la demo en italiano, **nada de lo on-device de Pleos sustituye a lo nuestro**:
el TTS no habla italiano, el STT no lo reconoce y el LLM de 0,5B no sigue el
idioma ni es fiable con los datos. Gemma y Piper dentro del APK siguen siendo
la vía. Lo que queda por evaluar son las variantes **de servidor**, que necesitan
red y las credenciales de `registerApp`.

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
