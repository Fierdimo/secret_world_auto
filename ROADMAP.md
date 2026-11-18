# ROADMAP

Versión 0.4, actualizada el 2026‑11‑17. Las fechas son relativas al arranque,
que **ya se ha producido**: el emulador arm64 de Pleos Connect funciona en local
desde el 2026‑09‑03. Ver [SETUP.md](SETUP.md).

> Cambios respecto a 0.3: **fuera Mapbox de todas las versiones**, también la del
> teléfono: no tenemos licencia. Mapa, búsqueda y rutas son ahora de
> OpenStreetMap (MapLibre, Nominatim, Photon y OSRM). **Por ahora se prueba sin
> voz ni IA**, en el emulador local de Pleos, que no tiene ninguna de las dos: la
> app lo detecta al arrancar y se usa entera tocando, itinerarios incluidos.

> Cambios respecto a 0.2: **la guía hablada es del navegador del sistema**; la app
> no narra maniobras, recálculos ni llegadas, y su narración es siempre a petición
> del usuario. En el coche **no hay mapa propio de navegación**; la app
> es un panel junto al navegador nativo
> ([docs/NAVIGATION-AND-MAP-DECISIONS.md](docs/NAVIGATION-AND-MAP-DECISIONS.md)).
> Hay dos APK de prueba: la demo para teléfono y la versión Pleos con los
> servicios de la plataforma. La voz se ha medido en un móvil de gama baja
> (Snapdragon 685) y se ha ajustado para que la IA nunca la frene.

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
- [x] **Spike 1**: ¿SpeechToText propio o vía asistente Gleo? *(resuelto)* Las dos
      vías, según la build: la demo escucha en el dispositivo (palabra de
      activación y reconocimiento con sherpa-onnx, sin red); la versión Pleos usa
      el reconocedor de la plataforma (`ai.umos.ambientai`). En la v2.1.5 ese
      reconocedor dependía de la red; en Connect‑W está por medir.
- [x] **Spike 2 · motor TTS embebido** *(2026‑09‑03)*: sherpa-onnx + Piper int8 integrado y midiendo en el emulador. **RTF 0,20 frente al presupuesto de 0,50**, memoria plana. Resultados en [spikes/voice/RESULTS.md](spikes/voice/RESULTS.md).
- [ ] Escuchar los 90 s generados y decidir si la calidad pasa el listón. Es lo único del spike 2 que no puede hacer una máquina.
- [ ] **Resolver la licencia de la voz italiana.** Es el bloqueante que deja el spike: ninguna voz italiana de Piper tiene hoy licencia verificada para uso comercial. El inglés sí (`ljspeech`, dominio público).
- [ ] **Spike 3**: Fused Location y Vehicle SDK. *La posición real ya está*
      (`LocationManager` con todos los proveedores, sin Play Services); falta el
      Vehicle SDK.
- [ ] **Spike 4**: traspaso de destino con NaviHelper. *Medido: el servicio está
      en la imagen y responde; falta la librería cliente, que Pleos no ha
      publicado.* Mientras, en teléfono el traspaso va a Google Maps o Waze.
- [ ] **Spike 5**: audio en segundo plano y pantalla partida. *En teléfono, la app
      sigue narrando en una ventana flotante junto al navegador. En Pleos está
      por probar en Connect‑W, que solo arranca en un PC x86_64.*
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

Actualizado el 2026‑11‑17. Conviene ser exacto, porque es fácil confundir una
prueba de concepto con un producto.

**Lo construido es una prueba de concepto completa en teléfono**, con una
versión para Pleos que ya funciona en el emulador local (v2.1.5) usándose solo
con el tacto, y que todavía no se ha visto en su emulador actual, Connect‑W.
Demuestra que el producto se puede hacer y se entiende al usarlo; aún no
demuestra que funcione dentro del coche.

### Las dos APK de prueba

| APK | Para | Voz, escucha e IA | Estado |
|---|---|---|---|
| Demo | Un teléfono Android | Propias, dentro del APK (~750 MB) | Probada en un moto g75 y en un Honor con Snapdragon 685 |
| Pleos | Los emuladores de Pleos | Las de la plataforma, si las hay; si no, solo tacto | Probada en el emulador local v2.1.5 (arm64), sin voz ni IA. En Connect‑W (x86_64), sin probar: no arranca en Apple Silicon |

