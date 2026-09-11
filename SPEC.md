# SPEC — Secret World Auto

Especificación de producto y requisitos. Versión 0.3.

> Cambios respecto a 0.2: el producto se estructura en **dos niveles** — un nivel
> determinista y offline que es el MVP, y un nivel de asistente conversacional
> abierto que llega después. Se corrige la premisa de que Pleos aporta síntesis de
> voz: **medido, no hay motor TTS en el vehículo**. Se añaden los requisitos de la
> capa de diálogo, que antes no existían.
>
> Cambios de 0.1 a 0.2: el producto se redefine como acompañante conversacional en
> lugar de "app de guías de audio"; la UI en marcha puede ser nuestra, simplificada.

---

## 1. Problema y oportunidad

Conducir es el único contexto de viaje en el que el usuario tiene **tiempo,
atención auditiva libre y ubicación cambiante**, y ninguna forma cómoda de
consumir contenido sobre lo que está atravesando. Secret World ya tiene el
activo que falta ahí: un catálogo geolocalizado de sitios con contenido
narrativo en 17 idiomas.

Pleos abre el canal y pone las piezas, **pero no por las APIs estándar de
Android**: por una capa propia con su espacio de permisos `pleos.car.permission.*`
y servicios vinculables. Ahí están `TTS_SERVICE`, `STT_SERVICE` y `LLM_SERVICE`,
los tres con protección `normal` — se conceden al instalar. Buscar por el camino
de AOSP daba falsos negativos: `TextToSpeech.getEngines()` devuelve cero motores
y `geo:` no lo atiende nadie, y sin embargo la plataforma sí ofrece síntesis de
voz y sí permite fijar un destino. El mapa medido está en
[docs/PLEOS-SDK-SURFACE.md](docs/PLEOS-SDK-SURFACE.md).

## 2. Propuesta de producto

> **Un acompañante que te cuenta dónde estás, te responde cuando le preguntas y
> te orienta hacia lo que merece la pena.**

Mientras el vehículo se mueve, la app narra por voz los lugares con historia que
hay en el trayecto y alrededor, con el ritmo de un copiloto que conoce la zona:
habla cuando hay algo que merece la pena, se calla cuando no. El conductor puede
hablarle sin tocar nada.

El objetivo final es un asistente de verdad. El camino hasta él tiene **dos
niveles**, y la separación no es una concesión: es lo que permite entregar algo
utilizable sin depender de que todo esté resuelto, y lo que hace que el producto
siga funcionando cuando se cae la red.

### 2.1 Nivel 1 — el acompañante determinista *(MVP)*

Vocabulario cerrado, respuestas previsibles, **funciona sin cobertura**.

| El usuario dice | La app hace |
|---|---|
| "¿Qué es ese castillo?" | Narra el lugar que el vehículo acaba de pasar |
| "Cuéntame más" | Segundo nivel de contenido sobre lo que está narrando |
| "Llévame ahí" | Fija el destino en el navegador nativo vía NaviHelper |
| "Sáltalo" / "Ahora no" | Descarta y reordena la cola |
| "¿Qué hay cerca?" | Enumera en voz los 3 lugares más relevantes del entorno |

Todo el audio de este nivel —narración y respuestas— está **pre-generado**, así
que su calidad se audita antes de publicar y no depende de la red. El
reconocimiento de estas órdenes se resuelve por coincidencia sobre un vocabulario
cerrado, no por comprensión abierta.

Este nivel es el criterio de aceptación de la v1 (§7). Demuestra la capacidad
completa del producto —narrar, entender, orientar y navegar— con riesgo acotado.

### 2.2 Nivel 2 — el asistente conversacional

Lenguaje abierto sobre el catálogo: preguntar cualquier cosa, pedir
recomendación con criterio, encadenar preguntas manteniendo el contexto.

- *"¿Hay algo de la guerra civil por esta zona?"*
- *"Recomiéndame un sitio para parar a comer con algo que ver al lado."*
- *"¿Y de eso que me contaste antes, quién lo construyó?"*

Requiere tres piezas que el nivel 1 no tiene: **comprensión de lenguaje abierto**,
una **capa de diálogo con memoria y anclada al catálogo**, y **síntesis de voz
dinámica** — porque una respuesta generada no puede tener el audio pre-grabado.

Las tres pueden venir de la plataforma: `STT_SERVICE`, `LLM_SERVICE` y
`TTS_SERVICE`. Pero **las tres figuran en el portal como *Request Required* y
*coming soon***: existen los permisos en la imagen, no el servicio publicado
([docs/PLEOS-SDK-SURFACE.md](docs/PLEOS-SDK-SURFACE.md)).

