# Resultados de la sonda — 2026‑09‑03

Ejecutada contra el emulador `Pleos_Connect_v2.1.5` (imagen arm64 `connect-lite`,
`RELEASE.Connect.v2.1.5`, AAOS 14 / API 34, fabricante 42dot). Salida cruda en
[informe-2026-09-03.txt](informe-2026-09-03.txt).

**Aviso**: esto mide una imagen de emulador, no un vehículo. Una ausencia aquí no
prueba una ausencia en producto. Lo que sí es firme es lo que **sí** está.

---

## Resumen ejecutivo

| Spike | Estado | Qué sabemos ahora |
|---|---|---|
| 3 · Vehicle SDK | **Verde** | Funciona con la API estándar de Android, sin SDK propietario |
| 1 · Reconocimiento de voz | **Verde para el nivel 1** | El `SpeechRecognizer` de AOSP está **vivo**: arranca el servicio y activa el micrófono. El `STT_SERVICE` de Pleos no está implementado todavía |
| 2 · TTS | **Ámbar** | Nada por la API de Android; el `TTS_SERVICE` de Pleos existe pero **devuelve binder nulo**. Para la v1, audio pre-generado |
| 4 · Navegación | **Ámbar** | El servicio **vincula y responde** (`ai.pleos.playground.navi.INaviService`). Solo falta la librería cliente |
| 5 · Audio | **Verde** | Foco concedido y reproducción PCM verificada de extremo a extremo |

**Cómo distinguir lo publicado de lo anunciado**: vincularse al servicio.
`NaviService` devuelve binder vivo; `SpeechService` y `LLMService` devuelven
`onNullBinding`. Detalle en
[../../docs/PLEOS-SDK-SURFACE.md](../../docs/PLEOS-SDK-SURFACE.md).

## 1. Vehicle SDK — resuelto en lo esencial

`Car.createCar()` conecta y expone **40 propiedades** con nuestros permisos.
Leídas de verdad:

| Propiedad | Valor | Permiso |
|---|---|---|
| `INFO_MAKE` / `INFO_MODEL` | `Hyundai 42dot` / `Connect` | `CAR_INFO`, automático |
| `PERF_VEHICLE_SPEED` | `0.0` | `CAR_SPEED`, runtime |
| `GEAR_SELECTION` | `4` | `CAR_POWERTRAIN`, automático |
| `PARKING_BRAKE_ON` | `false` | `CAR_POWERTRAIN`, automático |
| `IGNITION_STATE` | `4` | automático |
| `FUEL_LEVEL` | `15000.0` | `CAR_ENERGY`, runtime |
| `EV_BATTERY_LEVEL` | `77400.0` | `CAR_ENERGY`, runtime |
| `NIGHT_MODE` | `false` | automático |
| `PERF_ODOMETER` | **inaccesible** | `CAR_MILEAGE` |

Dos conclusiones que cambian el plan, ambas buenas:

1. **No hace falta un SDK propietario para el estado del vehículo.** Todo esto se
   compila contra `android.car.jar`, que viene en el platform del SDK de Android.
   `DrivingModeController` y el disparo por velocidad se pueden construir hoy.
2. **Hay un techo de permisos.** `CAR_MILEAGE` no es concedible:
   `"not a changeable permission type"`. Es permiso de sistema y exige app
   privilegiada o pre-aprobación. `CAR_SPEED` y `CAR_ENERGY` sí son de runtime y
   se conceden con consentimiento del usuario. Es decir: **velocidad sí,
   odómetro no**. Cualquier diseño que dependa de kilometraje acumulado hay que
   negociarlo con Pleos antes de escribirlo.

## 2. Reconocimiento de voz — la API está, falta la calidad

- `SpeechRecognizer.isRecognitionAvailable()` → **true**
- `isOnDeviceRecognitionAvailable()` → **false** ⇒ **depende de red**
- Servicio que lo atiende: `ai.umos.ambientai/.service.AmbRecognitionService`
- **Nadie atiende `ACTION_RECOGNIZE_SPEECH` ni `VOICE_SEARCH_HANDS_FREE`**

Lecturas:

- Se puede invocar `SpeechRecognizer` **programáticamente desde nuestra app**, sin
  pasar por el asistente. Esa era la mitad importante de la pregunta 1.
- No hay pantalla de dictado del sistema: la interacción la controlamos nosotros.
- Que no haya reconocimiento en dispositivo choca con RNF‑3 en túnel y zona sin
  cobertura. **Si la voz depende de red, no se puede prometer voz continua en
  carretera.** Hay que decidir si la voz es esencial o un extra que se degrada.

**Y está vivo, no es un stub.** Al llamar a `SpeechRecognizer.startListening()`
con `LANGUAGE_MODEL_FREE_FORM` y `es-ES`, el log del sistema muestra:

```
[GAA]AmbRecognitionService: onCreate
[GAA]AmbRecognitionService: onStartListening recognizerIntent Intent { act=android.speech.action.RECOGNIZE_SPEECH }
MoreStatusIcon: onMicActivated: true, ai.umos.ambientai
```

El servicio arrancó, recibió nuestro intent y **activó el micrófono**. No llegó
callback porque el emulador headless no tiene entrada de audio.

Esto importa para el calendario: el `STT_SERVICE` de Pleos figura como *coming
soon*, pero **el reconocimiento por la API estándar ya funciona**, así que el
nivel 1 puede tener voz sin esperar a Gleo.

Queda por medir: si admite vocabulario libre, con qué latencia y si funciona sin
red. Eso ya es el spike 1 de verdad, con micrófono.

## 3. TTS — no hay motor, ninguno. Confirmado a nivel de API

Cuatro pruebas independientes, todas negativas:

| Prueba | Resultado |
|---|---|
| `queryIntentServices(TTS_SERVICE)` | vacío |
| `CHECK_TTS_DATA` | nadie lo atiende |
| `settings get secure tts_default_synth` | `null` |
| **`TextToSpeech.getEngines()`** | **0 motores** |
| **`new TextToSpeech(...)` → `onInit`** | **`ERROR(-1)`** |

Las dos últimas son las que cuentan: no es que falte un registro en el
`PackageManager`, es que **el motor no existe**. Instanciar `TextToSpeech` falla.

**Y así fue**: la salvedad de que esto solo prueba la ausencia *por la API
estándar de Android* resultó ser la clave. Pleos **sí ofrece síntesis de voz**,
por su propia capa: `pleos.car.permission.TTS_SERVICE`, protección `normal`,
servido por `ai.pleos.playground.caas`. Ver
[../../docs/PLEOS-SDK-SURFACE.md](../../docs/PLEOS-SDK-SURFACE.md).

Lección para el resto de la sonda: **medir por AOSP produce falsos negativos en
esta plataforma.** Lo que no aparece por la API estándar hay que buscarlo en el
espacio `pleos.car.permission.*`.

Alternativas evaluadas en [../../docs/VOICE-OPTIONS.md](../../docs/VOICE-OPTIONS.md).

## 4. Navegación — aquí está el bloqueante real

- `geo:37.5665,126.9780` → **nadie lo atiende**
- `google.navigation:` → **nadie lo atiende**
- `CATEGORY_APP_MAPS` → `ai.umos.maps.android.navigation.app`

No existe el traspaso estándar de destino de Android, pero **sí el suyo**:
`pleos.car.permission.NAVI_ROUTE_SEARCH` (protección `normal`) expone
`requestRoute` para fijar destino y `startRouteGuiding` para iniciar el guiado,
sin restricción de llamada durante la conducción. Además `NAVI_ROUTE` da lectura
de ruta activa y **`getTBTInfo`**, la maniobra que viene — muy útil para callar
antes de un cruce en vez de narrar encima.

El camino es el servicio exportado `ai.pleos.playground.service.navi/.NaviService`
(v2.0.1), cuya acción `...intent.action.NAVI_START` resuelve en el emulador. Lo
que falta es **la librería cliente**: se puede vincular, pero el protocolo AIDL no
es público. Ver [../../docs/PLEOS-SDK-SURFACE.md](../../docs/PLEOS-SDK-SURFACE.md).

## 5. Audio

- Volumen MUSIC 15/15, `requestAudioFocus(USAGE_MEDIA, CONTENT_TYPE_SPEECH)` →
  **concedido**
- `MediaBrowserService` del sistema: `com.android.bluetooth`,
  `com.android.car.radio`, `com.android.music` ⇒ **stack de media AAOS estándar**,
  así que nuestro `MediaBrowserService` encaja sin nada propietario.
- **El audio fluye de verdad, verificado.** Un `AudioTrack` con 1 s de PCM
  generado: 44100 de 44100 muestras escritas, `getPlayState() = PLAYING` y el
  cabezal de reproducción avanzó a **44100 frames** a los 1,5 s. No es que el
  API acepte la llamada: es que el audio se consumió. El camino de reproducción
  sobre el que se apoya todo el producto está validado.

## 6. Ubicación

Providers `passive`, `fused` y `gps`, los tres habilitados. RF‑2.1 —tomar la
posición del vehículo y no del móvil— es viable con la API estándar.

## 7. Detalles operativos que cuestan una tarde si no se saben

- **El perfil de conductor es el usuario 10**, no el 0. Hay que instalar y lanzar
  con `--user 10`, o la app existe pero "no existe" al arrancarla.
- El idioma es **por usuario**: `persist.sys.locale` no basta, hace falta
  `settings put --user 10 system system_locales es-ES` y reiniciar.
- Tras reiniciar el framework, el car service tarda en estar listo: si se sondea
  demasiado pronto, `Car.createCar()` devuelve `null`.
- Los servicios de Pleos van instrumentados con **Datadog RUM** y usan
  androidx Work y Room.
