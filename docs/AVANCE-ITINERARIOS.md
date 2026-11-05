# Secret World Auto: avance en itinerarios y APK de prueba

Noviembre de 2026

## Resumen

La app ya arma, guarda y pone en marcha **itinerarios turísticos**: los propone
el asistente o los arma el usuario a mano, se revisan sobre un mapa animado y,
al empezar, la ruta pasa al navegador del usuario mientras la app sigue como
guía por voz.

Hay **dos APK**, porque no hemos podido probar la app sobre los servicios
reales de Pleos:

| APK | Para | Voz, escucha e IA | Peso |
|---|---|---|---|
| `app-demo-release.apk` | Un teléfono Android | Propias, dentro del APK | ~750 MB |
| `app-pleos-release.apk` | El emulador de Pleos **Connect‑W** (x86_64) | Las de la plataforma Pleos | ~60 MB |

**Descarga:** carpeta compartida en Google Drive: `[ENLACE A LA CARPETA DE DRIVE]`

## Contexto: por qué hay dos versiones

Al principio hubo una confusión de entorno. Las pruebas se hicieron sobre el
emulador **Pleos Connect v2.1.5**, que corre en un Mac con Apple Silicon (arm64).
Esa imagen **no trae operativos los servicios de Pleos** que necesitamos:

- Su síntesis de voz on-device **solo habla coreano**; en español, italiano e
  inglés responde "Unsupported locale".
- Su LLM on-device es un modelo de 0,5B que contesta en otro idioma y se
  inventa datos.
- El modo servidor de los servicios necesita credenciales (`registerApp`) que no
  tenemos.

Por eso la app se desarrolló con **voz, reconocimiento y LLM propios** dentro
del APK, y se ha probado en un teléfono (Motorola moto g75 5G).

El emulador que sí representa la plataforma actual es **Connect Waterfall**
(`POIP_EMULATOR_33_2685.JG.CW.SYS.KOR.033.005.260810.030000-x86_64`, compartido en
*Shared with me › Pleos connect emulator › connect waterfall_emulator*). Pero
**solo corre en x86_64**, y no tenemos dónde ejecutarlo: en un Mac con Apple
Silicon no arranca. Necesita un PC con Windows o Linux y virtualización por
hardware.

Para que alguien con ese equipo pueda probarla, se preparó la **versión Pleos**:
la misma app, pero hablando, escuchando y pensando con los servicios de la
plataforma.

## Lo que se avanzó en itinerarios

### Crear un itinerario

- **Sugerido por el asistente, de viva voz.** "Ármame un itinerario para esta
  tarde", "uno de tres horas con historia". Si falta algo (cuánto tiempo, qué
  le gusta) lo pregunta en una sola frase, y recuerda las preferencias en el
  dispositivo.
- **A mano.** Desde el inicio, el **+** de cada sitio lo añade a un itinerario. En
  el mapa del itinerario, los sitios de alrededor se pueden tocar para añadirlos.
  Por voz: "añade el Cerro de la Popa".
- **Añadir a uno que ya existe.** Si no hay ninguno abierto, la app pregunta a
  cuál va el sitio, o si empieza uno nuevo.
- **El plan es realista.** Cada parada tiene su tiempo de estancia según el tipo
  de sitio (un castillo no es una plaza), la hora de llegada a cada una y la
  comida si toca. El recorrido empieza en la primera parada, no en el coche.

### Presentarlo y cambiarlo

- **Presentación cinematográfica.** Al proponerse, la cámara recorre las paradas
  sobre un mapa nocturno simplificado, como en un videojuego. La ruta se dibuja
  tramo a tramo y la voz nombra todas las paradas con su tiempo.
- **Cambios de viva voz sobre el mismo mapa.** "Empieza por el castillo", "deja la
  catedral para el final", "más tiempo en el museo", "quita la plaza". Tras un
  cambio, la app dice solo lo que cambió y el total nuevo.
- **Guardar o empezar, dicho como sea.** "Guárdalo", "me gusta", "inicia el
  itinerario", "vamos", "más tarde"… No hace falta decir "sí". Lo que la
  gramática no entiende se lo pregunta al LLM.

### Mis itinerarios

- Varios itinerarios guardados en el dispositivo, presentados como la **selección
  de nivel de un juego**: la lista a la izquierda y, al fondo, el mapa, que vuela
  hasta el itinerario elegido.
