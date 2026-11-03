# PLAN — arquitectura e implementación

Cómo se construye lo descrito en [SPEC.md](SPEC.md). Versión 0.2.

> Cambios respecto a 0.1: se adopta el marco de **Automotive Core** propuesto por
> Antonio. Se corrige el reparto cliente/servidor (la sincronización y la caché
> son del cliente). Se incorpora la estrategia de tres plataformas
> ([docs/PLATFORM-STRATEGY.md](docs/PLATFORM-STRATEGY.md)) y los datos reales del
> SDK de Pleos.

---

## 1. La decisión de fondo: nativo, no React Native

**Se construye en Kotlin nativo sobre AAOS. No se reutiliza el código de la app
React Native de Secret World.**

1. **Los SDK de Pleos son Android.** Los seis módulos (Vehicle, NaviHelper,
   TextToSpeech, SpeechToText, ADAS, Fused Location) se consumen desde Kotlin.
2. **La UI en marcha tiene reglas.** Puede ser nuestra, pero simplificada, y la
   superficie de audio se integra con el centro multimedia del vehículo.
3. **Casi nada del código actual sobreviviría.** Firebase, Google Sign-In,
   RevenueCat, Play Billing, AppLovin, AppsFlyer, `expo-notifications`, WebViews.
   Ninguno aplica en el coche.
4. **Presupuesto de arranque.** RNF-1 pide primera narración posible en < 3 s.

Lo reutilizable es **el backend, el catálogo y la cuenta**, no la capa cliente.
Ver [docs/BACKEND-REUSE.md](docs/BACKEND-REUSE.md).

## 2. Automotive Core: una fundación, tres clientes

PLEOS no se construye como producto aislado, sino como **primer cliente de una
base reutilizable**. Lo que se paga una vez:

- El **backend** de Secret World, sin cambios para la v1.
- El **catálogo reescrito en formato conversacional** (60 a 90 s por lugar).
- El **sistema de cuentas** y la vinculación por código o QR.
- El **módulo de dominio en Kotlin**, sin una sola dependencia de Pleos.

Sobre esa base: PLEOS ahora, Android Auto después reutilizando el módulo Kotlin
casi entero, y CarPlay como cliente Swift nuevo sobre backend y contenido ya
pagados. El orden y sus razones están en
[docs/PLATFORM-STRATEGY.md](docs/PLATFORM-STRATEGY.md).

> **Regla no negociable:** `:core:domain` no importa nada de Pleos. En cuanto una
> llamada a su SDK se filtra en la lógica de negocio, Android Auto deja de ser
> barato y pasa a ser una reescritura.

## 3. Arquitectura por capas

```
┌───────────────────────────────────────────────────────────────┐
│  Superficies                                                  │
│  ┌──────────────┐  ┌────────────────┐  ┌───────────────────┐  │
│  │ Voz + media  │  │ Pantalla en    │  │ Pantalla detenido │  │
│  │ (siempre)    │  │ marcha (simple)│  │ (Compose + Pleos) │  │
│  └──────┬───────┘  └───────┬────────┘  └─────────┬─────────┘  │
├─────────┴──────────────────┴─────────────────────┴────────────┤
│  :core:domain — Kotlin puro, CERO dependencias de Pleos       │
│  TourQueue · DrivingMode · Entitlements · Language · Intents   │
├───────────────────────────────────────────────────────────────┤
│  :core:data                                                   │
│  SecretWorldApi (Retrofit) · Room · caché de audio · DataStore│
├───────────────────────────────────────────────────────────────┤
│  :core:platform — la ÚNICA capa que conoce Pleos              │
│  Vehicle · NaviHelper · TextToSpeech · SpeechToText           │
│  Fused Location · (ADAS) · Payments                           │
└───────────────────────────────────────────────────────────────┘
```

### Módulos Gradle

| Módulo | Contenido | Conoce Pleos | Reutilizable en Android Auto |
|---|---|---|---|
| `:core:domain` | Modelos, casos de uso, cola, intenciones | **No** | Sí, entero |
| `:core:data` | Retrofit, Room, DataStore, caché | No | Sí, entero |
| `:core:platform` | Wrappers sobre los SDK de Pleos | Sí | No |
| `:core:designsystem` | Tokens y componentes según su design system | Sí | Parcial |
| `:feature:voice` | Servicio de media, narración, bucle de intenciones | No directamente | Sí, mayormente |
| `:feature:discover` | Superficie de POIs en marcha | Sí | No |
| `:feature:parked` | Mapa, detalle, itinerarios, cuenta | Sí | No |
| `:app` | Ensamblado, manifest automotive, DI | Sí | No |

`:core:platform` existe para dos cosas: aislar a Pleos y ser **falsificable**.
Sin un doble de Vehicle SDK y Fused Location no hay desarrollo posible sin coche.

## 4. Reparto cliente/servidor

Es el punto que más se malinterpreta, así que queda explícito.

