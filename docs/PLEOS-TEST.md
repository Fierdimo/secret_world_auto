# Probar la versión Pleos en el emulador

Para quien tenga el emulador de Pleos **Connect‑W** (`POIP_EMULATOR_33_2685…‑x86_64`,
AAOS 14, caas 4.2.9), en un PC con Windows o Linux.

Esta versión no lleva voz ni IA propias: habla, escucha y piensa con los
servicios de la plataforma. El APK pesa ~60 MB (la demo con todo dentro, ~750 MB).

| Pieza | De dónde sale |
|---|---|
| Escuchar (STT) | `SpeechRecognizer` de Android, que en Pleos es `ai.umos.ambientai/.AmbRecognitionService` |
| Hablar (TTS) | La caas, `ITtsOnDeviceStream` |
| LLM | La caas, `IOnDeviceLLM` (solo para lo que la gramática no entiende) |
| Mapa | Mapbox (necesita internet en el emulador) |

> Los clientes de TTS y LLM están hechos a mano: Pleos no publica todavía sus
> librerías. El contrato se leyó de la caas 4.2.9. Ver
> [PLEOS-SDK-SURFACE.md](PLEOS-SDK-SURFACE.md).

## Instalar

```
adb install -r app-pleos-release.apk
adb shell pm grant com.secretworld.auto.pleos android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.secretworld.auto.pleos android.permission.RECORD_AUDIO
adb shell am start -n com.secretworld.auto.pleos/com.secretworld.auto.MainActivity
```

Los permisos `pleos.car.permission.TTS_SERVICE`, `STT_SERVICE` y `LLM_SERVICE`
se conceden solos al instalar. Para fijar una posición: en los controles del
emulador, *Location*, o `adb emu geo fix <longitud> <latitud>`.

## Qué comprobar

1. **Que abre y enseña el mapa** con los sitios de alrededor.
2. **Que habla**: tocar "escuchar" en un sitio, o pedirle algo. En la v2.1.5 el
   TTS on-device **solo hablaba coreano**; con es, it o en devolvía
   `UpdateTtsError`. Interesa saber si en Connect‑W pasa igual.
3. **Que escucha**: decir "Secret, ¿qué hay cerca?". El emulador necesita
   entrada de micrófono del PC.
4. **Que el LLM contesta** a algo que la gramática no entienda.

## Qué enviar

El registro de la app desde que se abre:

```
adb logcat -s SWPleos SWVoice SWVoiceInput SWNarration
```

Las líneas clave:

- `SWPleos: reconocedores: … · caas: 4.2.9`: qué reconocedor resuelve el sistema y la versión de la caas.
- `SWPleos: TTS <= UpdateTtsStart / UpdateTtsDone / UpdateTtsError …`: si la voz de Pleos llegó a hablar, y en qué idioma falla.
- `SWPleos: LLM de Pleos vinculado`: si el LLM se vinculó.
- `SWVoice: «…» → …`: lo que oyó y cómo lo entendió.

Y, si se cierra, el error:

```
adb logcat -d | grep -E "FATAL|ANR in|com.secretworld.auto.pleos"
```