Por eso **el nivel 2 no lleva fecha**. Depende de dos esperas encadenadas que no
controlamos —que Pleos publique y que nos aprueben la solicitud—, y la única
pieza sin alternativa propia es el modelo de lenguaje. Se diseña para que se
enchufe cuando llegue, no se planifica contra ella.

Que el modelo lo ponga la plataforma no exime de RF‑2.5: el anclaje al catálogo es
nuestro trabajo, lo ponga quien lo ponga.

**Este nivel exige red.** El reconocimiento del vehículo no funciona en local
(`isOnDeviceRecognitionAvailable = false`), y la generación tampoco. Sin
cobertura, el producto **cae al nivel 1 y lo dice**: sigue narrando y obedeciendo
órdenes, y avisa de que ahora no puede conversar. Degradar en silencio sería
peor que no tenerlo.

### 2.3 Con el vehículo detenido

Explorador visual del catálogo y de los itinerarios de la cuenta, sin
restricciones.

---

Esto es un producto que **en el móvil no tendría sentido y en el coche sí**. No
es Secret World reducido: es Secret World en el único formato donde una narración
larga y sin pantalla es una ventaja en lugar de un problema.

## 3. Usuarios

| Perfil | Situación | Qué espera |
|---|---|---|
| **Conductor en trayecto largo** | Autopista o carretera secundaria, 30 a 180 min | Compañía informada; cero interacción obligatoria |
| **Turista con coche** | Ciudad o región desconocida | "¿Qué hay cerca que valga la pena?" y navegación directa |
| **Pasajero** | Copiloto o asiento trasero | Exploración visual sin restricciones |
| **Usuario existente de Secret World** | Ya tiene cuenta, favoritos e itinerarios | Que su contenido aparezca en el coche sin reconfigurar nada |

## 4. Superficies de la aplicación

Tres superficies con reglas distintas, según el estado del vehículo.

### 4.1 Voz (siempre disponible, también en marcha)

Es el producto. No compite por la mirada del conductor, que es exactamente lo
que protegen las reglas de distracción.

- **Narración por proximidad**: segmentos de 60 a 90 segundos por lugar, disparados
  con antelación suficiente para que el usuario aún vea el sitio.
- **Cola inteligente** (`TourQueue`): se reordena según avanza el coche, encadena
  por proximidad y no repite lo ya escuchado. Prefiere el silencio al relleno.
- **Escucha activa** vía el reconocimiento del vehículo: vocabulario cerrado en
  el nivel 1 (§2.1), lenguaje abierto en el nivel 2 (§2.2).
- **Salida por audio pre-generado** en el nivel 1; síntesis dinámica en el nivel 2
  (§RF-1.2). El vehículo **no aporta motor TTS**.
- Integración con el centro multimedia del vehículo (mandos del volante,
  metadatos, portada) mediante servicio de media.

### 4.2 Pantalla en marcha (UI propia, simplificada)

Contra lo asumido en la v0.1, **la interfaz puede ser nuestra**: colores,
tipografía, iconografía y fotos de Secret World. Lo que se restringe es la
densidad, no la identidad.

- Listas cortas (orientativo: máximo 6 elementos), áreas táctiles grandes.
- Texto breve, sin scroll libre largo, sin vídeo ni animación llamativa.
- Tareas de uno o dos pasos como máximo.
- Dos acciones por lugar: **Escuchar** y **Navegar hasta aquí**.

### 4.3 Pantalla con el vehículo detenido (libertad casi total)

- Mapa con los lugares del entorno y del itinerario activo.
- Detalle de lugar con galería y texto completo.
- Itinerarios del usuario, con "empezar itinerario" que carga la cola de voz.
- Cuenta, idioma y suscripción.

La transición entre 4.2 y 4.3 es automática, instantánea y **sin cortar la voz**.

## 5. Requisitos funcionales

### RF-1 · Voz y contenido
- RF-1.1 El catálogo se sirve del backend existente (`POST /places/cards`), filtrado por lat/long y radio.
- RF-1.2 La voz del nivel 1 la produce un **motor TTS embebido en la app**, sin depender de la plataforma ni del backend. Se sintetiza **por delante**, a una caché en disco, aprovechando que `TourQueue` conoce el siguiente lugar: la CPU nunca se paga en el momento de hablar. El `TTS_SERVICE` de Pleos se adoptará cuando se publique, si su calidad e idiomas lo justifican. Ver [docs/VOICE-OPTIONS.md](docs/VOICE-OPTIONS.md).
- RF-1.7 **Los idiomas de voz de la v1 son inglés e italiano.** El resto del catálogo sigue existiendo en texto, pero un lugar sin contenido en uno de esos dos idiomas no entra en la cola de narración (RF-1.6). Añadir idiomas es añadir modelos de voz, no republicar la app.

