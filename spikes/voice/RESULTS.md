# Spike 2 — TTS embebido · resultados

Ejecutado el 2026‑09‑03 sobre el emulador `Pleos_Connect_v2.1.5` (arm64, AAOS 14),
que corre en un Mac de 8 GB con render por software. **Un SoC de vehículo real
debería ir igual o mejor, pero eso es un supuesto, no una medida.**

Montaje: **sherpa-onnx 1.13.7** (Apache‑2.0) con voces **Piper VITS int8**, dos
hilos, `provider = cpu`. Texto real de narración —el mismo sobre la Via Appia en
los dos idiomas— en el formato de SPEC RF‑1.5.

## Rendimiento: holgado

| | Inglés (ljspeech-medium-int8) | Italiano (paola-medium-int8) |
|---|---|---|
| Palabras | 218 | 209 |
| Audio generado | 68,8 s | 60,1 s |
| Síntesis, motor frío | 18,70 s | 14,08 s |
| Carga del modelo + síntesis | 21,92 s | 15,65 s |
| **Factor de tiempo real (frío)** | **0,272** | **0,234** |
| **Factor de tiempo real (caliente)** | **0,199** | **0,207** |
| Memoria de heap | 2 → 8 MB | 2 → 8 MB |
| WAV resultante | 2,9 MB | 2,5 MB |

**RNF‑7b pedía menos de 0,5 y estamos en 0,20.** Con el motor caliente se sintetiza
un minuto de narración en unos 13 segundos: sobra margen para ir por delante,
que es exactamente la estrategia de RF‑1.2. La carga del modelo cuesta 1,5–3 s y
se paga una vez por idioma, no por narración.

La memoria de heap apenas se mueve porque el trabajo ocurre en nativo; el consumo
real hay que medirlo con `Debug.getNativeHeapAllocatedSize` o `dumpsys meminfo`
antes de dar el número por bueno.

## Calidad: la juzga una persona

Los ficheros están en este directorio y se enviaron para escuchar. El listón de
[SPEC.md §9](../../SPEC.md) no es medible por software: *¿aguantarías cuatro
minutos de esto conduciendo?*

## Licencias: **aquí está el problema**

La diligencia de la pregunta 2d resultó no ser un trámite. Las voces que mejor
suenan son precisamente las que no se pueden usar.

| Voz | Dataset | Licencia | ¿Se puede publicar? |
|---|---|---|---|
| `en_US-lessac-high` | Blizzard 2013 / Lessac Technologies | Licencia nominal, concedida a mano por persona u organización | **No** |
| `en_US-ryan-high` | RyanSpeech | CC BY‑**NC**‑SA 4.0 | **No**, prohíbe uso comercial |
| **`en_US-ljspeech-medium`** | LJ Speech | **Dominio público** | **Sí** |
| `it_IT-paola-medium` | Dataset de HuggingFace sin licencia declarada, y **afinada sobre lessac** | Sin resolver, y hereda el problema de Blizzard | **No, hasta aclararlo** |
| `it_IT-riccardo-x_low` | M‑AILABS | Sin verificar; la web del dataset no respondió | Por confirmar |

**El inglés está resuelto**: `ljspeech` es de dominio público y es la voz con la
que se midió arriba. **El italiano no.** Ninguna voz italiana de Piper tiene hoy
una licencia verificada y compatible con un producto comercial.

Opciones para el italiano, por orden:

1. Verificar la licencia de M‑AILABS y, si es permisiva, usar `riccardo`. Pesa
   solo 12,7 MB en int8, pero es `x_low` a 16 kHz: hay que escucharlo antes.
2. Buscar un dataset italiano CC0 o CC‑BY y entrenar o afinar una voz propia.
3. Licenciar una voz comercial.
4. Lanzar la v1 **solo en inglés** y añadir italiano cuando la licencia esté
   cerrada.

## Tamaño del APK: cuatro medidas que cuentan la historia

| Configuración | APK |
|---|---|
| Voces empaquetadas + las cuatro ABIs | **169 MB** |
| Voces fuera, cuatro ABIs | 124 MB |
| **Voces fuera, solo `arm64-v8a`** | **34 MB** |
| Solo la app, sin motor de voz | 3,9 MB |

Dos conclusiones y una trampa:

- **Las voces fuera del APK** son 135 MB de diferencia. Decisión cerrada.
- **`abiFilters = ["arm64-v8a"]`** ahorra otros 90 MB: el AAR trae cuatro ABIs y
  el vehículo solo necesita una.
- **La trampa**: `abiFilters` puesto en un módulo de *librería* no filtra nada.
  Tiene que estar en el módulo de *aplicación*. Costó un build de 124 MB
  descubrirlo.

De los 34 MB restantes, 30 son `libonnxruntime.so` y `libsherpa-onnx-*.so`. Es el
precio del motor. Si hiciera falta bajarlo, la variante
`sherpa-onnx-static-link-onnxruntime` es la siguiente puerta a la que llamar.

## Instalación de voces: probada de extremo a extremo

