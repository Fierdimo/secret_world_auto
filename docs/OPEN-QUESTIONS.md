# Decisiones abiertas y riesgos

Versión 0.2. Ordenado por urgencia.

> El alta en Pleos Playground **ya está resuelta**, el emulador arm64 funciona y
> una app sonda ya ha medido la plataforma: ver
> [spikes/probe/RESULTS.md](../spikes/probe/RESULTS.md). Varias de las preguntas
> de abajo tienen ya media respuesta.

## Bloqueantes de fase 0

| # | Pregunta | Por qué bloquea | Cómo se resuelve |
|---|---|---|---|
| 1 | **¿Admite `SpeechRecognizer` vocabulario propio, y con qué latencia?** | *Medido: la API está disponible y la sirve `ai.umos.ambientai`; se puede invocar desde nuestra app sin pasar por el asistente. Falta la calidad.* **Y no hay reconocimiento en dispositivo: la voz depende de red**, lo que choca con RNF‑3 en túnel | Spike 1, con micrófono |
| 1b | **¿Cómo se obtiene la librería cliente de navegación?** | **Único bloqueante técnico de la v1.** `NaviService` vincula y devuelve binder vivo con interfaz `ai.pleos.playground.navi.INaviService`, pero el contrato AIDL no es público: se puede conectar y no entenderse. Reconstruirlo está descartado — APK ofuscado y cliente frágil | Consulta a Pleos, máxima prioridad |
| 2 | **¿Permite su política desbloquear contenido comprado fuera del coche?** | Decide todo el modelo de acceso. Si lo prohíbe, hay que integrar su pasarela y **sí habría cambios de backend** | Consulta a Pleos |
| 2b | **¿Cuándo se publican los servicios de Gleo y cuánto tarda la aprobación?** | *Medido: no es una puerta de permisos —se conceden solos al instalar— sino que `SpeechService` y `LLMService` **devuelven binder nulo**. El código no está.* El nivel 2 entero depende de ellos; la v1 no. Sin fecha no se puede planificar el asistente | Solicitud + consulta, ya |
| 2c | ~~¿Aguanta un TTS embebido?~~ **Verde en hardware real**: RTF **0,326** en un móvil de gama media, y suena. Queda la escucha crítica de cuatro minutos y la medida en el vehículo | Lo que decidía el MVP ya no bloquea | [spikes/voice/RESULTS.md](../spikes/voice/RESULTS.md) |
| 2h | ~~¿Dónde se prueba el audio?~~ **Resuelto**: el sabor `bench` se instala en cualquier Android y ahí se oye. El emulador sigue sin salida de audio en este Mac | — | [DEV-ENVIRONMENT.md](DEV-ENVIRONMENT.md) |
| 2d | **¿Qué voz italiana podemos publicar?** | *Verificado y es un problema real: `lessac` es licencia nominal de Blizzard, `ryan` es CC BY-**NC**, y `paola` no declara licencia y está afinada sobre lessac. **El inglés está resuelto con `ljspeech`, dominio público; el italiano no tiene ninguna voz limpia**.* Bloquea publicar en italiano, no la v1 en inglés | Diligencia legal, urgente |
| 2e | **¿Permite el App Market descargar modelos tras la instalación?** | *Medido: con voces dentro el APK pesa 169 MB, sin ellas 34. Empaquetarlas no es viable, así que la v1 depende de poder descargar tras instalar. **La descarga ya está implementada y probada de extremo a extremo**; lo que falta es su permiso* | Consulta a Pleos |
| 2f | **¿Qué hace la app si en el primer arranque no hay cobertura?** | Un coche puede entregarse y encenderse por primera vez en un garaje sin datos. Sin voz instalada no hay narración. La regla escrita es decirlo y reintentar (RF‑1.8.5), pero conviene decidir si además se ofrece algo útil mientras tanto | Producto |
| 2g | **¿Se permite descargar una voz con el vehículo en marcha?** | 21 MB por una conexión de vehículo, y una pantalla de progreso en marcha. Puede que la respuesta sea solo con el coche detenido | Producto + guidelines |
| 3 | ¿Qué calidad y qué idiomas da el **`TTS_SERVICE` de Pleos**? | No hay motor por la API de Android, pero **sí por su capa** (permiso `normal`). Decide si el nivel 2 puede hablar sin coste nuestro, y si el nivel 1 podría prescindir de pre-generar | Spike 2, cuando haya SDK |
| 4 | ¿Sigue sonando el audio con otra app en primer plano o en pantalla partida? | El producto entero depende de que la narración no se corte | Spike 5 |
| 4b | **¿Se puede activar la app por voz, o el hueco de asistente es exclusivo de `ai.umos.ambientai`?** | Si la única entrada es su asistente, el vocabulario del nivel 2 queda limitado a lo que expongan y el producto cambia de forma. Con pulsación en volante o botón en pantalla el nivel 1 sale igual | Spike 1 + consulta |
| 4c | **¿Qué motor de síntesis dinámica para el nivel 2**: embebido, nube o Gleo? | Perfiles de coste opuestos — tamaño de APK contra coste por reproducción. No bloquea la v1 | [VOICE-OPTIONS.md](VOICE-OPTIONS.md) |
| 5 | Revenue share, países y monedas de su pasarela | Condiciona si la venta in-car merece la pena algún día | Consulta a Pleos |
| 6 | Alcance y plazos de la pre-aprobación de permisos | Un permiso no aprobado es rechazo automático. *Medido: `CAR_SPEED` y `CAR_ENERGY` son de runtime y se conceden solos con consentimiento; `CAR_MILEAGE` **no es concedible** a una app normal. Velocidad sí, odómetro no* | Consulta a Pleos |
| 6b | **¿Cómo se registra el CRN del emulador?** | Sin registrar, `ai.umos.setupwizard` bloquea la pantalla entera y no hay pruebas visuales fiables. Se puede desactivar para desarrollar, pero no es una base sobre la que validar distracción | Consulta a Pleos |
| 7b | **¿Cubre vuestro contrato con Mapbox el uso embarcado en vehículo?** | La app móvil ya usa Mapbox y el SDK funciona en AAOS, pero los términos estándar cubren móvil y web; automoción es acuerdo aparte. Decide si el mapa de la v1 es Mapbox o hay que esperar al nativo. Ver [MAP-OPTIONS.md](MAP-OPTIONS.md) | Consulta a Mapbox |
| 7 | ¿Mapa nativo del vehículo por SDK, o hay que licenciar uno? | Mapbox automotive es contrato aparte y caro. *Medido: existe app de navegación nativa `ai.umos.maps.android.navigation.app`, pero no expone `geo:`* | Consulta + spike |

