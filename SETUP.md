# SETUP — puesta en marcha

Estado a **2026‑09‑03**: cuenta de desarrollador concedida, Android Studio
instalado y **emulador arm64 funcionando**. Arranca en ~30 s.

La imagen y las guías están en `emulator/` — 17 GB, no versionar.

## Desde un clon limpio

El repositorio no lleva binarios grandes. Tras clonar hacen falta dos cosas:

1. **La librería de síntesis y las voces** (~100 MB), que descarga:

   ```sh
   tools/fetch-tts-assets.sh
   ```

   Deja `libs/sherpa-onnx.aar` y las voces en su sitio. Sin esto, `:feature:voice`
   no compila.

2. **El token de descarga de Mapbox**, solo para el sabor `bench` —en el vehículo
   se usa el mapa del dispositivo y Mapbox no se enlaza—. Es un token `sk.` de
   lectura de descargas, y va en `~/.gradle/gradle.properties`, **nunca en el
   repositorio**:

   ```properties
   MAPBOX_DOWNLOADS_TOKEN=sk....
   ```

   También vale la variable de entorno del mismo nombre. Ver
   [docs/MAP-OPTIONS.md](docs/MAP-OPTIONS.md).

Con eso: `./gradlew check` y `./gradlew :app:assembleBenchDebug`.

3. **Los modelos de escucha y el LLM**, solo para probar la voz en el
   dispositivo. No hacen falta para compilar: sin ellos la app usa el
   reconocedor del sistema y la gramática sola, y lo dice en el `logcat`.

   ```sh
   tools/fetch-voice-models.sh      # KWS + VAD + Whisper tiny → dist/models/*.zip
   tools/sideload-models.sh         # los copia con adb al dispositivo conectado
   ```

   El LLM (Gemma 3 1B, 555 MB) lo baja el mismo script de una copia sin puerta
   de Hugging Face y lo comprueba contra el `sha256` del fichero oficial de
   `litert-community/Gemma3-1B-IT`, que está detrás de un formulario. Es el
   mismo fichero byte a byte; la licencia (Gemma Terms of Use) aplica igual.

   **Esto es la comodidad de desarrollo de SPEC RF‑1.8.9**: la app de tienda
   descarga y verifica todo esto en la primera ejecución; lo cargado a mano va
   sin verificar al directorio externo de la app y solo lo busca ahí porque
   quien lo puso es quien prueba. Para ver qué encontró:
   `adb logcat -s SWVoice SWVoiceInput SWVoiceLlm`.

La imagen del emulador de Pleos se entrega aparte y va en `emulator/`, que está
ignorado.

---

## 1. Entorno

- [x] Android Studio 2026.1.3, SDK en `~/Library/Android/sdk`, emulador 37.1.11.0.
- [x] **API 28 mínimo** — instalados `platforms/android-37.0` y build-tools 36.0.0.
- [x] **Emulador de Pleos Connect arm64 instalado y arrancando**: `crp_avd_out_arm64-v8a`, AAOS **14 (API 34)**, ABI **arm64-v8a**, tag `android-automotive`, vendor **42dot**.
- [x] **JDK 21** (`brew install openjdk@21`) y **Gradle 9.7.1**. El JDK es *keg-only*: hay que exportar `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- [x] **`cmdline-tools` del SDK** instaladas, y con ellas `platforms;android-34` y `android-36`. El `android-37.0` preinstalado es Android 17 / API 37: demasiado nuevo para lo que corre el vehículo.
- [ ] Configurar idioma: el emulador arranca en **ko‑KR**.

> La primera entrega fue una imagen **x86_64**, inservible en Apple Silicon: el
> emulador de macOS ARM solo trae los backends `qemu-system-aarch64` y
> `qemu-system-armel`. Se resolvió pidiendo la build arm64. Si alguien recibe
> solo el paquete x86_64, esa es la petición que hay que hacer.

### Cómo está instalado

La imagen vive en el repositorio y se enlaza al SDK, en vez de copiar 17 GB:

```bash
SDK=~/Library/Android/sdk
mkdir -p "$SDK/system-images/android-34/pleos_car_emulator"
ln -sfn "$PWD/emulator/crp_avd_out_arm64-v8a" \
        "$SDK/system-images/android-34/pleos_car_emulator/arm64-v8a"