### RF-1.8 · Instalación de voces y modelos
- RF-1.8.1 **Las voces no viajan en el APK.** Con las dos empaquetadas el APK pasaba de 34 MB a 169. Se instalan en tiempo de ejecución desde CDN, ~21 MB por idioma.
- RF-1.8.2 **La primera ejecución instala el idioma que elige el usuario**, con el idioma del vehículo como propuesta por defecto. Los demás idiomas se instalan cuando se piden.
- RF-1.8.3 La descarga muestra **progreso real**, se puede reanudar y **verifica `sha256` antes de instalar**. Un modelo a medias que se cargue igual es un fallo caro de diagnosticar.
- RF-1.8.4 La instalación es **atómica**: se monta aparte y se mueve al final. Nunca puede existir un directorio de voz incompleto.
- RF-1.8.5 **Sin voz instalada la app no narra, y lo dice.** Sigue siendo utilizable en la superficie visual con el vehículo detenido, y reintenta la instalación cuando haya red. Misma regla que RF‑2.6: degradar en silencio es peor que no funcionar.
- RF-1.8.6 Las voces se pueden **desinstalar**: el disco del vehículo no es infinito y el usuario puede haber probado idiomas que ya no usa.
- RF-1.8.7 **El mismo mecanismo instala todos los modelos, no solo las voces.** Un idioma es un *paquete*: voz TTS (~21 MB), modelo de reconocimiento de órdenes (~50 MB) y modelo de palabra de activación (KB). Aparte, y común a todos los idiomas, el **LLM de intenciones en el dispositivo** (Gemma 3 1B int4, ~530 MB; pendiente de spike) que entiende lo que la gramática no prevé y solo puede llamar a las órdenes existentes. Ninguno va en el APK publicado en la tienda.
- RF-1.8.8 **La primera ejecución muestra una sola ventana de «Descargando actualizaciones»**, con el progreso agregado del paquete del idioma elegido y del LLM. Mientras el paquete del idioma no esté completo la app no narra (RF‑1.8.5); el LLM puede terminar de bajar después, porque hasta entonces la gramática cerrada sigue atendiendo las órdenes. La descarga grande conviene hacerla con el vehículo detenido y, si la plataforma lo permite distinguir, preferentemente por Wi‑Fi.
- RF-1.8.9 **En las builds de prueba estos modelos van empaquetados o se instalan a mano** —el sabor `bench` lleva la voz inglesa dentro del APK para que baste instalar y pulsar—. Es una comodidad de desarrollo y **no es el comportamiento de la tienda**: la build `vehicle` que se publique tiene que pasar por RF‑1.8.8 desde un dispositivo limpio antes del envío.
- RF-1.3 El contenido debe reproducirse sin cobertura: texto y/o audio cacheados por zona con antelación.
- RF-1.4 Idioma del contenido igual al del vehículo, con override manual persistente.
- RF-1.5 **El contenido de coche es una variante editorial propia**: segmentos de 60 a 90 segundos, tono conversacional, escritos para ser oídos. Los textos actuales, de longitud artículo, no se usan tal cual.
- RF-1.6 Un lugar sin contenido de coche en el idioma activo no entra en la cola.

### RF-2 · Escucha y diálogo
- RF-2.1 **Nivel 1**: reconocimiento de las intenciones de §2.1 sobre vocabulario cerrado, con las respuestas habladas pre-generadas. Se usa el `SpeechRecognizer` estándar de Android —verificado vivo en el emulador, servido por `ai.umos.ambientai`—, **no el `STT_SERVICE` de Pleos**, que aún no está publicado. Queda por medir si funciona sin red.
- RF-2.2 Toda intención debe tener respuesta hablada; nunca una acción silenciosa.
- RF-2.3 La app no debe activarse por conversación entre ocupantes. **El hueco de asistente del sistema está ocupado por `ai.umos.ambientai`**, así que la activación por palabra propia puede no ser posible: se define en fase 0 (pulsación en volante, botón en pantalla, o entrada por su asistente).
- RF-2.4 **Nivel 2**: comprensión de lenguaje abierto sobre el catálogo, con memoria de la conversación en curso.
- RF-2.5 **Toda respuesta del nivel 2 va anclada al catálogo.** El asistente no inventa lugares, fechas ni hechos: lo que no esté respaldado por contenido nuestro se responde con "no lo sé". Un dato inventado sobre un monumento es peor que el silencio y no es recuperable ante el usuario.
- RF-2.6 El nivel 2 **degrada de forma explícita**: sin red, la app anuncia que no puede conversar y sigue operando en nivel 1. Nunca falla en silencio ni se queda esperando.
- RF-2.7 El asistente no responde fuera de su dominio. Ante una petición ajena al viaje y a los lugares, lo dice y no improvisa.