## Decisiones de producto pendientes

| # | Pregunta | Recomendación provisional |
|---|---|---|
| 8 | ~~Mercados e idiomas de lanzamiento~~ **Decidido**: voz en **inglés e italiano** en la v1 | Encaja con Europa, que es donde el catálogo tiene densidad y el turismo en coche es real. Añadir idiomas después es añadir modelos de voz, no republicar |
| 9 | Métrica del nivel gratuito | Por lugares, no por tiempo: la duración de los trayectos varía demasiado |
| 10 | ¿Publicidad en v1? | No. Primero validar el producto |
| 11 | Nombre en el App Market | Mantener la marca Secret World |
| 12 | ¿Cuenta obligatoria para el nivel gratuito? | No. La fricción de login en un coche es letal |
| 13 | ¿A qué se ata el entitlement? | A la cuenta, no a la instalación del coche. Los coches se comparten y se venden |

## Riesgos abiertos

- **El contenido es la ruta crítica**, y no es código. Reescribir el catálogo en
  segmentos de 60 a 90 segundos es el mayor coste del proyecto.
- **La anticipación separa la magia del ruido.** Narrar algo ya pasado no vale
  nada. *Resuelto en lo técnico: el bucle completo con vehículo simulado no tuvo
  ni una espera. Lo que queda es el arranque en frío — ver
  [spikes/voice/RESULTS.md](../spikes/voice/RESULTS.md).*
- **Densidad del catálogo en carretera.** El catálogo es urbano; hay autopistas
  con decenas de kilómetros sin nada que contar. Medirlo antes de prometer.
- **La voz tiene que aguantar horas.** Un TTS mediocre a los cuatro minutos es
  peor que el silencio.
- **El reconocimiento de voz depende de red.** `isOnDeviceRecognitionAvailable`
  es `false`: en túnel o zona sin cobertura no hay comandos por voz. Decidir si
  la voz es esencial al producto o un extra que se degrada con elegancia.
- **Ventana temporal.** La ventaja de llegar temprano se evapora si esto se
  alarga más allá de 2027.
- **Dependencia de un solo fabricante.** Mitigada por diseño: `:core:domain`
  aislado permite llevar el producto a otros AAOS y a Android Auto con coste
  acotado. Ver [PLATFORM-STRATEGY.md](PLATFORM-STRATEGY.md).
- **Perfil compartido del vehículo.** Varios conductores por coche complica
  favoritos, historial y suscripción. Definir antes de la fase 3. El sistema trae
  un `ai.umos.driverprofilemanager`: mirar qué expone antes de inventar nada.