cp emulator/crp_avd_out_arm64-v8a/devices.xml ~/.android/devices.xml

mkdir -p ~/.android/avd/Pleos_Connect_v2.1.5.avd
cp emulator/config.ini ~/.android/avd/Pleos_Connect_v2.1.5.avd/config.ini
cat > ~/.android/avd/Pleos_Connect_v2.1.5.ini <<EOF
avd.ini.encoding=UTF-8
path=$HOME/.android/avd/Pleos_Connect_v2.1.5.avd
path.rel=avd/Pleos_Connect_v2.1.5.avd
target=android-34
EOF
```

La ruta del enlace no es arbitraria: es exactamente lo que declara
`image.sysdir.1` en el `config.ini` del fabricante, que es relativo a la raíz del
SDK.

Arrancar:

```bash
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.5
```

El `config.ini` del fabricante ya trae `fastboot.forceColdBoot = yes`, así que el
VHAL arranca bien sin pasar `-no-snapshot-load`. **Queda zanjada** la duda de cold
boot que arrastraba este documento.

### Aviso de rendimiento

La máquina tiene **8 GB de RAM**. El emulador sube la RAM del invitado a 4096 MB
y pide 5 GB libres para renderizado por hardware; como no los hay, cae a
**software GL** (`lavapipe` / `swangle`) y avisa de ello. Arranca igual, pero la
UI va lenta. Si molesta: cerrar todo lo demás, o bajar el perfil de pantalla de
Connect‑W (3840×1440) a Connect‑L (1920×1080) en `hw.lcd.width/height`.

### Lo que ya se ve en el emulador

Inventario de paquetes del sistema, que adelanta trabajo de los spikes:

| Paquete | Qué es | Para qué spike |
|---|---|---|
| `ai.pleos.playground.service.vehicle` | VehicleService — backend del Vehicle SDK | 3 |
| `ai.pleos.playground.service.navi` | NaviService — backend de NaviHelper | 4 |
| `ai.pleos.gleoai.model.provider` | `LLMModelProviderSerivce` — Gleo AI es un proveedor de modelo LLM | 1 |
| `ai.umos.ambientai` | Asistente y **`RecognitionService`** del sistema | 1 |
| `ai.pleos.playground.account` | Pleos Account | RF‑4, vinculación |
| `ai.umos.maps.android.navigation.app` | App de navegación nativa | Mapas |
| `ai.umos.mediaapp` | Centro multimedia del vehículo | 5 |
| `ai.umos.appmarket` | App Market | Publicación |
| `ai.umos.driverprofilemanager` | Gestor de perfiles de conductor | Riesgo de perfil compartido |
| `ai.umos.android.android.auto` | Android Auto, presente en la imagen | Estrategia de plataformas |

Dos lecturas que cambian cosas, ambas **a confirmar** porque salen de una imagen
*connect-lite* y no de una política escrita:

1. **Hay `RecognitionService` del sistema.** `voice_recognition_service` apunta a
   `ai.umos.ambientai/.service.AmbRecognitionService` y `assistant` a
   `.service.AiAssistantService`. Es decir, la API estándar `SpeechRecognizer` de
   Android existe y la sirve su motor. Eso no responde todavía si podemos usar
   vocabulario propio —que es la pregunta real del spike 1— pero sí que la
   superficie de API está.
2. **No hay ningún motor TTS registrado.** `pm query-services -a
   android.intent.action.TTS_SERVICE` no devuelve nada y `tts_default_synth` es
   `null`. Si se confirma en la imagen completa, el spike 2 se cierra solo y el
   audio hay que **pre-generarlo nosotros**, como decía el plan original.

Pantalla activa: una sola, **3840×1440 a 160 dpi** (perfil Connect‑W). El
`config.ini` declara además un `hw.display6` de 400×600, que no aparece como
viewport activo — probablemente el cluster.

### Perfiles de pantalla

De la guía de HMG para la imagen x86_64, aplicables a `hw.lcd.width/height` y
`skin.name`:

| Perfil | Resolución |
|---|---|
| Connect-W | 3840 × 1440 |
| Connect-S | 2560 × 1440 |
| Connect-L | 1920 × 1080 |

La guía los nombra **Connect-W / S / L** y [SPEC.md](SPEC.md) habla de
**Connect-S / W / WF**. Aclarar la correspondencia antes de dimensionar layouts.

### La pantalla está bloqueada hasta registrar un CRN

El emulador muestra, por encima de todo, un aviso de `ai.umos.setupwizard`: *"This
target board does not have a registered CRN"*. Hasta registrar el CRN en Pleos
Playground, la pantalla no es usable. Para desarrollar, basta con apartarlo:

```bash
adb shell pm disable-user --user 10 ai.umos.setupwizard
```

Conviene registrarlo de verdad antes de fiarse de cualquier prueba visual.

### Detalles operativos que cuestan una tarde

- **El perfil de conductor es el usuario 10**, no el 0: `adb install --user 10` y
  `adb shell am start --user 10 ...`, o la app se instala pero al lanzarla dice
  que no existe.
- El **idioma es por usuario**. La imagen viene en coreano; para ponerla en
  español no basta `persist.sys.locale`:

  ```bash
  adb root
  adb shell setprop persist.sys.locale es-ES
  adb shell settings put --user 10 system system_locales es-ES
  adb reboot
  ```

- Tras reiniciar el framework, el car service tarda: si se consulta demasiado
  pronto, `Car.createCar()` devuelve `null`.
- **El render va por software y tarda 1,5–2,7 s por frame.** Una captura tomada
  demasiado pronto sale en blanco aunque la UI esté bien compuesta. Para
  verificar la interfaz sin depender del pintado, el árbol de accesibilidad es
  fiable e instantáneo: `adb shell uiautomator dump /sdcard/ui.xml`.
- **Compose exige `compileSdk` 37 y el plugin `org.jetbrains.kotlin.plugin.compose`.**
  Que AGP 9 traiga Kotlin integrado no incluye el compilador de Compose.

### La sonda de alcance

`spikes/probe/` contiene una app mínima que mide qué ofrece la plataforma y
responde a buena parte de los spikes. Resultados de la primera pasada en
[spikes/probe/RESULTS.md](spikes/probe/RESULTS.md).

### Si no hay red o sale el aviso de Bluelink

De `emulator/Emulator_Guide_Bluelink_Issues.txt`:

```bash
adb root
adb shell cmd wifi set-wifi-enabled enabled
adb shell cmd wifi connect-network AndroidWifi open
```

## 2. Esqueleto del proyecto — **hecho**

Construido el 2026‑09‑03: `./gradlew check` en verde, APK instalada y arrancando
en el emulador.

**Versiones**: AGP 9.4.0, Kotlin 2.4.0, Gradle 9.7.1, JDK 21, `compileSdk` 36,
`minSdk` 28, `targetSdk` 34.

Tres cosas que costaron y conviene no volver a descubrir:

- **AGP 9.5.0 no existe como release**, solo alphas. La última estable es 9.4.0.
- **AGP 9 trae Kotlin integrado**: aplicar `org.jetbrains.kotlin.android` en un
  módulo Android **falla con error explícito**. Solo `:core:domain` lleva plugin
  de Kotlin, y es el de JVM porque es Kotlin puro.
- **`targetSdk` 34 es deliberado**: es la API de Pleos Connect. El aviso
  `OldTargetApi` de lint queda desactivado con esa razón escrita en el build.

El orden importó: `:core:domain` nació aislado y se queda aislado.

```
:app
:core:domain        ← Kotlin puro. CERO dependencias de Pleos y de Android
:core:data          ← Retrofit + Room + DataStore
:core:platform      ← única capa que importa los SDK de Pleos
:core:designsystem
:feature:voice
:feature:discover
:feature:parked
```

- [x] `:core:domain` como librería **Kotlin pura**, sin plugin de Android. Es la
      forma más barata de garantizar que nadie meta una dependencia de Pleos por
      descuido: no compilaría.
- [x] **Verificación de pureza en el build**: la tarea `checkDomainPurity` cuelga
      de `check` y falla, con mensaje legible, si algún fuente del dominio importa
      `android.*`, `androidx.*` o `ai.pleos`/`pleos.*`.
- [x] Primer dominio real con tests: `TourQueue` y `NarrationTrigger`, **12 tests
      en verde**, ejecutables sin dispositivo.
- [ ] Dobles de prueba de Vehicle SDK y Fused Location en `:core:platform`, para
      poder desarrollar sin coche y sin emulador.

## 3. Los cinco spikes

Cada uno es de días, no de semanas. Juntos eliminan el riesgo del producto
entero. Se hacen en este orden porque los dos primeros pueden cambiar el diseño.

### Spike 1 · SpeechToText y el asistente
**La pregunta más importante del proyecto.** ¿Puede nuestra app usar el
SpeechToText SDK directamente, o la entrada de voz tiene que pasar por su
asistente Gleo?

Si es lo segundo, nuestro vocabulario queda limitado a las intenciones que ellos
expongan, y el acompañante conversacional cambia de forma. Todo lo demás depende
de esta respuesta.

- [ ] Reconocimiento invocado por la app, sin asistente de por medio.
- [ ] Latencia medida de extremo a extremo (objetivo RNF-3: < 1,5 s).
- [ ] Comportamiento con ruido de carretera si el emulador lo permite simular.

### Spike 2 · TextToSpeech
Decide si usamos la voz del vehículo o generamos audio nosotros.

- [ ] Idiomas soportados, contra los 17 del catálogo.
- [ ] Calidad subjetiva en un texto real de 90 segundos. El listón: ¿aguantarías
      cuatro minutos de esto en un viaje?
- [ ] Funcionamiento sin red.
- [ ] Control de reproducción: pausar, reanudar, cortar a mitad.

### Spike 3 · Fused Location y Vehicle SDK
- [ ] Posición, rumbo y velocidad, con frecuencia y precisión reales.
- [ ] Estados `Parked` y `Driving` fiables.
- [ ] Comportamiento en el arranque en frío: ¿cuánto tarda en haber posición?

### Spike 4 · NaviHelper
- [ ] Fijar un destino arbitrario por coordenada y que el navegador nativo lo acepte.
- [ ] Leer la ruta activa y su polilínea, si es posible.
- [ ] Qué ocurre si ya hay una ruta en curso.

### Spike 5 · Audio en segundo plano y pantalla partida
- [ ] ¿Sigue sonando nuestra narración si el usuario abre otra app?
- [ ] Comportamiento en la ventana de dos apps simultáneas.
- [ ] Integración con el centro multimedia y los mandos del volante.

## 4. Lo que hay que preguntar a Pleos, en paralelo

No requiere código y bloquea decisiones de producto. Vía Console o
`partnership@pleos.ai`.

- [ ] **¿Permite su política un desbloqueo de contenido comprado fuera del coche?**
      Decide todo el modelo de acceso (ver [docs/MONETIZATION.md](docs/MONETIZATION.md)).
- [ ] Revenue share, países y monedas de su pasarela.
- [ ] Proceso y plazos de pre-aprobación de permisos.
- [ ] Sus reglas concretas de distracción del conductor.
- [ ] Si Pleos Account puede usarse para vincular con nuestra cuenta sin teclear.
- [ ] Guías del design system y componentes disponibles.

## 5. Primer entregable

Una app que, en el emulador con posición simulada en movimiento:

1. Obtiene posición y velocidad del vehículo.
2. Pide al backend los lugares cercanos por delante.
3. Narra uno de ellos en el momento correcto.
4. Acepta "cuéntame más", "llévame ahí" y "sáltalo".
5. Pasa el destino al navegador nativo.

Es el MVP acordado con Antonio. Nada más entra hasta que esto funcione.

## 6. Contenido, en paralelo desde el día uno

No es trabajo de desarrollo, pero es la dependencia más larga del proyecto.

- [ ] Definir el formato editorial: 60 a 90 s, tono conversacional, escrito para oír.
- [ ] Escribir o generar un piloto de ~200 lugares en 3 idiomas.
- [ ] Validar con gente real en un coche antes de generar a escala.
- [ ] Medir la densidad del catálogo sobre carretera real, para saber si el
      acompañamiento continuo es prometible.
