# Dónde ejecutar el emulador

Escrito el 2026‑09‑04, después de que el Mac de desarrollo se quedara corto.

## El problema, medido

La máquina actual es un **Apple Silicon de 8 GB y 6 núcleos**. El emulador de
Pleos reserva **4 GB de invitado** —lo impone la definición de dispositivo del
fabricante, y el flag `-memory` no lo baja—, así que la mitad de la RAM del host
se va en el coche virtual antes de abrir nada más.

Las consecuencias no son teóricas:

| Síntoma | Evidencia |
|---|---|
| Render por software | `Software GL rendering will be used due to system memory pressure (Available Memory: 1070 MB, Required: 5120 MB)` |
| Frames de 1,5–2,7 s | `Davey! duration=2691ms` |
| Síntesis 7,5× más lenta | RTF 0,22 con holgura → **1,64** con el host saturado |
| El sistema del invitado se ahoga | ANRs en `system_server`; la carga del modelo dejó de completarse |
| Sin audio | `coreaudio: kAudioHardwareIllegalOperationError` |

El patrón que más daño hace: **compilar y emular a la vez**. El demonio de Gradle
y el emulador se pelean por la misma RAM.

## Lo que no hace falta pedir otra vez

**Ya tenemos las dos arquitecturas de la imagen.** Además de la arm64 que se usa
en el Mac, sigue en `emulator/` el paquete original
`HMGCP14_260423_cp_emulator_x86_64_connect-lite.tar.gz` (2,5 GB). Eso abre la
puerta a cualquier host **x86_64 con KVM**, que es lo barato y lo abundante.

## Opciones, de más a menos recomendable

### 1. Servidor dedicado x86_64 con KVM — la mejor relación coste/dolor

Un dedicado barato (Hetzner y similares, en el entorno de 40–60 €/mes) da 32–64 GB
y virtualización real sin trucos. Se instala el SDK de Android, la imagen
**x86_64**, y se expone por red:

```bash
emulator -avd cp_car -no-window -gpu swiftshader_indirect &
adb -a -P 5037 nodaemon server        # o: adb tcpip / socat
```

Desde el Mac se conecta con `adb connect <host>:5555` y **Android Studio lo trata
como un dispositivo local**: mismo flujo de trabajo, sin cambiar nada del
proyecto. Es la opción que yo elegiría para uso continuado.

### 2. VM en la nube con virtualización anidada — si el uso es a ratos

Google Cloud permite **nested virtualization** en máquinas Intel/AMD, así que hay
KVM de verdad. Azure lo permite en varias series. Se paga por hora, lo que encaja
si el emulador solo se enciende para probar.

Requisito imprescindible: **que la VM exponga KVM**. Sin él el emulador cae a
emulación por software y estamos peor que en el Mac.

### 3. Bare metal en la nube — potente y caro

Instancias `*.metal` de AWS y equivalentes. Funcionan, pero el precio por hora
sólo compensa para ráfagas cortas. Las instancias Mac de AWS además facturan un
mínimo de 24 horas.

### 4. Otro Mac con 16–32 GB — lo más simple si hay uno a mano

Con la imagen **arm64** que ya está instalada, sin montar nada remoto. Si se puede
pedir prestado un Mac con más memoria, es la vía de menor fricción.

### Lo que **no** sirve

**Las granjas de dispositivos** —Firebase Test Lab, BrowserStack, Sauce Labs,
Genymotion Cloud— no admiten imágenes de sistema propias. La de Pleos es una
imagen de fabricante: hay que ejecutarla en una máquina nuestra.

## ¿Y meter la imagen del emulador en un móvil? No

La imagen de Pleos está compilada para la **placa virtual de QEMU**:
`ro.product.board = goldfish_arm64`, `kernel-ranchu`, drivers virtio para GPU,
sonido y sensores, y un VHAL que habla con el emulador. Un dispositivo real tiene
otro SoC, otro bootloader y otro particionado. No arrancaría, y si arrancara no
tendría drivers. **No es una vía.**

## Lo que sí funciona en una tablet o un móvil: nuestra app

Lo que hacía imposible instalarla en un dispositivo corriente era una sola línea:
`<uses-feature android:name="android.hardware.type.automotive" android:required="true" />`.

Ahora hay **dos sabores de compilación**:

