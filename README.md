# Secret World Auto — app para PLEOS (Hyundai / Kia / Genesis)

Proyecto hermano de [`secret_world`](../secret_world). Aplicación **nativa Kotlin
sobre Android Automotive (AAOS)** para el ecosistema **Pleos Connect**,
construida sobre el contenido, el backend y la marca de Secret World, pero como
**producto nuevo**, no como port de la app móvil.

> **Estado: MVP demostrativo funcionando.** Catálogo **real** de Secret World por
> posición, narración por voz sintetizada en el dispositivo, y una interfaz de
> automoción con el **mapa como lienzo permanente** — Mapbox en el banco de
> pruebas, mapa del dispositivo en el vehículo. Cuatro pantallas: home, detalle,
> itinerario y ajustes. Capturas en [spikes/ui/](spikes/ui/).
>
> Sigue sin ser producto: el contenido no está reescrito para ser oído, "Llévame
> ahí" espera la librería de Pleos, y no hay cuenta vinculada. Ver
> [ROADMAP.md § Dónde estamos de verdad](ROADMAP.md#dónde-estamos-de-verdad).

## El producto en una frase

Un acompañante que te cuenta dónde estás mientras conduces, te responde cuando le
preguntas y te orienta hacia lo que merece la pena.

Se construye en **dos niveles** ([SPEC.md §2](SPEC.md)): primero un acompañante
determinista de vocabulario cerrado que funciona sin cobertura —*"cuéntame más"*,
*"llévame ahí"*, *"sáltalo"*, *"¿qué hay cerca?"*— y que es el MVP; después el
asistente conversacional abierto, capaz de responder y recomendar con criterio.

## Documentos

| Documento | Qué contiene |
|---|---|
| [SPEC.md](SPEC.md) | Qué es el producto: alcance, superficies, requisitos, criterios de aceptación |
| [PLAN.md](PLAN.md) | Arquitectura, módulos, reparto cliente/servidor, riesgos técnicos |
| [ROADMAP.md](ROADMAP.md) | Fases, hitos y estimaciones (~5 meses a v1) |
| [SETUP.md](SETUP.md) | **Checklist para arrancar el día que llegue el emulador**, con los cinco spikes |
| [docs/PLEOS-PLATFORM.md](docs/PLEOS-PLATFORM.md) | La plataforma: SDKs, requisitos, restricciones |
| [docs/PLATFORM-STRATEGY.md](docs/PLATFORM-STRATEGY.md) | Por qué PLEOS primero, luego Android Auto, luego CarPlay |
| [docs/BACKEND-REUSE.md](docs/BACKEND-REUSE.md) | Qué se reutiliza del backend actual y por qué la v1 no lo toca |
| [docs/MONETIZATION.md](docs/MONETIZATION.md) | Los tres modelos de monetización y cuál va en v1 |
| [docs/B2B-STRATEGY.md](docs/B2B-STRATEGY.md) | Cómo llegar a un acuerdo directo con Hyundai Motor Group |
| [docs/MAP-OPTIONS.md](docs/MAP-OPTIONS.md) | **El mapa**: lienzo propio, Mapbox o el nativo del vehículo, y qué bloquea cada uno |
| [docs/DEV-ENVIRONMENT.md](docs/DEV-ENVIRONMENT.md) | **Dónde ejecutar el emulador**: por qué este Mac se queda corto y qué alternativas hay |
| [docs/PLEOS-SDK-SURFACE.md](docs/PLEOS-SDK-SURFACE.md) | **El SDK real, medido**: permisos `pleos.car.permission.*`, servicios vinculables y qué habilita cada uno |
| [docs/VOICE-OPTIONS.md](docs/VOICE-OPTIONS.md) | **La voz**: la plataforma no la pone, qué alternativas hay y cuál se recomienda |
| [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) | Decisiones abiertas, ordenadas por urgencia |
| [spikes/probe/](spikes/probe/) | Sonda de alcance: qué ofrece la plataforma de verdad, medido |

## Decisiones ya tomadas

1. **Kotlin nativo, no React Native.** Los SDK del vehículo son Android y la UI
   en marcha tiene reglas que RN no puede cumplir.
2. **Automotive Core.** PLEOS es el primer cliente de una fundación reutilizable:
   backend, contenido y un módulo de dominio Kotlin sin dependencias de Pleos.
3. **Orden de plataformas**: PLEOS, luego Android Auto (casi gratis), luego
   CarPlay (cliente Swift nuevo).
4. **El backend no cambia en la v1**, salvo que Pleos obligue a vender por su
   pasarela.
5. **Monetización v1**: no se vende dentro del coche. El desbloqueo llega al
   vincular una cuenta con suscripción activa.
6. **La voz del MVP es un motor TTS embebido**, en inglés e italiano. Elimina el
   pipeline de audio pre-generado y da capacidad de decir texto arbitrario desde
   el día uno. Ver [docs/VOICE-OPTIONS.md](docs/VOICE-OPTIONS.md).

## Lo siguiente

Ejecutar los spikes de [SETUP.md](SETUP.md), empezando por el 1 y el 2, y montar
el esqueleto de módulos. Las dos preguntas que pueden cambiar el producto siguen
siendo si podemos usar SpeechToText con vocabulario propio y si su política
permite comprar fuera del coche.

## No-objetivos

No es un port de la app React Native. No reemplaza a la app móvil, la
complementa. No cubre iOS, CarPlay ni Android Auto en esta v1.
