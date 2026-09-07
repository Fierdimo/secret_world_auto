# Superficie visual — primera versión

Capturas del 2026‑09‑03 sobre el emulador `Pleos_Connect_v2.1.5`, perfil
Connect‑W (3840 × 1440 a 160 dpi), en ventana de dos apps.

| | |
|---|---|
| [02-conduciendo.png](02-conduciendo.png) | Superficie en marcha (SPEC §4.2) |
| [01-aparcado.png](01-aparcado.png) | Superficie con el vehículo detenido (SPEC §4.3) |

## Tres cosas que solo se descubren mirando la pantalla

### 1. El emulador estaba bloqueado por un muro de registro

Al hacer la primera captura no salía la app sino esto:

> *This target board does not have a registered CRN. For security reasons, any
> target board without a CRN cannot be used. For instructions on CRN
> registration, please visit the Pleos Playground website.*

Lo impone `ai.umos.setupwizard`, que se pone encima de todo. Todo el trabajo
anterior —sonda, TTS, bucle de narración— se hizo por `logcat` y por eso no había
aparecido: **nunca habíamos mirado la pantalla**.

Apaño de desarrollo, suficiente para trabajar:

```bash
adb shell pm disable-user --user 10 ai.umos.setupwizard
```

Lo correcto es **registrar el CRN en Pleos Playground**. Mientras no se haga, la
pantalla del emulador no es usable de forma normal.

### 2. `1 dp = 1 px`, y eso rompe la intuición de móvil

El perfil declara **160 dpi con 3840 px de ancho**. A esa densidad un título de
32 sp ocupa el 2 % del alto de pantalla: ilegible de reojo y en movimiento.

La primera escala, dimensionada con criterio de móvil, se veía diminuta. Hubo que
subirla en torno a **×1,7**: título 52 sp, cuerpo 36 sp, objetivo táctil 120 dp
—frente a los 48 dp de Material y los 76 recomendados en automoción—. Los valores
están en `Dimens` y `TextSizes` con el porqué escrito al lado.

### 3. El render por software va al límite

Los frames tardan **1,5–2,7 s** (`Davey! duration=...`), con avisos de
`EGL_SWAP_BEHAVIOR_PRESERVED` y `Unable to match the desired swap behavior`. Una
captura tomada demasiado pronto sale **en blanco** aunque la UI esté perfectamente
compuesta.

Truco útil para verificar la UI sin depender del pintado: el árbol de
accesibilidad sí es fiable e instantáneo.

```bash
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml | tr '<' '\n<' | grep -oE 'text="[^"]+"'
```

Eso confirmó que la composición era correcta cuando la captura salía blanca.

## Lo que se ve en las capturas

**En marcha**: lista corta con el límite de seis aplicado en el código, distancia
real calculada por `TourQueue` desde la posición del vehículo, y **dos acciones y
solo dos** por lugar. Al avanzar el simulado, las distancias y el orden cambian.

**Detenido**: título, hueco del mapa —que dice honestamente que la licencia está
sin decidir en vez de fingir un mapa—, las dos acciones y el texto completo con
scroll libre.

Abajo, en gris, los controles del simulador. No van al producto: existen para
poder recorrer un trayecto entero sin moverse de la mesa.
