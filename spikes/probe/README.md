# Sonda de alcance — Pleos Connect

App mínima cuyo único trabajo es **medir qué ofrece la plataforma de verdad**,
contra el emulador, antes de decidir arquitectura. Responde con hechos a las
preguntas que hoy están abiertas en [../../docs/OPEN-QUESTIONS.md](../../docs/OPEN-QUESTIONS.md).

**No es el esqueleto del proyecto.** Es desechable: un módulo, un `Activity`, sin
Gradle, sin Kotlin, sin recursos. Se construye a mano con el JBR que trae Android
Studio más `aapt2`, `d8` y `apksigner` del SDK, porque en esta máquina todavía no
hay JDK ni Gradle instalados y montar eso no debe bloquear la medición.

## Uso

```bash
./build.sh
adb install -r build/probe.apk
adb shell am start -n com.secretworld.auto.probe/.ProbeActivity
adb logcat -s SWPROBE:I
```

El informe sale por pantalla y por `logcat` con el tag `SWPROBE`.

## Qué mide, y para qué spike

| Sección | Qué responde | Spike |
|---|---|---|
| Sistema, features, pantallas | Perfil real del dispositivo y resoluciones activas | — |
| Permisos concedidos | Cuáles de los `android.car.permission.*` se conceden sin pre-aprobación | 3 |
| Vehicle SDK | Si `Car.createCar` conecta y qué propiedades se leen: velocidad, marcha, freno de mano, encendido, batería, odómetro | 3 |
| Reconocimiento de voz | Si `SpeechRecognizer` está disponible, si hay reconocimiento en dispositivo, y quién atiende `RECOGNIZE_SPEECH` | 1 |
| Síntesis de voz | Si existe algún motor TTS registrado | 2 |
| Navegación | Si un `geo:` lo atiende alguien, y qué componentes exportan los servicios de Pleos | 4 |
| Audio y media | Si se concede foco de audio, si suena un tono, y qué `MediaBrowserService` hay | 5 |
| Ubicación | Qué providers existen y están habilitados | 3 |
| Paquetes | Inventario completo de `ai.pleos.*` y `ai.umos.*` | — |

## Límites

- Mide **una imagen `connect-lite` de emulador**, no un vehículo ni la imagen
  completa. Una ausencia aquí no prueba una ausencia en producto.
- No prueba **calidad**: que exista un `RecognitionService` no dice si admite
  vocabulario propio, y que exista un TTS no dice si suena bien. Eso son los
  spikes 1 y 2 de verdad, con audio real.
- Compila contra `android.car.jar` del platform del SDK, no contra los SDK de
  Pleos, que todavía no tenemos como librería cliente.