| Responsabilidad | Dónde | Por qué |
|---|---|---|
| Seleccionar lugares candidatos por zona o ruta | Servidor | Es una consulta al catálogo |
| **Decidir el instante exacto de narrar** | **Cliente** | Depende de velocidad y posición al segundo. Un round-trip narra sitios ya pasados |
| Caché de contenido y audio | **Cliente** | Tiene que funcionar en un túnel |
| Reconocer voz y despachar intenciones | **Cliente** | Latencia (RNF-3: < 1,5 s) y cobertura irregular |
| Servir el contenido de "cuéntame más" | Servidor | Es contenido, no interacción |
| Estado de suscripción | Servidor | Fuente de verdad única entre móvil y coche |

## 5. Componentes clave

### 5.1 `TourQueue`
Decide qué se escucha a continuación. Entradas: posición, rumbo y velocidad del
vehículo, ruta activa si existe, catálogo cacheado, historial e idioma. Salida:
cola ordenada que se reevalúa de forma continua.

Reglas: no repetir en la sesión, preferir lo que está por delante, no encolar sin
contenido descargado, y **silencio antes que relleno**.

### 5.2 `NarrationTrigger`
~~Calcula el momento de disparo a partir de velocidad y distancia.~~ **Sin uso en
el producto desde el 2026‑11‑03**: la narración en marcha la pide siempre el
usuario y no hay disparo automático (SPEC §2, RF‑3.3). Sigue en el dominio solo
porque lo usa la prueba de [NarrationLoopActivity](feature/voice/src/main/kotlin/com/secretworld/auto/feature/voice/NarrationLoopActivity.kt),
que mide si el audio está preparado a tiempo. La velocidad y la distancia siguen
sirviendo para **ordenar** lo que se ofrece y **preparar** el audio por delante.

### 5.3 `DrivingModeController`
Única fuente de verdad sobre qué superficie puede estar visible. Escucha Vehicle
SDK y emite `Parked | Driving`. Ninguna pantalla decide por su cuenta.

### 5.4 `IntentRouter`
Traduce lo que llega de SpeechToText en intenciones de dominio (`TellMeMore`,
`TakeMeThere`, `Skip`, `WhatsNearby`, `Save`) y garantiza que **toda intención
tiene respuesta hablada**. Vive en `:core:domain`; el reconocimiento en
`:core:platform`.

### 5.5 `AudioPrefetcher` y `EntitlementResolver`
Descarga anticipada con presupuesto de disco y política LRU. Estado de
suscripción consultado al servidor con TTL corto, nunca decidido en cliente.

## 6. Backend

**La v1 no requiere cambios**, bajo el modelo de acceso A de
[docs/MONETIZATION.md](docs/MONETIZATION.md): no se vende dentro del coche y el
desbloqueo llega al vincular una cuenta con suscripción activa.

Los endpoints existentes cubren catálogo, contenido, itinerarios y cuenta. Ver
[docs/BACKEND-REUSE.md](docs/BACKEND-REUSE.md) para el detalle y para las
optimizaciones posteriores a la v1.

## 7. Riesgos técnicos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| **El STT debe pasar por su asistente Gleo** en vez de por nuestro SDK | Alto | Verificar en el primer spike. Si es así, el vocabulario queda limitado a lo que expongan y el producto cambia |
| El TTS del vehículo no cubre nuestros idiomas o suena mal | Alto | Spike de calidad temprano. Alternativa: audio pre-generado en CDN |
| Su política de tienda exige pasar por su pasarela | Alto | Consulta a Pleos antes de nada. Cambia el modelo de acceso y sí tocaría backend |
| Permisos no aprobados | Alto | Lista cerrada y pre-aprobada en fase 0. Undeclared = rechazo |
| Licencia de mapas embebida | Medio | El mapa solo hace falta en modo detenido. Evaluar el nativo del vehículo antes que embeber |
| Audio cortado al abrirse otra app en pantalla partida | Medio | Verificar en spike: su ventana muestra una o dos apps |
| Fragmentación de pantallas | Medio | Diseño responsive con breakpoints para Connect-S, W y WF, en landscape y portrait |
| Densidad de catálogo en carretera | Medio | Medir kilómetros con cobertura antes de prometer acompañamiento continuo |

## 8. Entorno

Android Studio, **API 28 mínimo**, **Gradle 8.0+**, Kotlin. Emulador de Pleos
Connect en **cold boot** (VHAL no funciona con quick boot). El `config.ini` que
entrega el fabricante con la imagen arm64 ya trae `fastboot.forceColdBoot = yes`,
así que no hay nada que ajustar.

El emulador arm64 de Pleos Connect (AAOS 14, API 34, vendor 42dot) funciona en
local desde el 2026‑09‑03. Detalle de instalación, perfiles de pantalla e
inventario de paquetes del sistema en [SETUP.md](SETUP.md).

CI: build de APK y tests de `:core:domain`, que al ser Kotlin puro se prueba sin
dispositivo. Repositorio propio; el único punto de contacto con `secret_world`
es el contrato del API.
