# ROADMAP

Versión 0.2. Las fechas son relativas al arranque, que **ya se ha producido**:
el emulador arm64 de Pleos Connect funciona en local desde el 2026‑09‑03. Ver
[SETUP.md](SETUP.md).

> Cambios respecto a 0.1: el alta en Playground ya está hecha, así que la fase 0
> pasa de trámite a **spikes técnicos**. El audio pre-generado deja de ser
> dependencia crítica: primero se prueba el TTS del vehículo. Se añade el flujo
> de trabajo de contenido, que es ahora la ruta crítica real.

Supuesto para las estimaciones: **1 desarrollador Android a tiempo completo**,
con apoyo editorial para el contenido. Con dos desarrolladores las fases 2 y 3 se
solapan.

---

## Fase 0 — Spikes y verificación · 2–3 semanas

Ya somos desarrolladores registrados en Pleos Playground. Lo que queda es
técnico y de consulta, y está detallado en [SETUP.md](SETUP.md).

- [x] Solicitar, instalar y arrancar el emulador. *(2026‑09‑03: la primera entrega fue x86_64 e inservible en Apple Silicon; la build arm64 funciona)*
- [x] Esqueleto de módulos con `:core:domain` aislado y verificación de pureza en `check`. *(2026‑09‑03: 8 módulos, `./gradlew check` en verde, APK arrancando en el emulador)*
- [ ] **Spike 1**: ¿SpeechToText propio o vía asistente Gleo? Es la pregunta que
      puede cambiar el producto.
- [x] **Spike 2 · motor TTS embebido** *(2026‑09‑03)*: sherpa-onnx + Piper int8 integrado y midiendo en el emulador. **RTF 0,20 frente al presupuesto de 0,50**, memoria plana. Resultados en [spikes/voice/RESULTS.md](spikes/voice/RESULTS.md).
- [ ] Escuchar los 90 s generados y decidir si la calidad pasa el listón. Es lo único del spike 2 que no puede hacer una máquina.
- [ ] **Resolver la licencia de la voz italiana.** Es el bloqueante que deja el spike: ninguna voz italiana de Piper tiene hoy licencia verificada para uso comercial. El inglés sí (`ljspeech`, dominio público).
- [ ] **Spike 3**: Fused Location y Vehicle SDK.
- [ ] **Spike 4**: traspaso de destino con NaviHelper.
- [ ] **Spike 5**: audio en segundo plano y pantalla partida.
- [ ] **Solicitar ya los servicios de Gleo** (`STT_SERVICE`, `TTS_SERVICE`,
      `LLM_SERVICE`). Son *Request Required*: el plazo de aprobación es
      desconocido y no bloquea la v1, pero sí el nivel 2. Cuanto antes entre la
      solicitud, antes se sabe la fecha real.
- [ ] En paralelo, las consultas a Pleos: política de compra externa, revenue
      share, pre-aprobación de permisos, reglas de distracción.

**Hito 0**: informe con estimación firme en lugar de rango, riesgos cerrados y
las dos decisiones grandes tomadas (voz propia o asistente; TTS del vehículo o
audio propio). Es el punto de go / no-go real.

## Dónde estamos de verdad

Actualizado el 2026‑09‑10. Conviene ser exacto, porque es fácil confundir una
prueba de concepto con un producto.

**Lo construido es una prueba de concepto técnica.** Demuestra que el producto se
puede hacer y que rinde. No demuestra que alguien lo quiera usar, porque todavía
no hay nada que usar.

### Lo que ve hoy un usuario que abre la app *(actualizado el 2026‑09‑10)*

- **El catálogo real de Secret World** por posición: nombres, fotos, textos y
  ciudad, servidos por `POST /places/cards`. En Roma devuelve 32 lugares útiles.
- Un **mapa permanente** con esos lugares encima: Mapbox en el banco de pruebas,
  lienzo propio en el vehículo.