### Lo que ve hoy quien abre la app

- **El mapa es de OpenStreetMap** (MapLibre con los estilos de OpenFreeMap, día y
  noche), sin Mapbox.
- **«Qué ver»**: los sitios del catálogo y de Wikipedia alrededor, con foto y
  texto. Wikipedia se consulta en inglés, español e italiano y se junta por
  Wikidata, así que **los sitios son los mismos en los tres idiomas**; los
  eventos se filtran.
- **Búsquedas como un radar** (restaurantes, gasolina, aparcamiento…), que
  empiezan cerca y amplían el radio hasta encontrar bastante, también en otra
  zona y por nombre de cadena. Los negocios salen de OpenStreetMap (Nominatim
  por tipo, Photon por nombre); sin foto propia, llevan su inicial.
- **Órdenes de voz sin tocar nada** en inglés, español e italiano, empezando por
  *"Secret"*. La gramática local resuelve las básicas en milisegundos; una
  batería de pruebas recorre cada orden en los tres idiomas. Con auriculares se
  puede interrumpir a la app mientras habla.
- **Itinerarios**: sugeridos de viva voz o **tocando** —las preguntas y la
  propuesta salen en una tarjeta sobre el mapa con todas las respuestas como
  botones—, o armados a mano; presentados con un vuelo por el mapa, editables de
  viva voz, guardados en «Mis itinerarios» y enviados a Google Maps o Waze con la
  app en una ventana flotante.
- **Uso solo táctil**: si el dispositivo no puede hablar o escuchar el idioma, o
  su IA no contesta a tiempo, la app lo comprueba al arrancar, lo dice una vez
  con el micrófono tachado y sigue sin bloquear nada.
- **Narración** sintetizada en el dispositivo, frase a frase; si un sitio no tiene
  texto en el idioma, la app lo dice y pregunta antes de traducirlo.
- **Primer arranque** con pantalla de carga mientras se instalan voces y modelos;
  tema claro u oscuro en Ajustes; recuperación al volver la conexión.

### Lo que se midió en un móvil de gama baja *(2026‑11‑14, Snapdragon 685)*

| | Antes | Ahora |
|---|---|---|
| Voz lista tras abrir la app, con IA | 57 s | **7,7 s** |
| Una orden, de terminar de hablar a empezar a contestar | 4–8 s, 85 s en el peor caso | **1–3 s** |
| Reconocer una frase | ~3 s | **0,8–1,2 s** |

El modelo de IA (Gemma 3 1B) tarda en prepararse 9 s en un moto g75 y 41 s en el
Snapdragon 685, y en este último **no contesta en menos de 4 s** ni preparado. Por
eso nunca bloquea: se prepara en los ratos sin voz y solo se usa, con 4 s como
máximo, para lo que la gramática no entiende. Los tiempos quedan en el registro
`SWTiming`.

### Lo que sí quedó demostrado, y no era gratis

Supuestos del plan original que resultaron falsos, y que solo se descubrieron
construyendo:

1. La plataforma **no expone nada por las APIs estándar de Android**: vive en
   `pleos.car.permission.*`.
2. **No hay motor TTS** por la vía de AOSP; el de Pleos, en la v2.1.5, solo
   hablaba coreano.
3. **`geo:` no lo atiende nadie**: el traspaso de destino es por NaviHelper.
4. Las **voces que mejor suenan no se pueden licenciar** comercialmente.
5. El **rendimiento** es viable en hardware real, pero el del emulador no sirve
   para decidir nada.
6. **Mapbox en el coche exige un contrato de automoción aparte**, y para este
   proyecto no tenemos licencia de Mapbox. Hecho: fuera de todas las versiones;
   mapa propio con MapLibre y OpenStreetMap solo para los puntos, y en el coche
   el panel junto al navegador nativo.
7. **Pleos no da a la app la pantalla entera**: la abre en uno de dos huecos
   (1640 × 1400 apaisado y 800 × 1400 vertical en la v2.1.5) y le pasa las medidas
   de toda la pantalla. La app mide ahora su ventana real y se adapta a los dos.
8. **Un modelo de IA en el dispositivo no es viable en gama baja** para contestar
   en marcha: sirve como respaldo, nunca como camino principal.

### Cómo se prueba ahora: sin voz ni IA