| Sabor | Para qué |
|---|---|
| `vehicle` | El producto. Exige hardware de automoción, como debe ser para el App Market |
| `bench` | El mismo código, instalable en cualquier Android. Trae la voz inglesa dentro del APK, así que basta instalar y pulsar |

```bash
./gradlew :app:assembleBenchDebug
adb install app/build/outputs/apk/bench/debug/app-bench-debug.apk
```

**Qué se puede validar en una tablet**: la voz —con altavoces de verdad—, el
`TourQueue`, el disparo por anticipación, la síntesis por delante, la caché, la
reproducción con foco de audio, y el **rendimiento sobre ARM real**, que es la
medida que falta y que un emulador sobre un portátil no puede dar.

**Qué no**: Vehicle SDK, NaviHelper y los servicios de Pleos. Pero eso ya estaba
detrás de dobles —el vehículo es simulado y se controla con los botones de
abajo—, así que el nivel 1 del producto funciona igual.

**Aviso de diseño**: la escala tipográfica está calibrada para una pantalla de
coche a 160 dpi, donde 1 dp = 1 px. En un móvil de 400 dpi se verá
desproporcionadamente grande. No es un fallo del banco de pruebas: es la prueba
de que esos tamaños son para el coche.

## Un matiz importante: el audio no depende del emulador

En un host Linux headless tampoco habrá altavoces, así que no hay que elegir
servidor pensando en oír la voz. **La validación de la voz no necesita el
emulador**: la app escribe la narración en WAV y ese fichero se descarga y se
escucha en cualquier sitio.

```bash
adb pull /data/media/10/Android/data/com.secretworld.auto/files/narracion-en.wav
```

Lo que sí necesita hardware real es el **rendimiento**: el RTF medido en un
emulador sobre un portátil no dice nada del coche. Esa medida sigue pendiente.

## Mientras tanto, en el Mac actual

Reglas que hacen el día usable, aunque no cómodo:

- **Nunca compilar con el emulador encendido.** `./gradlew --stop` antes de arrancarlo.
- Arrancar siempre `-no-window`: la ventana añade trabajo gráfico que aquí no sobra.
- Usar el perfil **Connect‑L** (1920×1080) en vez de Connect‑W (3840×1440) para
  el trabajo diario; el perfil ancho, solo para revisar layout.
- Reiniciar el emulador en frío en cuanto la síntesis deje de completarse: es la
  señal de que el invitado se ha degradado.

## Qué se puede desarrollar con el emulador

Revisado el 2026‑11‑03, después de probar contra el emulador la sonda con
llamadas reales a los servicios de Pleos. El detalle técnico está en
[PLEOS-SDK-SURFACE.md](PLEOS-SDK-SURFACE.md); aquí solo va **qué se puede hacer,
dónde y con qué coste**.

### Imágenes que tenemos

| Imagen | Arquitectura | ¿Arranca en este Mac? | Qué aporta |
|---|---|---|---|
| `Pleos_Connect_v2.1.5` (perfil W), **instalada** | arm64 | **Sí**, lenta | La de trabajo. caas 2.2.7.1, con el proveedor de modelos on-device |
| `crp-v2.1.5-arm64-s` | arm64 | Sí | Lo mismo con el perfil Connect‑S (2560×1440). Solo sirve para revisar layout |
| `crp-v2.1.5-x86_64-w` / `-s` | x86_64 | No | Lo mismo que las arm64, para un PC Intel/AMD |
| `POIP_EMULATOR_33_2685…260810` (Connect‑W, KOR, agosto 2026) | x86_64 | **No** | caas 4.2.9 y NaviHelper dentro. **Sin proveedor de modelos** |
| `HMGCP14_260423…connect-lite` (en `emulator/`) | x86_64 | No | Imagen antigua; no aporta nada frente a la v2.1.5 |

El emulador de Android en Apple Silicon **solo arranca imágenes arm64**. Las
x86_64 necesitan un PC Intel/AMD con virtualización por hardware (KVM en Linux;
WHPX o AEHD en Windows).

### En este Mac (Apple A18 Pro, 8 GB), con la v2.1.5