- **Narración que suena**, sintetizada en el dispositivo y preparada por delante.
- **Órdenes por voz sin tocar nada** *(2026‑09‑07)*: se dice *"Siky"* y, en la
  misma frase, una de las cuatro órdenes del nivel 1 —"cuéntame más", "llévame
  ahí", "sáltalo", "¿qué hay cerca?"—. La gramática vive en el dominio, en Kotlin
  puro y auditable; la respuesta se habla con el mismo motor que narra.
- Cuatro pantallas: home, detalle, itinerario y ajustes.
- Respaldo sin cobertura: 18 lugares reales guardados, y la app **dice** que está
  en modo respaldo en vez de quedarse en blanco.

Lo que sigue faltando, y no es poco: la **escucha no es eficiente** —se reabre el
reconocedor en cada frase, no hay detector de palabra clave—, el **mapa del
vehículo no enseña calles**, **"Llévame ahí" no fija destino**, el **vehículo es
simulado**, el contenido no está reescrito para ser oído y el itinerario es de
demostración porque los reales exigen cuenta.

### Lo que sí quedó demostrado, y no era gratis

Cinco supuestos del plan original resultaron falsos, y solo se descubrieron
construyendo:

1. La plataforma **no expone nada por las APIs estándar de Android**: vive en
   `pleos.car.permission.*`.
2. **No hay motor TTS** por la vía de AOSP, y el de Pleos está *coming soon*.
3. **`geo:` no lo atiende nadie**: no existe el traspaso estándar de destino.
4. Las **voces que mejor suenan no se pueden licenciar** comercialmente.
5. El **rendimiento** es viable en hardware real —RTF 0,326—, pero la cifra del
   emulador oscilaba entre 0,22 y 1,64 y no servía para decidir nada.

Sin esto, la fase 1 se habría escrito sobre cinco supuestos equivocados.

### Lo que falta para que esto sea un MVP

En orden de cuánto acercan el producto a alguien que lo usaría:

1. ~~Catálogo real~~ **hecho el 2026‑09‑04**: `:core:data` contra
   `api.sworld.co.uk`, con filtrado de artículos de marketing, deduplicación de
   fichas repetidas y respaldo sin red.
2. **Contenido real** — segmentos de 60 a 90 s escritos para ser oídos. Es ahora
   **la brecha más visible**: los textos del catálogo son de longitud artículo y
   se están recortando a las primeras frases como apaño. Sigue siendo la ruta
   crítica y no es trabajo de desarrollo.