Desde el 2026‑11‑17 las pruebas se hacen en el **emulador local de Pleos
(v2.1.5, arm64)**, que no tiene voz en ningún idioma salvo coreano, cuyo
reconocedor no recibe audio y cuya IA genera tan despacio que no sirve. La app
lo detecta al arrancar —la voz con una frase de prueba, la IA con una pregunta
que debe contestar en 10 s— y pasa a usarse solo tocando. Así se comprueban los
procesos a mano sin quitar nada: la voz vuelve en cuanto el dispositivo la tenga.

Recorrido probado tocando, de principio a fin:

- Inicio con el mapa, las secciones (qué ver, restaurantes, gasolina…) y la lista.
- Búsqueda por tipo en OpenStreetMap: en el Centro de Cartagena, 60 restaurantes.
- «Mis itinerarios» → «Sugerido» → cuánto tiempo → qué gusta → propuesta con su
  ruta → guardar o empezar.
- Ficha de un sitio con su texto, que sustituye a escucharlo cuando no hay voz.
- Los dos huecos de Pleos y el paso de uno a otro.

Pendiente de esta línea de pruebas:

- La tarjeta de ruta enseña la maniobra en bruto de OSRM («depart right…»), sin
  traducir. Como la guía es del navegador, quizá baste con no enseñarla.
- La conversación con voz no se ha vuelto a probar en un dispositivo que la
  tenga después de estos cambios.
- El emulador y una compilación completa a la vez agotan los 8 GB del Mac y el
  emulador se cierra: arrancarlo con `-gpu swiftshader_indirect` y no compilar
  todas las variantes con él en marcha.

### Lo que falta para que esto sea un MVP

En orden de cuánto acercan el producto a alguien que lo usaría:

1. **Contenido real**: segmentos de 60 a 90 s escritos para ser oídos. Sigue
   siendo la ruta crítica y no es trabajo de desarrollo.
2. **NaviHelper**: la librería cliente y las credenciales de `registerApp`. Sin
   ella, en el coche no se puede fijar destino.
3. **Probar la versión Pleos en Connect‑W**: voz, escucha y pantalla dividida.
   Hace falta un PC x86_64; la guía de instalación está en `docs/PLEOS-TEST.md`,
   en la rama `pleos`. *Mientras, se prueba en el emulador local sin voz ni IA.*
4. **Servidor propio de OpenStreetMap** —teselas, búsqueda y rutas— o un
   proveedor de pago. Los servidores públicos valen para demos pero limitan el
   uso (Nominatim, una petición por segundo).
5. **Interfaz simplificada para conducir**: el panel junto al navegador, según
   las normas de distracción.
6. **Caché sin conexión por zona**: sitios, textos, fotos, narraciones y mapa.
7. **Cuenta vinculada**, para que favoritos e itinerarios aparezcan en el coche.

### Lo que falta para poder enviar el APK a un desconocido

- [x] **La localidad se adapta sola**: GPS real, catálogo por posición, Wikipedia
      y búsquedas en cualquier ciudad. Probado en Cartagena y en Cancún.
- [x] **Italiano**: reconocimiento multilingüe, voz italiana y gramática.
      *Falta resolver la licencia de la voz italiana.*
- [x] **Pantalla de primera ejecución** con progreso, para lo que va dentro del APK.
- [ ] **Alojar los paquetes en un CDN** —voces, modelos de escucha y LLM— para
      una build de tienda que no los lleve dentro. Lo demás —descarga, `sha256`,
      instalación atómica— ya existe.
- [ ] **Sin cobertura**: los sitios se guardan por zona y la app se recupera
      sola al volver la red; falta guardar fotos y narraciones de una zona entera.
- [ ] Probar **en marcha**, con el coche moviéndose, el traspaso al navegador y la
      llegada a las paradas del itinerario. La guía hablada —maniobras,
      recálculos, llegada— es del navegador del sistema: la app no la narra.

## Fase 1 — El MVP acordado · 4–6 semanas

El **nivel 1** de [SPEC.md §2.1](SPEC.md): acompañante determinista, vocabulario
cerrado, audio pre-generado, funcionando sin cobertura. Nada más entra hasta que
esto funcione. El asistente conversacional abierto —nivel 2— viene después, y a
propósito: el nivel 1 demuestra la capacidad entera del producto con riesgo
acotado.