### RF-3 · Ubicación y ruta
- RF-3.1 La posición se obtiene del **Fused Location SDK** del vehículo.
- RF-3.2 Si hay ruta activa en el navegador nativo, se usa para precargar contenido por delante del coche.
- RF-3.3 **Anticipación**: el disparo de narración se calcula con la velocidad actual, de forma que el usuario aún vea el lugar. Narrar algo ya pasado se considera un fallo, no una degradación.
- RF-3.4 "Llévame ahí" entrega el destino al navegador nativo con **`requestRoute` + `startRouteGuiding`** (permiso `pleos.car.permission.NAVI_ROUTE_SEARCH`, nivel `normal`, sin restricción durante la conducción). La app no dibuja navegación propia. El intent estándar `geo:` **no sirve**: nadie lo atiende.
- RF-3.5 **La narración respeta las maniobras.** Con `getTBTInfo` se conoce el giro que viene; la app calla antes de una maniobra compleja en lugar de narrar encima. Es la diferencia entre copiloto y ruido.

### RF-4 · Estado del vehículo
- RF-4.1 Vehicle SDK como fuente de verdad del modo de UI (`Parked` / `Driving`).
- RF-4.2 (Deseable) Con autonomía baja en eléctrico, priorizar lugares con carga cerca.

### RF-5 · Cuenta e identidad
- RF-5.1 La app es usable **sin cuenta**. Nunca se pide login para arrancar.
- RF-5.2 Vinculación por código corto o QR en pantalla, jamás tecleando credenciales.
- RF-5.3 Con cuenta vinculada: favoritos, itinerarios y suscripción sincronizados.
- RF-5.4 El perfil del vehículo puede ser compartido: cierre de sesión sencillo y sin datos personales en la pantalla de inicio.

### RF-6 · Monetización
- RF-6.1 Nivel gratuito con cupo diario de lugares narrados.
- RF-6.2 Premium: catálogo completo, acompañamiento continuo, uso offline.
- RF-6.3 Cobro con la pasarela de Pleos; entitlements resueltos en el servidor.
- RF-6.4 Una suscripción activa en móvil vale en el coche y viceversa.

### RF-7 · Permisos y analítica
- RF-7.1 **Los permisos deben estar pre-aprobados con Pleos App Market.** Cualquier permiso no declarado o no aprobado provoca rechazo. Se define la lista en fase 0.
- RF-7.2 Eventos mínimos: sesión, narración iniciada y completada, intención de voz reconocida, destino fijado, conversión.
- RF-7.3 Sin SDKs de atribución móvil (AppsFlyer, Facebook, AppLovin).

## 6. Requisitos no funcionales

| # | Requisito | Umbral |
|---|---|---|
| RNF-1 | Arranque en frío hasta primera narración posible | < 3 s. *Medido: en régimen la narración ya está en disco y la latencia es cero, pero la **primera** síntesis cuesta ~11 s porque incluye cargar el modelo. Se cumple cargando el motor al arrancar la app y persistiendo la caché entre sesiones* |
| RNF-2 | Transición conducción a detenido | < 300 ms, sin cortar la voz |
| RNF-3 | Latencia de respuesta a una intención del **nivel 1** | < 1,5 s hasta empezar a hablar |
| RNF-3b | Latencia de respuesta del **nivel 2** (reconocer + generar + sintetizar) | < 3 s hasta empezar a hablar; por encima, la conversación deja de parecer conversación |
| RNF-4 | Funcionamiento sin red | **El nivel 1 completo** debe funcionar sin cobertura, con la zona cacheada ≥ 50 km alrededor. El nivel 2 requiere red por diseño (§2.2) |
| RNF-5 | Consumo de datos | < 150 MB/h en uso continuo |
| RNF-6 | Distracción | Cero UI compleja en marcha; toda interacción en marcha ≤ 2 pasos |
| RNF-7 | Idiomas | 17 en interfaz. **Voz en la v1: inglés e italiano**, por motor embebido. Los modelos se descargan al primer uso, no van en el APK |
| RNF-7b | Presupuesto de síntesis | *Real-time factor* < 0,5, sintetizando por delante y nunca al hablar. **Cumplido en hardware real: 0,326 en un móvil de gama media.** En emulador la cifra oscila entre 0,22 y 1,64 según la carga del host, así que solo cuenta la medida en dispositivo. Falta el vehículo, donde se compite por CPU |
| RNF-7c | Tamaño de la app | APK ≤ 40 MB sin voces. Cada voz ~21 MB, instalada aparte. **Medido: 34 MB** |
| RNF-8 | Pantallas | Familias **Connect-S**, **Connect-W** y **Connect-WF**, en **landscape y portrait** y a **media pantalla**. El sistema de diseño cambia a un juego de medidas compacto según el tamaño de ventana; el discriminante es la **altura**, no la anchura — un móvil en horizontal tiene 955 dp de ancho pero solo 432 de alto |
| RNF-9 | Plataforma | Android API 28 o superior, Gradle 8.0+, Kotlin |
| RNF-10 | Privacidad | Ubicación no persistida fuera del vehículo sin consentimiento explícito |

