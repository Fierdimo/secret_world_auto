# La voz: qué hacemos si la plataforma no la pone

Escrito el 2026‑09‑03, a partir de lo medido en
[../spikes/probe/RESULTS.md](../spikes/probe/RESULTS.md).

## El hecho, en dos capas

**Por la API estándar de Android no hay síntesis**: `TextToSpeech.getEngines()`
devuelve 0 e instanciarlo da `onInit → ERROR(-1)`.

**Por la capa propia de Pleos sí la hay**: `pleos.car.permission.TTS_SERVICE`,
protección `normal`, declarado por `ai.pleos.playground.caas`. Buscar por AOSP
daba un falso negativo.

Y el camino de reproducción está validado: un `AudioTrack` con PCM generado se
reprodujo entero, con el cabezal avanzando 44100 frames.

Es decir: **podemos sonar, y probablemente también sintetizar. Lo que no tenemos
todavía es la librería para hablar con su servicio.**

## Las opciones

### A · Audio pre-generado en servidor  ← recomendada

Un fichero de audio por (lugar × idioma), generado antes de publicar y servido
por CDN, con `duration`, `bytes` y `etag` en los metadatos del lugar.

**No es tecnología nueva para nosotros.** La tubería ya existe:
`POST /text-to-speech` contra `ttsapi.sworld.co.uk`. Lo que cambia es *cuándo* se
invoca — al publicar contenido, no al pulsar play.

A favor:

- **Calidad auditable.** Se escucha antes de publicar. Con una voz sintética
  sonando cuatro minutos en un coche, esto no es un lujo.
- **Cero latencia** en carretera y **funciona sin red**, que es RNF‑3.
- **Coste por lugar, no por reproducción.** Se paga una vez.
- Permite producción de verdad: pausas, música de fondo, mezcla, corrección de
  pronunciación de topónimos — imposible con TTS en vivo.

En contra:

- Almacenamiento y generación del catálogo × idiomas. Se acota empezando por
  3–4 idiomas y solo lugares marcados `car_ready`.
- **No sirve para texto dinámico**: nada que no exista de antemano puede sonar.

### B · Motor TTS embebido en la app

Empaquetar un sintetizador dentro del APK (Piper, sherpa-onnx, RHVoice,
eSpeak‑NG como librería nativa).

A favor: texto arbitrario, offline, sin depender de la plataforma ni del backend.

En contra: **tamaño** — una voz neuronal decente ronda decenas de MB, y son
varios idiomas; CPU en un infotainment estrecho; licencias por voz; calidad que
hay que validar una por una; y una app grande con librerías nativas tiene más
superficie de revisión en el App Market.

Veredicto: **no como base del catálogo**. Sí como plan B si hace falta texto
dinámico y sin red.

### C · TTS en la nube en tiempo real

Lo que hace hoy la app móvil. En el coche es la peor opción: latencia
impredecible, dependencia de cobertura justo donde no la hay, y coste por
reproducción.

Veredicto: **solo como complemento** para texto que no se pueda pre-generar, y
asumiendo que se degrada a silencio sin red.

### D · El TTS de la plataforma  ← **confirmado que existe**

Lo que se sospechaba está medido: Pleos **sí ofrece síntesis de voz**, por su
propia capa y no por la API `TextToSpeech` de Android. El permiso
`pleos.car.permission.TTS_SERVICE` existe, lo declara `ai.pleos.playground.caas`
y su protección es **`normal`**: se concede al instalar, sin diálogo al usuario.
El servicio vinculable es `...caas.service.speech.SpeechService`, y su acción
resuelve en el emulador. Detalle en [PLEOS-SDK-SURFACE.md](PLEOS-SDK-SURFACE.md).

A favor: sin coste de generación ni de almacenamiento, sin límite de catálogo, y
sirve para **texto dinámico**, que es justo lo que el nivel 2 necesita.

Por medir: **calidad y cobertura de idiomas** — el spike 2 vuelve a tener sentido.
Y si funciona sin red, cosa dudosa: el `SpeechService` declara acciones de
activación de *relay*, lo que huele a nube.

Falta además la **librería cliente**: el servicio está exportado y su acción
resuelve, pero el protocolo AIDL no es público.

## Recomendación

**A para el nivel 1, D para el nivel 2, B solo si hace falta texto dinámico sin
red.**

Que aparezca su TTS **no cambia** la recomendación de pre-generar el nivel 1, y
conviene decir por qué para que no parezca inercia: la narración es contenido
finito y editorial, se escucha durante minutos seguidos, y pre-generada se audita
antes de publicar, funciona sin cobertura y no cuesta por reproducción. Sigue
siendo mejor producto aunque haya alternativa.

Lo que sí cambia, y mucho, es que **el nivel 2 deja de tener un agujero**: la
síntesis dinámica que le faltaba puede venir de la plataforma, junto con el
reconocimiento y el modelo de lenguaje, y los tres permisos son de nivel
`normal`.