- [ ] `SecretWorldApi` sobre los endpoints existentes.
- [ ] Room, caché y `AudioPrefetcher`.
- [ ] `TourQueue` v1. ~~`NarrationTrigger` (anticipación por velocidad).~~ Descartado el 2026‑11‑03: no hay narración automática en marcha, toda narración la pide el usuario (SPEC RF‑3.3).
- [ ] Servicio de media e integración con el centro multimedia del vehículo.
- [x] **Vocabulario cerrado con palabra de activación** *(2026‑09‑07)*: gramática en el dominio para "cuéntame más", "llévame ahí", "sáltalo" y "¿qué hay cerca?", con *"Siky"* al principio de la frase y pruebas sobre transcripciones sucias. Micrófono por `SpeechRecognizer` de AOSP: el `SpeechService` de Pleos acepta la conexión pero su `onBind` devuelve `null`.
- [x] **Detector de palabra clave *offline*** delante del reconocedor, con sherpa-onnx *(2026‑09‑15)*. En inglés con detector; en español e italiano, por transcripción.
- [ ] Traspaso de destino vía NaviHelper. *Bloqueado por la librería cliente.*
- [x] **Motor TTS embebido con descarga de voces al primer uso** *(2026‑09‑03)*: `VoiceInstaller` con progreso, verificación `sha256` e instalación atómica, probado de extremo a extremo contra un CDN local.
- [x] **Síntesis por delante a caché de disco** *(2026‑09‑03)*: `NarrationPrefetcher` en el dominio con presupuesto y política LRU, `DiskNarrationStore` en disco, y bucle completo probado con vehículo simulado — **cero esperas en el momento de narrar**.
- [x] **Spike: LLM de intenciones en el dispositivo** (Gemma 3 1B, MediaPipe). Entra detrás de la gramática. *Medido: en gama media cumple; en un Snapdragon 685 no contesta en 4 s. No-go como camino principal; se queda como respaldo con 4 s de máximo.*
- [ ] **Primer arranque con ventana «Descargando actualizaciones»** (SPEC RF‑1.8.7–1.8.9). *Hecha la pantalla para lo empaquetado en el APK (2026‑11‑05); falta la descarga desde CDN para la build de tienda.*
- [x] Cargar el motor de voz al arrancar la app y **persistir la caché entre sesiones**. *Medido: voz lista a los 7,7 s en arranque en frío en un Snapdragon 685.*
- [ ] Prueba en modo avión: el nivel 1 completo debe funcionar sin red, voces ya descargadas. *Probado: la app se recupera sola al volver la red; falta el recorrido completo sin red.*
- [ ] **Contenido**: piloto de ~200 lugares en **inglés e italiano**, formato conversacional.

**Hito 1**: en el emulador con posición en movimiento, la app dice qué hay por
delante **cuando el conductor lo pide**, narra el lugar que elige antes de pasarlo,
acepta las órdenes por voz y pasa el destino al navegador. La narración es siempre
a petición del usuario: la app no habla sola en marcha (SPEC RF‑3.3).

## Fase 2 — Superficie visual y ruta · 4–6 semanas

- [x] **Superficie de POIs en marcha** y **modo detenido**, en Compose con sistema de diseño propio *(2026‑09‑03)*. Capturas y hallazgos en [spikes/ui/](spikes/ui/).
- [x] `DrivingModeController` con vehículo simulado; falta engancharlo al Vehicle SDK real.
- [x] Mapa e itinerarios en modo detenido. *Desde el 2026‑11‑17, con OpenStreetMap y sin Mapbox en ninguna versión: ver [docs/NAVIGATION-AND-MAP-DECISIONS.md](docs/NAVIGATION-AND-MAP-DECISIONS.md).*
- [ ] **Registrar el CRN del emulador en Playground**: hasta entonces la pantalla solo funciona desactivando el setup wizard.
- [ ] `TourQueue` v2 sobre la ruta activa: lo que hay por delante en la ruta, para
      ofrecerlo cuando el conductor pregunte, no para narrarlo solo.
- [ ] Layouts para Connect-S, W y WF, en landscape, portrait y media pantalla. *Prioridad: el panel junto al navegador. Hecho para los dos huecos de la v2.1.5: el grande con mapa y panel, el pequeño vertical con la lista a todo el ancho.*

**Hito 2**: recorrido completo, con el coche yendo a un destino y la app
contando, cuando se le pide, lo que hay en el camino.

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