3. ~~Detección de voz eficiente~~ **hecho el 2026‑09‑15, medido en un Moto g75**:
   detector de palabra clave, detector de voz y Whisper *base*, los tres con
   sherpa-onnx en el dispositivo y sin red; detrás, Gemma 3 1B (MediaPipe, CPU)
   para lo que la gramática no prevé. Cargan en 2–5 s, ~900 MB de RAM con todo
   residente. Lo que queda abierto de aquí, con dato:
   - **Interrumpir la narración por voz no funciona con altavoz y micrófono
     juntos**: la app se oye a sí misma a 10× el nivel de la voz (0,08 frente a
     0,008). Probados y descartados en este móvil el `AcousticEchoCanceler`
     (no cancela), `VOICE_COMMUNICATION` (deforma la voz, el detector no la
     reconoce) y el `NoiseSuppressor` (la silencia). Hace falta cancelación de
     eco con la señal reproducida como referencia: la del vehículo o WebRTC APM
     por software. Con auriculares funciona.
   - **Whisper *base* oye mal las órdenes cortas** ("what's nearby" → "what's
     new by", "glass new buy"). El LLM lo absorbe —«What's new? Bye?» →
     `what_is_nearby`— pero cuesta ~1,5 s más. Whisper *small* está descargado
     para medir si el tiempo compensa.
4. ~~Mapa real~~ / ~~Navegación~~ **hechos en el banco de pruebas el
   2026‑09‑15**, por Mapbox como puente: posición real del dispositivo
   (`LocationManager`, sin Play Services), rutas por Mapbox Directions dibujadas
   en el mapa, guía por maniobras en voz alta, recálculo al salirse y aviso de
   llegada. Medido en Cartagena: "Ey Sícret, llévame al cerro de la Popa" →
   ruta de 7,2 km con 15 maniobras. Lo que sigue abierto es lo de siempre: en
   el vehículo, `NaviHelper` de Pleos.
5. **Lugares reales con texto.** El catálogo editorial tiene tres fichas por
   ciudad. Se suman los de **Wikipedia por geolocalización** —22 monumentos en
   10 km de Cartagena, con introducción en el idioma y foto—, filtrando
   eventos, provincias y aeropuertos. Licencia CC BY-SA: hay que atribuir.
6. **Reconocimiento:** Whisper base destrozaba el español. Se cambió por el
   FastConformer multilingüe de NVIDIA (en/es/fr/de): 6× más rápido (~110 ms
   por frase en el móvil) y entiende los dos idiomas. La palabra de activación
   es **por idioma**: "Hey Secret" con el detector de palabras clave en
   inglés; "Ey Sícret" por transcripción en español, porque el detector solo
   sabe inglés y ninguna palabra española le dispara (medido: 0/4 en todas).
   Coste a medir: en español se transcribe todo lo que se dice en el coche.
7. **Contenido en el idioma.** Lo del catálogo solo existe en inglés; se
   traduce en el dispositivo con Gemma una vez por lugar (10–20 s en segundo
   plano). Correcto, no literario: la traducción editorial sigue siendo humana.
6. **Posición real del vehículo** en lugar del simulador.
7. **Cuenta vinculada**, para que favoritos e itinerarios aparezcan en el coche.

Con 1 y 2 la app ya sería algo que un turista podría encender en un viaje; con 3,
4 y 5, algo que se puede usar conduciendo. Sin ellas, no.

### Lo que falta para poder enviar el APK a un desconocido

Actualizado el 2026‑09‑15. Es la pregunta que decide si esto es un MVP o una
demostración: ¿qué pasa si el APK llega a alguien en Nueva York o en Milán?

- **La localidad se adapta sola**: GPS real, catálogo por posición, Wikipedia
  y búsqueda de Mapbox funcionan en cualquier ciudad, y las rutas también.
- **El idioma, solo inglés y español.** Nueva York recibiría todo; Milán
  recibiría el mapa y las rutas, pero en inglés y sin entender italiano.
- **Los modelos no llegan.** En el móvil de pruebas se cargaron con `adb`; la
  app sabe descargarlos, pero `ModelCatalog.baseUrl` apunta a la máquina de
  desarrollo. Sin ellos no hay palabra de activación, ni reconocimiento en el
  dispositivo, ni LLM: queda el botón con el reconocedor de Google.

Tareas, en orden. Sin las dos primeras no hay MVP que enviar:

- [ ] **Alojar los paquetes en un CDN real** —voces (~15–22 MB cada una),
      modelos de escucha (~110 MB) y LLM (~390 MB)— y apuntar `ModelCatalog` y
      `VoiceCatalog` ahí. Falta decidir dónde: el backend de Secret World, un
      bucket, o una *release* de GitHub. Lo demás —descarga, `sha256`,
      instalación atómica— ya existe.
- [ ] **Pantalla de primera ejecución** (SPEC RF‑1.8.8): una sola ventana de
      «Descargando actualizaciones» con progreso agregado, que no deje pasar sin
      el paquete del idioma y deje el LLM terminar detrás. Hoy solo se instala
      la voz, sola y en segundo plano.
- [ ] **Italiano**, si Milán importa: cambiar al FastConformer de 10 idiomas
      (`be-de-en-es-fr-hr-it-pl-ru-uk`, incluye italiano), incluir la voz
      italiana y **resolver su licencia**, y hacer la tabla italiana de gramática
      y frases. El español costó un día; esto, parecido.
- [ ] **Sin cobertura, lo que no se puede con el puente**: teselas del mapa y
      cálculo de rutas necesitan red. Los sitios ya se guardan por zona una
      semana. Mapa y rutas sin red los da el navegador del vehículo, no Mapbox
      por REST; en el banco de pruebas es un límite conocido.
- [ ] Probar la guía **en marcha**: maniobras a tiempo, recálculo al salirse,
      llegada. Todo está escrito y nada está probado con el coche moviéndose.

## Fase 1 — El MVP acordado · 4–6 semanas

El **nivel 1** de [SPEC.md §2.1](SPEC.md): acompañante determinista, vocabulario
cerrado, audio pre-generado, funcionando sin cobertura. Nada más entra hasta que
esto funcione. El asistente conversacional abierto —nivel 2— viene después, y a
propósito: el nivel 1 demuestra la capacidad entera del producto con riesgo
acotado.

- [ ] `SecretWorldApi` sobre los endpoints existentes.
- [ ] Room, caché y `AudioPrefetcher`.
- [ ] `TourQueue` v1 y `NarrationTrigger` (anticipación por velocidad).
- [ ] Servicio de media e integración con el centro multimedia del vehículo.
- [x] **Vocabulario cerrado con palabra de activación** *(2026‑09‑07)*: gramática en el dominio para "cuéntame más", "llévame ahí", "sáltalo" y "¿qué hay cerca?", con *"Siky"* al principio de la frase y pruebas sobre transcripciones sucias. Micrófono por `SpeechRecognizer` de AOSP: el `SpeechService` de Pleos acepta la conexión pero su `onBind` devuelve `null`.
- [ ] **Detector de palabra clave *offline*** delante del reconocedor, con sherpa-onnx. Es lo que convierte la escucha permanente en algo que se puede dejar encendido.
- [ ] Traspaso de destino vía NaviHelper.
- [x] **Motor TTS embebido con descarga de voces al primer uso** *(2026‑09‑03)*: `VoiceInstaller` con progreso, verificación `sha256` e instalación atómica, probado de extremo a extremo contra un CDN local.
- [x] **Síntesis por delante a caché de disco** *(2026‑09‑03)*: `NarrationPrefetcher` en el dominio con presupuesto y política LRU, `DiskNarrationStore` en disco, y bucle completo probado con vehículo simulado — **cero esperas en el momento de narrar**.
- [ ] **Spike: LLM de intenciones en el dispositivo** (Gemma 3 1B int4, MediaPipe o llama.cpp). Solo entiende y llama a las órdenes existentes; entra detrás de la gramática cuando esta devuelve `NotUnderstood`. Medir en hardware real: RAM residente, latencia por frase con y sin caché de prefijo, y acierto sobre ~50 frases fuera de gramática en inglés e italiano. Go/no-go: < 1,5 s y < 1,5 GB.
- [ ] **Primer arranque con ventana «Descargando actualizaciones»** (SPEC RF‑1.8.7–1.8.9): paquete de idioma —voz, reconocimiento, palabra de activación— y LLM por el mismo `VoiceInstaller`. **En pruebas van empaquetados**; la build de tienda no. Probar desde dispositivo limpio antes del envío.
- [ ] Cargar el motor de voz al arrancar la app y **persistir la caché entre sesiones**: es lo que falta para cumplir RNF‑1 en arranque en frío.
- [ ] Prueba en modo avión: el nivel 1 completo debe funcionar sin red, voces ya descargadas.
- [ ] **Contenido**: piloto de ~200 lugares en **inglés e italiano**, formato conversacional.

**Hito 1**: en el emulador con posición en movimiento, la app narra un lugar
**antes** de que el conductor lo pase, acepta las tres intenciones por voz y pasa
el destino al navegador.

## Fase 2 — Superficie visual y ruta · 4–6 semanas

- [x] **Superficie de POIs en marcha** y **modo detenido**, en Compose con sistema de diseño propio *(2026‑09‑03)*. Capturas y hallazgos en [spikes/ui/](spikes/ui/).
- [x] `DrivingModeController` con vehículo simulado; falta engancharlo al Vehicle SDK real.
- [ ] Mapa e itinerarios en modo detenido. El mapa sigue pendiente de la decisión de licencia.
- [ ] **Registrar el CRN del emulador en Playground**: hasta entonces la pantalla solo funciona desactivando el setup wizard.
- [ ] `TourQueue` v2 sobre la ruta activa.
- [ ] Layouts para Connect-S, W y WF, en landscape, portrait y media pantalla.

**Hito 2**: recorrido completo, con el coche yendo a un destino y la app narrando
lo que hay en el camino.

## Fase 3 — Cuenta y acceso · 3–4 semanas

- [ ] Vinculación por código corto o QR.
- [ ] Sincronización de favoritos e itinerarios.
- [ ] Nivel gratuito con cupo y desbloqueo por cuenta vinculada (modelo A).
- [ ] `EntitlementResolver` contra servidor.
- [ ] Analítica propia, sin SDKs de atribución móvil.

**Hito 3**: candidata a release según los criterios de [SPEC.md §7](SPEC.md).

## Fase 4 — Homologación y publicación · 3–4 semanas

- [ ] Auditoría de distracción contra sus guidelines.
- [ ] Lista de permisos pre-aprobada y verificada.
- [ ] Pruebas de degradación: sin red, túnel, disco lleno, idioma sin voz.
- [ ] Legales, privacidad y consentimiento de ubicación.
- [ ] Envío al App Market e iteración sobre la revisión.

**Hito 4**: app aprobada y publicada.

---

## Vista agregada

| Fase | Duración | Acumulado |
|---|---|---|
| 0 · Spikes y verificación | 2–3 sem | ~3 sem |
| 1 · MVP | 4–6 sem | ~9 sem |
| 2 · Superficie visual y ruta | 4–6 sem | ~15 sem |
| 3 · Cuenta y acceso | 3–4 sem | ~19 sem |
| 4 · Homologación | 3–4 sem | **~5 meses** |

## La ruta crítica no es el código

El **contenido en formato conversacional** es la dependencia más larga y no
depende del desarrollo. Arranca en fase 0 y corre en paralelo todo el proyecto.
Si el piloto de 200 lugares no está listo, la fase 1 no cierra aunque el software
funcione.

Dos decisiones han acortado esta ruta: el TTS embebido elimina el pipeline de
audio pre-generado —el contenido vuelve a ser **texto**— y limitar la voz a
**inglés e italiano** reduce el trabajo editorial del piloto en un tercio respecto
a los tres idiomas previstos. Lo que no cambia es lo esencial: reescribir para ser
oído sigue siendo trabajo humano.

En paralelo también, y por la vía de negocio, la conversación B2B de
[docs/B2B-STRATEGY.md](docs/B2B-STRATEGY.md). No bloquea nada, pero cuanto antes
empiece, mejor.

## Después de la v1

1. **Nivel 2: el asistente conversacional** ([SPEC.md §2.2](SPEC.md)). **Sin
   fecha, y a propósito**: sus tres piezas —`STT_SERVICE`, `TTS_SERVICE` y
   `LLM_SERVICE`— figuran como *Request Required* y *coming soon* en el portal.
   Dependemos de que Pleos publique y de que apruebe la solicitud, y ninguna de
   las dos esperas está en nuestra mano. Se diseña para que enchufe; no se
   planifica contra ello.
2. **Android Auto**, reutilizando `:core:domain` casi entero. Es el siguiente
   paso natural y el más barato.
3. Pantallas de pasajero traseras con la experiencia completa.
4. Venta dentro del coche con la pasarela de Pleos, si el revenue share compensa.
5. **CarPlay**, como capa Swift dentro de la app iOS actual.
6. Comisión sobre servicios del vehículo: carga y aparcamiento.

Ver [docs/PLATFORM-STRATEGY.md](docs/PLATFORM-STRATEGY.md).