## La decisión de producto: tomada

**El MVP lleva TTS embebido, limitado a inglés e italiano.**

| | Nivel 1 · MVP | Nivel 2 · asistente |
|---|---|---|
| Qué dice | Narración y respuestas de vocabulario cerrado | Respuesta abierta y generada |
| Voz | **Opción B: motor embebido en el APK** | B mientras Gleo no exista; `TTS_SERVICE` cuando se publique |
| Idiomas de voz | **Inglés e italiano** | Los mismos, al principio |
| Red | No hace falta | Imprescindible |

Se elige B sobre A —audio pre-generado— por una razón de producto: **capacidad
desde el día uno**. Con un motor en el dispositivo la app puede decir cualquier
texto, lo que abre respuestas compuestas, nombres de lugar, distancias y tiempos,
y deja el nivel 2 a un paso en vez de a un rediseño.

### Lo que esto elimina

**La dependencia de backend más larga de la v1 desaparece.** Ya no hace falta
pre-generar un fichero por lugar × idioma, ni alojarlo en CDN, ni añadir
`duration`, `bytes` y `etag` al catálogo, ni construir el pipeline de publicación.
El contenido vuelve a ser lo que ya es: **texto**, que el catálogo ya tiene en 17
idiomas. Ver [BACKEND-REUSE.md](BACKEND-REUSE.md).

Limitar a dos idiomas reduce además el trabajo editorial del piloto en un tercio
respecto a los tres previstos.

### Lo que esto cuesta

Tres cosas, y ninguna es menor:

1. **Tamaño.** Un motor neuronal decente pesa. Orientativo: 20–65 MB por voz según
   calidad, más 15–25 MB de runtime nativo para `arm64-v8a`. Dos idiomas pueden
   irse a 100–170 MB de APK.
2. **CPU.** La síntesis compite con el resto del infotainment, que es más estrecho
   que un móvil medio. Hay que medir el *real-time factor* en el dispositivo, no
   suponerlo.
3. **Calidad no auditable.** Con audio pre-generado se escucha cada pieza antes de
   publicar. Con motor embebido no: sale lo que salga. Y el riesgo 4 de
   [SPEC.md §9](../SPEC.md) sigue en pie — un TTS mediocre a los cuatro minutos es
   peor que el silencio.

### La recomendación técnica: sherpa-onnx con voces Piper

- **sherpa-onnx** (k2-fsa, Apache‑2.0) trae AAR de Android ya construido, API
  Java/Kotlin, binarios `arm64-v8a` y un zoo de modelos. Evita tener que montar a
  mano ONNX Runtime más espeak-ng para la fonemización, que es lo que hay que
  hacer si se integra **Piper** directo.
- **Voces Piper VITS** para `en_US` e `it_IT`. Calidad claramente por encima de
  eSpeak-NG, que es la alternativa diminuta pero robótica: sirve de red de
  seguridad, no de voz de producto.
- **Descartado RHVoice**: licencia GPL, incompatible con una app propietaria.

### Dos decisiones de integración que conviene tomar ya

**No empaquetar las voces en el APK: descargarlas al primer uso.** Deja el APK
pequeño para la revisión del App Market y permite añadir idiomas sin publicar
versión nueva. Tras la descarga, el funcionamiento offline es idéntico.
*Implementado y verificado el 2026‑09‑03: 169 MB con voces dentro, 34 MB sin
ellas. Ver [../spikes/voice/RESULTS.md](../spikes/voice/RESULTS.md) y RF‑1.8.*

**No sintetizar en tiempo real: sintetizar por delante.** El `TourQueue` ya sabe
qué lugar viene después, así que se sintetiza con antelación a una caché en disco,
igual que el `AudioPrefetcher` iba a descargar audio. Así el coste de CPU nunca
cae sobre el momento de hablar, y RNF‑1 y RNF‑3 se cumplen sin depender de la
velocidad del motor. La síntesis en tiempo real solo hace falta en el nivel 2.

### El listón que hay que pasar antes de comprometerse

Un spike corto, antes de escribir producto encima:

1. Sintetizar un texto real de **90 segundos** en inglés y en italiano.
2. Escucharlo entero. ¿Aguantarías cuatro minutos de esto conduciendo?
3. Medir el *real-time factor* y el pico de memoria en el emulador arm64.
4. **Verificar la licencia de cada voz concreta.** Los modelos Piper derivan de
   datasets con licencias distintas —CC0, CC‑BY, CC‑BY‑SA— y algunas obligan a
   atribuir o contaminan. Esto es diligencia legal, no un detalle.

Si el punto 2 falla, el plan B es volver a pre-generar la narración y dejar el
motor embebido solo para el texto dinámico. Por eso la opción A se queda
documentada arriba en lugar de borrarse.