- Desde ahí se empieza, se edita, se borra o se crea uno nuevo, sugerido o a mano.
- Los cambios a uno guardado se guardan al momento.

### En marcha

- Al empezar, la ruta se entrega al **navegador del usuario** (Google Maps o
  Waze) y la app pasa a una **ventana flotante** que solo atiende la voz.
- La app **no habla al iniciar la ruta**: la voz del navegador manda. Sí cuenta
  un sitio si se le pide mientras se conduce.
- "Termina el viaje" la devuelve a pantalla completa.
- Pedir que cuente un sitio no saca del itinerario: la cámara se acerca a él y
  vuelve al terminar.

### Alrededor del itinerario

- **Inicio sobre el mapa**, con los sitios para ver dentro de un radio de acción
  configurable en Ajustes (1 a 10 km). Si dentro hay muy poco, el radio se amplía
  solo.
- **Búsquedas como un radar** (restaurantes, gasolina, parqueaderos…): empiezan
  cerca según lo que se busca y se amplían hasta encontrar bastante. También en
  otra zona ("restaurantes en Bocagrande"); entonces el mapa se centra allí.
- **Imágenes de los sitios**, de Wikipedia/Wikidata para lo turístico y vista de
  satélite para los negocios. Las fotos de negocios están pendientes de decidir
  su fuente (ver [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md), punto 14).
- **Pantalla de carga de primer arranque**, como la de un videojuego, mientras se
  descomprimen la voz y los modelos (solo en la APK de Android).

## APK para Android (`app-demo-release.apk`)

La versión completa, con voz, reconocimiento y LLM dentro. Es la que se ha
probado.

### Requisitos

Los del equipo en que se ha probado, un **Motorola moto g75 5G**:

| | Requisito |
|---|---|
| Sistema | Android 16 (como el moto g75). Arranca desde Android 8.1, pero solo se ha probado en Android 16 |
| Procesador | 64 bits ARM (**arm64‑v8a**). No funciona en procesadores de 32 bits ni en x86 |
| Memoria | **8 GB de RAM**, como el moto g75. El LLM (Gemma 3 1B) necesita varios GB libres al cargar |
| Almacenamiento | **3 GB libres**: el APK ocupa ~750 MB, y la primera vez descomprime voces y modelos en ~1,5 GB más |
| Conexión | Internet, para el mapa, el catálogo de sitios y las imágenes |
| Sensores | **GPS** y **micrófono**, con los permisos concedidos |
| Navegador | Google Maps o Waze instalado, para recibir la ruta del itinerario |

### Instalación

1. Descargar `app-demo-release.apk` de la carpeta de Drive.
2. Permitir la instalación de apps de origen desconocido para el navegador o el
   gestor de archivos que se use.
3. Abrir el APK e instalar. Está firmado con una clave de pruebas; no hace falta
   tienda.
4. La primera vez, la pantalla de carga tarda unos minutos mientras descomprime
   voces y modelos. Solo ocurre una vez.
5. Conceder ubicación y micrófono.

## APK para el emulador de Pleos (`app-pleos-release.apk`)

La misma app, sin nada propio de voz ni de IA:

| Pieza | De dónde sale |
|---|---|
| Escuchar | El reconocedor de la plataforma (`ai.umos.ambientai`) |
| Hablar | La síntesis de voz de la caas de Pleos |
| LLM | El LLM on-device de la caas, solo para lo que la gramática no entiende |
| Mapa | Mapbox (necesita internet en el emulador) |

Pleos no publica todavía las librerías de estos servicios. Los clientes se
hicieron a mano con el contrato leído de la caas 4.2.9 de la imagen Connect‑W:
pueden romperse con otra versión.

**Sin probar en Connect‑W.** En el emulador v2.1.5 se comprobó que instala,
recibe los permisos de Pleos y encuentra los servicios, pero el emulador iba
tan lento que no se pudo ver hablar ni escuchar.

Cómo instalarla, qué comprobar y qué registros enviar:
[PLEOS-TEST.md](PLEOS-TEST.md).

## Pendiente

- **Probar la APK de Pleos en Connect‑W**: confirmar si su voz habla en español
  o italiano y si el reconocedor responde.
- **Las librerías oficiales de Pleos** (NaviHelper, `sdk.tts`, `sdk.stt` y el
  cliente del LLM) y las credenciales de `registerApp`, para sustituir los
  clientes hechos a mano.
- **El origen de las fotos de negocios** (Google Places o Mapillary), pendiente de
  autorización.