| Tarea | ¿Se puede? | Notas |
|---|---|---|
| Instalar el sabor `vehicle` y comprobar que arranca | **Sí** | `--user 10`, siempre |
| Estado del vehículo (`android.car`): velocidad, marcha, batería | **Sí** | 40 propiedades; el odómetro no |
| Foco de audio y reproducción | **Sí** | Verificado con `AudioTrack` |
| Ubicación (`fused`, `gps`) | **Sí** | |
| Vincularse a los servicios de Pleos (navegación, STT, TTS, LLM) | **Sí** | Con las acciones correctas; ver PLEOS-SDK-SURFACE |
| LLM on-device de Pleos | Sí, pero **no sirve** | 0,5B: no sigue el italiano y se inventa datos. Unos 250 tokens en 5,7 min |
| TTS on-device de Pleos | **Solo coreano** | it, en y es devuelven `Unsupported locale` |
| STT con micrófono | No | El emulador va sin entrada de audio |
| Revisar layout a 3840×1440 | Sí, a duras penas | Render por software; mejor capturas que la ventana |
| Medir rendimiento (RTF de la voz, latencias) | **No** | Un emulador sobre un portátil saturado no mide nada útil |
| Compilar con el emulador encendido | **No** | Se pelean por la RAM; `./gradlew --stop` antes |
| Trabajo diario de UI y lógica | **No** | Demasiado lento para iterar |

Conclusión práctica: aquí el emulador sirve para **sesiones cortas de
verificación**, no para desarrollar sobre él.

### Lo que necesita otro equipo

- **La imagen de agosto (caas 4.2.9 y NaviHelper)**: un PC x86_64. Para nuestro
  producto aporta poco: no trae modelos on-device, y lo de servidor necesita
  credenciales de Pleos igualmente.
- **Trabajar a diario contra el emulador**: cualquier máquina con 16 GB o más.
  Un PC Windows Intel/AMD sirve con las imágenes x86_64 de la v2.1.5. Si es un
  Mac Apple Silicon de 16 GB o más, sirve la arm64 que ya tenemos.
- **Medir rendimiento**: ni emulador ni portátil. Hace falta una tablet ARM real
  (con el sabor `bench`) o, mejor, el coche.

### Cómo trabajar mientras tanto

1. **Desarrollar sobre el sabor `bench`** en un móvil o tablet Android. La voz, el
   `TourQueue`, la narración, la caché y la UI se validan ahí, más rápido y con
   altavoces de verdad. Ver [§ Lo que sí funciona en una tablet](#lo-que-sí-funciona-en-una-tablet-o-un-móvil-nuestra-app).
2. **Ir al emulador solo para lo que es del coche**: que el APK `vehicle`
   instala y arranca, que lee el vehículo, que el audio convive con el sistema y
   que el layout aguanta el perfil ancho. Juntar esas comprobaciones en una sola
   sesión.
3. **Usar la sonda como prueba de humo** de la plataforma
   ([../spikes/probe/](../spikes/probe/)): un comando y unos minutos.

Una sesión de emulador típica:

```bash
./gradlew --stop                                   # nunca compilar a la vez
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.5 -no-window -no-audio &
adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 5; done
adb install -r --user 10 app/build/outputs/apk/vehicle/debug/app-vehicle-debug.apk
adb shell am start -S --user 10 -n com.secretworld.auto/.MainActivity
adb logcat | grep --line-buffered -aE 'SWPROBE|secretworld'   # filtrar en vivo
adb emu kill
```

Dos trampas del log:

- **Con el sistema en `es-ES`, logcat se inunda**: el asistente del coche repite
  `initializeWakeUpWordDetector error Unsupported locale: es-ES` sin parar, y el
  búfer de 2 MB se recicla en segundos. Hay que filtrar en vivo, no con
  `logcat -d` a posteriori.
- La caas escribe texto en coreano: para que `grep` no se salte líneas, usar
  `grep -a` (o `LC_ALL=C`).

### Lo que desbloquearía más

| Qué | A quién | Para qué |
|---|---|---|
| Un equipo con 16 GB o más (PC Intel/AMD o Mac) | Interno | Usar el emulador a diario |
| Credenciales de `registerApp` (`clientId`, `clientSecret`) | Pleos | Probar STT, TTS y LLM de servidor, y ver si tienen italiano |
| Librerías NaviHelper ≥ 2.0.2, `sdk.tts` y `sdk.stt` ≥ 2.1.5 | Pleos | Integrar navegación sin un cliente reconstruido |
| La imagen de agosto en arm64 | Pleos | Probarla en este Mac |
