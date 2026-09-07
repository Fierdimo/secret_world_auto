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
