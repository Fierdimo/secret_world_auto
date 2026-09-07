# La plataforma Pleos — lo que sabemos y lo que falta

Recopilado en septiembre de 2026 a partir de fuentes públicas. **Todo lo marcado
como "por confirmar" requiere acceso real a Pleos Playground** y es lo que
resuelve la fase 0 del roadmap.

## Qué es

Pleos es la marca de software de Hyundai Motor Group, presentada en 2025.
**Pleos Connect** es el sistema de infotainment, basado en **Android Automotive
OS (AAOS)**.

- Debut en vehículo: **Q2 2026**.
- **Pleos App Market** preinstalado de forma gradual en Hyundai, Kia y Genesis desde 2026.
- Objetivo declarado: más de **20 millones de vehículos a 2030**.

## Qué ofrece a terceros

**Pleos Playground** es la plataforma de desarrollo abierta:

| Recurso | Para qué |
|---|---|
| SDK de AAOS + herramientas base | Desarrollo Android estándar para automoción |
| **Vehicle SDK** | Estado y control del vehículo, compatible con la API de Android |
| **NaviHelper SDK** | Integración con navegación: fijar destino, gestión de ruta |
| **Gleo AI SDK** | Reconocimiento de voz y comandos en la nube |
| Fleet API · Vehicle Data API | Datos de flota y vehículo |
| Devbox y emulador | Desarrollo y prueba sin vehículo físico |
| Design system, guías de UI y estilo | Conformidad visual con Pleos Connect |
| Guías de envío y revisión de apps | Requisitos para publicar |
| Plug & Play (PnP) | Conexión directa de hardware externo al vehículo |

Monetización soportada en el App Market: **pagos de contenido digital y
suscripciones**, integración con servicios de pago del vehículo (carga,
aparcamiento, combustible) y **publicidad adaptada al entorno de conducción**.

## Estado del acceso

Actualizado el 2026‑09‑03.

| Recurso | Estado |
|---|---|
| Cuenta de desarrollador en Pleos Playground | **Conseguida** |
| Documentación del portal (guidelines de revisión, design system, App Market, pagos) | Accesible — pendiente de leer y resumir |
| Android Studio + SDK de Android | **Instalado**: Studio 2026.1.3, emulador 37.1.11.0 |
| **Emulador de Pleos Connect (arm64)** | **Funcionando en local**: AAOS 14 / API 34, `arm64-v8a`, tag `android-automotive`, vendor 42dot, arranque en ~30 s |
| SDKs (Vehicle, NaviHelper, Gleo) | Servicios presentes en la imagen; falta confirmar cómo se distribuyen las librerías cliente |

La primera entrega del emulador fue una imagen **x86_64**, inservible sobre Apple
Silicon: el emulador de Android para macOS ARM solo incluye los backends
`qemu-system-aarch64` y `qemu-system-armel`. Se resolvió pidiendo la build arm64.
Procedimiento de instalación en [SETUP.md](../SETUP.md).

### Lo que trae la imagen

Los tres SDK de los que habla el portal existen como servicios del sistema:
`ai.pleos.playground.service.vehicle` (VehicleService),
`ai.pleos.playground.service.navi` (NaviService) y `ai.pleos.gleoai.model.provider`,
que internamente es un `LLMModelProviderSerivce` — Gleo AI es un proveedor de
modelo LLM, no solo un reconocedor de voz.

Alrededor, la plataforma **uMOS**: `ai.umos.ambientai` (asistente y
`RecognitionService` del sistema), `ai.umos.maps.android.navigation.app`
(navegación nativa), `ai.umos.mediaapp` (centro multimedia), `ai.umos.appmarket`,
`ai.umos.driverprofilemanager` y, notablemente,
`ai.umos.android.android.auto` — Android Auto viene en la imagen.

Inventario completo y lo que implica para cada spike, en
[SETUP.md](../SETUP.md#lo-que-ya-se-ve-en-el-emulador).

### Perfiles de pantalla

De la guía de ejecución de HMG, útiles para dimensionar layouts: **Connect-W**
3840×1440, **Connect-S** 2560×1440 y **Connect-L** 1920×1080, todos a densidad
160. El AVD arm64 arranca con el perfil Connect‑W y una sola pantalla activa.

## Lo que impone (y condiciona todo el diseño)

Al ser AAOS, aplican las restricciones de distracción del conductor del sistema:
con el vehículo en movimiento solo se permite UI *distraction‑optimized* o
categorías de app homologadas (media, navegación/POI, mensajería). Una UI libre
—la de una app móvil típica— **no pasa revisión para uso en marcha**.

Consecuencias directas sobre este proyecto, todas ya incorporadas al SPEC:

- El audio es la única superficie con garantía de estar disponible siempre.
- El descubrimiento visual debe ir por plantillas homologadas.
- Todo lo rico va al modo detenido.
- Sin entrada de texto en marcha → el login es por código/QR, no por teclado.
- Pantalla landscape ancha, no portrait.

## Por confirmar en fase 0

1. Categorías de app exactas que admite el App Market y a cuál nos acogemos.
2. Qué plantillas homologadas existen para listados de POI y con qué límites.
3. Nivel de API de AAOS mínimo y modelo de compatibilidad hacia adelante.
4. ~~Requisitos de alta como desarrollador.~~ **Resuelto**: cuenta activa desde 2026‑09‑01. Queda por confirmar cómo se obtienen emulador y SDKs una vez dado de alta.
5. Modelo de comisiones y países soportados por la pasarela de pagos.
6. Si hay acceso al motor de mapas nativo del vehículo o hay que embeber uno.
7. Política de OTA/actualizaciones: cadencia y proceso de revisión de cada versión.
8. Si existe algún soporte de webview/web app y qué se permite hacer con él.

## Fuentes

- [Hyundai Motor Group — lanzamiento de Pleos](https://www.hyundaimotorgroup.com/en/news/hyundai-motor-group-launches-pleos-software-brand-unveiling-new-sdv-technologies-and-collaborations)
- [Pleos Playground](https://pleos.ai/playground)
- [Pleos App Market](https://pleos.ai/playground/discover/app-market)
- [S&P Global — análisis del lanzamiento](https://www.spglobal.com/automotive-insights/en/blogs/2025/04/hyundai-pleos-software-launch-shifts-mobility-game)