Implementada y verificada contra un CDN local servido desde el host de desarrollo,
que es lo más parecido al flujo real sin montar infraestructura.

Ciclo completo desde una instalación limpia, sin ninguna voz en el dispositivo:

```
voces ya instaladas: ninguna
--- EN ---
paquete: 21 MB · licencia: LJ Speech, dominio publico
  descargando 5% ... 100%
  verificando checksum
  extrayendo
  instalada
instalacion completa: 2,7 s
cargando voz en: en_US-ljspeech-medium.onnx
factor tiempo real : 0,220  OK, cumple RNF-7b
```

Lo que hace [`VoiceInstaller`](../../feature/voice/src/main/kotlin/com/secretworld/auto/feature/voice/VoiceInstaller.kt):

- Descarga con **progreso real**, informando cada 5 % para no inundar la UI.
- **Verifica `sha256`** antes de instalar. La conexión de un vehículo se corta, y
  un modelo a medias que cargue igual es un fallo caro de diagnosticar.
- **Instala de forma atómica**: monta en un directorio de trabajo y lo mueve al
  final, así que nunca existe una voz incompleta.
- Protege contra **zip slip** al extraer.
- Permite **desinstalar**: el disco del vehículo no es infinito.

Los 2,7 s de instalación son sobre loopback: es el coste de verificar y extraer.
La descarga real depende de la conexión del vehículo — a 5 Mbps, 21 MB son unos
35 segundos.

## Veredicto

- **Rendimiento**: verde, con mucho margen.
- **Integración**: verde. sherpa-onnx se integra sin fricción y el motor queda
  detrás del puerto `SpeechSynthesizer` del dominio, así que sustituirlo por el
  `TTS_SERVICE` de Pleos el día que exista no toca nada por encima.
- **Calidad**: pendiente de escucha humana.
- **Licencia del italiano**: **rojo**. Es el único bloqueante real que deja este
  spike, y es legal, no técnico.


---

# Bucle completo con vehículo simulado

Añadido el 2026‑09‑03. Cierra la cadena entera del MVP:

```
posición → TourQueue → NarrationPrefetcher → síntesis a disco
                    → NarrationTrigger → ¿estaba listo el audio?
```

Escenario: cinco lugares a lo largo de 14 km de la via Appia, vehículo a 90 km/h
rumbo norte, presupuesto de caché de 30 MB, *lookahead* de 2.

```
t=0s    km 0,0  cola: porta-san-sebastiano, tomba-cecilia-metella
        sintetizado por delante: los dos (11,3 s)
t=40s   >>> NARRAR porta-san-sebastiano: audio LISTO en disco (538 KB)
t=80s   sintetizado por delante: villa-dei-quintili (2,3 s)
t=140s  >>> NARRAR tomba-cecilia-metella: audio LISTO (520 KB)
t=220s  sintetizado por delante: casal-rotondo (2,5 s)
t=260s  >>> NARRAR villa-dei-quintili: audio LISTO (492 KB)

cache final: 4 narraciones, 2038 KB
TODOS los disparos encontraron el audio ya listo
```

**El resultado que importa: cero esperas.** En marcha, sintetizar por delante
convierte un factor de tiempo real de 0,22 en latencia cero en el momento de
hablar, que es lo que pedía RF‑1.2.

En régimen, preparar un lugar cuesta **2,3–2,5 s** con el motor caliente, y la
caché se mantiene en unos 2 MB para cinco lugares. El disco no es un problema: el
presupuesto de 30 MB da para más de sesenta narraciones.

## El punto débil que destapa la prueba

La primera pasada costó **11,3 segundos**, porque incluye cargar el modelo. En el
simulacro no molestó —el disparo llegó a t=40s—, pero **el arranque en frío es el
caso que puede fallar**: si el conductor arranca y a 500 metros hay un lugar, no
da tiempo. RNF‑1 pide audio en menos de 3 s desde frío y eso no se cumple con el
motor sin cargar.

Mitigaciones, por orden de sensatez:

1. **Cargar el motor al arrancar la app**, antes de que el coche se mueva. La
   carga son 1,5–3 s y se paga una vez; lo caro es hacerlo tarde.
2. **Persistir la caché entre sesiones.** Hoy el bucle la vacía al empezar porque
   es una demostración; en producto, lo ya sintetizado sobrevive al apagado y el
   arranque en frío casi siempre encuentra algo listo.
3. Aceptar que el primer lugar de un trayecto pueda saltarse si no da tiempo.
   `NarrationTrigger.isTooLate` ya sabe decidirlo, y saltar es mejor que narrar
   tarde.


---

# Addendum del 2026‑09‑04: la app ya narra, y dos avisos

## La reproducción estaba sin conectar

Hasta hoy el botón *Escuchar* solo cambiaba una etiqueta: `onListen` no llamaba a
nada. Las piezas estaban construidas y probadas por separado —instalación de voz,
síntesis, caché, cola— pero **nunca se unieron a la interfaz**. Ya están:

- [`NarrationPlayer`](../../feature/voice/src/main/kotlin/com/secretworld/auto/feature/voice/NarrationPlayer.kt)
  reproduce el WAV pidiendo **foco de audio** y soltándolo al terminar. En un
  coche se comparte altavoz con radio, navegación y llamadas: una app que no
  negocia el foco es una app que pisa a las demás.
- [`NarrationController`](../../feature/voice/src/main/kotlin/com/secretworld/auto/feature/voice/NarrationController.kt)
  une instalar, sintetizar por delante y sonar, y publica su estado para que la
  pantalla diga la verdad —*instalando voz 40 %*, *preparando X…*, *sonando X*—
  en vez de fingir.

Dos bugs reales encontrados al conectarlo:

1. **`LaunchedEffect` con clave inestable.** La clave era `upcoming.map { it.id }`,
   una lista nueva en cada recomposición, así que el efecto se relanzaba sin
   parar y apilaba prefetches.
2. **`engineFor` no era thread-safe.** Con varios prefetches simultáneos se
   construían varios motores de decenas de MB a la vez. Ahora va bajo candado.

## Aviso 1: el emulador no tiene audio en este Mac

```
coreaudio: Could not set audio format change listener
coreaudio: Reason: kAudioHardwareIllegalOperationError
Failed to create voice `virtio-snd-mic0'
```

**Aunque la reproducción sea correcta, por los altavoces no sale nada.** No es la
app. Para juzgar la voz hay que escuchar los WAV que genera —están en este
directorio— o probar en un dispositivo real.

## Aviso 2: el factor de tiempo real medido depende del host

Mismo código, mismo modelo, mismo emulador, dos días distintos:

| | RTF |
|---|---|
| Mac con holgura (2026‑09‑03) | **0,22** |
| Mac saturado tras una sesión larga (2026‑09‑04) | **1,64** |

Un factor **7,5×** de diferencia. La cifra de 0,22 no es una propiedad del motor:
es una medida tomada en unas condiciones concretas. Con el host ahogado, la
síntesis pasa a ir más lenta que el tiempo real y la estrategia de ir por delante
deja de compensar.

**Consecuencia para el proyecto**: el presupuesto de RNF‑7b no se puede dar por
cumplido hasta medirlo en **hardware de vehículo**, no en un emulador sobre un
portátil de 8 GB. Es la medida que falta.

## Nota operativa

Este emulador se degrada con el uso: llegó a tener ANRs en `system_server`.
Cuando la síntesis deja de completarse, la causa suele estar fuera de la app.
Reiniciarlo en frío lo devuelve a un estado utilizable.


---

# Medido en hardware real · 2026‑09‑04

**moto g75 5G**, Android 16, arm64-v8a, 1080×2388 a 400 dpi. Es la primera medida
que no viene de un emulador, y por tanto la primera que significa algo.

| | |
|---|---|
| Audio generado | 68,7 s |
| Síntesis | **22,43 s** |
| Carga del modelo + síntesis | 26,37 s |
| **Factor de tiempo real** | **0,326** |
| Memoria de heap | 2 → 8 MB |
| Instalación de la voz desde el APK | 1,5 s |
| Preparar 2 lugares por delante | 19,5 s, carga incluida |
| Reproducción | `MediaPlayer`, narración de 20,9 s |

## Las tres medidas, juntas

| Entorno | RTF |
|---|---|
| Emulador, Mac con holgura | 0,22 |
| **Móvil de gama media, real** | **0,326** |
| Emulador, Mac saturado | 1,64 |

**RNF‑7b se cumple en hardware real con margen**: 0,326 frente a un presupuesto de
0,5. Y un móvil de gama media es una referencia razonable —probablemente
conservadora— para un SoC de infotainment.

Lo que esto cierra: la estrategia de sintetizar por delante es viable de verdad,
no solo en una medición afortunada. Lo que no cierra: sigue faltando medirlo en
el vehículo, donde la app compite por CPU con navegación, radio y sistema.

## Y suena

Verificado con altavoces: la app instala la voz, sintetiza, y **reproduce**. El
estado en pantalla dice *"Sonando: Porta San Sebastiano (20 s)"* mientras suena.
El emulador nunca pudo demostrarlo porque su salida de audio está rota en este
Mac; una tablet o un móvil sí.

## Tres bugs que solo aparecieron en hardware real

1. **`assets.openFd` sobre un asset comprimido lanza** `FileNotFoundException`.
   El tamaño se toma ahora del catálogo, que ya lo conoce.
2. **El prefetch no se reintentaba tras instalar la voz.** El primer intento
   llegaba antes de que la voz existiera, salía por la puerta de atrás y nadie
   volvía a intentarlo. Ahora el instalador dispara el prefetch al terminar.
3. **El umbral de layout usaba solo el ancho.** Un móvil en horizontal mide
   955 dp de ancho y pasaba por pantalla de coche, pero **solo tiene 432 dp de
   alto**: el contenido se salía y únicamente se veían los botones. El
   discriminante real es el alto.