## 7. Criterios de aceptación de la v1

**La v1 es el nivel 1 completo** (§2.1). El nivel 2 no entra en la v1; entra
cuando el nivel 1 esté en la calle y las piezas de §2.2 estén resueltas.

En vehículo o en el emulador de Pleos:

1. Con el coche en movimiento y sin tocar nada, la app narra el lugar relevante
   más cercano **antes** de que el conductor lo pase, y encadena el siguiente.
2. "Llévame ahí" fija el destino y el navegador nativo lo acepta.
3. "Cuéntame más" y "sáltalo" funcionan sobre lo que se está narrando.
4. Con el coche detenido se ve mapa, ficha completa e itinerarios.
5. Un usuario existente vincula su cuenta con un código en < 60 s.
6. Una suscripción comprada en el coche desbloquea el catálogo en el móvil.
7. La app pasa la revisión de distracción y la de permisos de Pleos.
8. **Con el modo avión activado**, los puntos 1 a 3 siguen funcionando igual.

## 8. Fuera de alcance de la v1

Creación y edición de itinerarios en el coche, fotos y subida de contenido,
pasaporte, competiciones, juegos, blog, vídeo, reservas y widgets de booking,
pantallas de pasajero traseras, publicidad in-car.

## 9. Riesgos propios de este concepto

1. **El contenido es el trabajo grande, y no es código.** Reescribir o
   re-generar el catálogo en formato conversacional de 60 a 90 segundos es la
   dependencia crítica del producto.
2. **La anticipación separa la magia del ruido.** Ver RF-3.3.
3. **Densidad del catálogo en carretera.** El catálogo es urbano y turístico;
   hay que medir cuántos kilómetros de carretera real tienen cobertura antes de
   prometer acompañamiento continuo.
4. **La voz tiene que ser agradable durante horas.** Un TTS mediocre a los cuatro
   minutos es peor que el silencio. Con motor embebido **la calidad deja de ser
   auditable pieza a pieza**: sale lo que salga. Por eso el listón se prueba antes
   de escribir producto encima — 90 segundos reales en los dos idiomas, escuchados
   enteros. Si no pasa, se vuelve a pre-generar la narración.
4b. **Licencia de las voces — riesgo materializado.** Verificado el 2026‑09‑03:
   las voces Piper que mejor suenan son precisamente las que no se pueden
   publicar. El inglés está resuelto con `ljspeech`, que es de dominio público.
   **Para el italiano no hay hoy ninguna voz con licencia verificada**, lo que
   pone en duda RF‑1.7 en su mitad italiana. Ver
   [spikes/voice/RESULTS.md](spikes/voice/RESULTS.md).
5. **El nivel 2 depende de la red, y la carretera es donde peor está.** Medido:
   no hay reconocimiento en dispositivo. El riesgo no es que falle, es que falle
   justo en el trayecto largo por zona despoblada, que es el caso de uso estrella.
   Mitigación: el nivel 1 es autónomo y la degradación es explícita (RF-2.6).
6. **Un asistente que inventa destruye la marca.** Secret World vende contenido
   con criterio; un modelo que se inventa la historia de un monumento convierte
   ese activo en pasivo. RF-2.5 no es una preferencia técnica.
7. **El hueco de asistente ya está ocupado.** Si la activación por voz solo es
   posible a través de `ai.umos.ambientai`, el vocabulario del nivel 2 queda
   limitado a lo que ellos expongan y el producto cambia de forma. Es la pregunta
   del spike 1.
