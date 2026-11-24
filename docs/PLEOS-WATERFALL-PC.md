# Connect Waterfall en un PC, y lo que dice la documentación oficial

24 de noviembre de 2026

Se probó la imagen **Connect Waterfall** (x86_64) en un PC con Linux para saber si
trae los servicios que hacen falta para seguir desarrollando, y se contrastó con
la documentación oficial de Pleos. Este documento recoge lo medido, lo que solo
se ha leído y lo que falta, para continuar en el Mac.

## Conclusión

- **Waterfall arranca y la app funciona por tacto**: mapa, lista, fichas, lugar
  seleccionado y ruta propia.
- **No sirve para voz, LLM ni traspaso al navegador** tal como viene: faltan los
  modelos de voz, el LLM está desactivado y el navegador está oculto y no carga.
- **No parece el emulador que Pleos publica para desarrollar.** El oficial es
  **Pleos Connect Emulator v2.1.0** (19 de octubre de 2026). La documentación
  leída no menciona Waterfall.
- **Siguiente paso: probar el emulador oficial v2.1.0 en el Mac** (imagen arm64).

## Entorno de la prueba

| | |
|---|---|
| Equipo | i3-6100U (2 núcleos, 4 hilos), 12 GB de RAM visibles, HD 520, SSD |
| Sistema | Xubuntu 26.04.1 LTS, KVM |
| Emulador | 37.3.10 |
| Imagen | `POIP_EMULATOR_33_2685.JG.CW.SYS.KOR.033.005.260810.030000-x86_64` |
| App | `0.1.0-pleos`, commit `fe6356e`, compilada en el Mac |

El arranque (`Boot completed`) tarda 54–73 s, con la gráfica del PC. La pantalla
efectiva es de **3840×850**; con el AVD a 3840×1440 el resto queda en negro.

## Cómo arrancarlo

La imagen se copia en `~/Android/Sdk/system-images/android-34/pleos_connect_w/x86_64/`
y el AVD se crea como en [PLEOS-TEST.md](PLEOS-TEST.md), con estos cambios:
`hw.lcd.width=3840`, `hw.lcd.height=850`, `hw.cpu.ncore=4`, `hw.gpu.mode=host` y
`fastboot.forceColdBoot=yes`.

La **wifi no arranca** sola: SELinux impide que el `wpa_supplicant` de la imagen
registre un servicio de su fabricante. Se arregla con SELinux permisivo **después
del arranque**. Arrancar con `-selinux permissive` desde el principio coincidió
con un sistema atascado (63 procesos colgados en 7 min, frente a 17 en 3,5 min
sin la opción), aunque en la prueba buena tampoco se reabrió la app durante el
arranque.

```bash
SDK="$HOME/Android/Sdk"; ADB="$SDK/platform-tools/adb"
"$SDK/emulator/emulator" -avd Pleos_Connect_W -gpu host -allow-host-audio -no-snapshot-load &
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
"$ADB" root; sleep 2; "$ADB" wait-for-device
"$ADB" shell setenforce 0
"$ADB" shell cmd wifi set-wifi-enabled enabled
"$ADB" emu geo fix -75.5514 10.4236
```

`-allow-host-audio` da al emulador el micrófono del PC; `-no-audio` lo apaga.
Conviene esperar a que aparezca el inicio de Pleos antes de abrir la app.

## Lo que se midió

| Servicio | Resultado | Detalle |
|---|---|---|
| Micrófono | **Funciona** | Captura desde el PC dentro del emulador, con sonido |
| Salida de audio | El emulador envía audio al PC | No se comprobó de oído |
| Voz on-device (caas 4.2.9) | **Falla** en inglés | `ON_DEVICE_ENGINE_ERROR`: no existe `/mnt/product/vr/image/airspeech/TextToSpeechConfig.dat`. Español e italiano, sin probar |
| LLM on-device | **No contesta** | `startLLM ignored: on-device LLM is disabled` |
| Reconocedor de voz | Disponible solo si se muestra el asistente | Arranca en modo servidor. Sin probar |
| Servicio de navegación | Presente (`ai.pleos.playground.service.navi` 2.0.1.3) | — |
| Navegador `ai.umos.maps.android.navigation.app` | **No está** | Es el único que busca `PleosNavigation.kt`; por eso la app usa su propia ruta |
| Navegador `com.autoever.navi` | Presente, oculto y desactivado | Mostrado a mano, se conecta al servicio de navegación, pero su motor falla (`Navigation Engine Loading Failed`) y la pantalla queda en blanco. Causa desconocida |
| Asistente `ai.umos.ambientai` 3.5.11 | Presente, oculto y desactivado | Mostrado a mano, arranca. Espera la red del coche (`OEM_CCS`) y no detecta la wifi |
| Encendido (`IGNITION_STATE`) | **OFF**, no se pudo cambiar | `inject-vhal-event` no tiene efecto y `set-property-value` lo rechaza |
| Virtual Console | **No responde** | Nada escucha en el puerto 8088 |

### Apps ocultas

El sistema las oculta en cada arranque. Coincide con la configuración del coche
virtual (`persist.product.hmg.property.type_navi=OFF`, `ro.product.hmg.eol.navi=OFF`),
pero no se ha probado que sea la causa. Para mostrarlas hasta el reinicio:

```bash
adb shell pm unhide --user 10 ai.umos.ambientai
adb shell pm enable --user 10 ai.umos.ambientai
```

### Encendido

El servicio del vehículo es propio de esta imagen. Dar permiso de lectura sobre
`/dev/input/event1` le permite abrir el teclado, pero unas 70 teclas no
encendieron el coche. Los textos del binario indican que el encendido llega en
señales del microcontrolador (`SocBootInfo`, `EventCarIFStatus`); no se encontró
nada en la imagen que las envíe. Que el encendido active navegación o voz no está
demostrado.

### Formato de 850 px

Con un lugar seleccionado y la ruta abierta, en el panel grande caben dos lugares
de la lista. En el hueco pequeño (unos 800×810) solo se ve uno. Conviene revisarlo.

## Lo que dice la documentación oficial

Leído en `document.pleos.ai` el 24 de noviembre, con un lector automático: se ha
resumido, no copiado. Lo que no aparece en las páginas leídas se indica abajo.

| Tema | Documentación | Consecuencia |
|---|---|---|
| Emulador | Pleos Connect Emulator **v2.1.0**, API 34, 4 GB, arranque en frío | Es el que hay que usar |
| Simular el vehículo | **Virtual Console** (desde la v2.1.0): marcha, freno, puertas, energía, clima… No lista encendido | `adb forward tcp:8088 tcp:8088` y abrir `http://127.0.0.1:8088/` |
| Maven | `https://nexus-playground.pleos.ai/repository/maven-releases/` | **Comprobado sin credenciales**: existen `ai.pleos.playground:NaviHelper:3.0.5` y `TextToSpeech:3.3.1`. `Vehicle:2.3.1` (la versión de la guía) dio **404**. `SpeechToText:3.3.1` sin comprobar |
| NaviHelper | «Ready to Use»; `requestRoute` pide `NAVI_ROUTE_SEARCH` | Puede sustituir al cliente manual. La app ya declara ese permiso |
| TTS y STT | «Not Available Yet» en el alta; modos `HYBRID`, `SERVER` y `ON_DEVICE` | Los que usan servidor piden **Client ID y Client Secret** de *My Project*; `ON_DEVICE` solo, no |
| `ON_DEVICE_ENGINE_ERROR` | Recomienda revisar el motor o usar `HYBRID`/`SERVER` | En Waterfall ya se sabe que falta el modelo |
| Idiomas (TTS y STT) | `ko-KR`, `en-US`, `fr-CA`, `es-US`, `es-MX` | **Sin italiano** |
| LLM | SDK **retirado** en la v2.1.0 | El cliente LLM manual no tiene continuidad. Alternativa: **Gleo Interface**, en la que Gleo interpreta la orden y llama a funciones declaradas por la app |
| `minSdk` | 28 o superior | El proyecto usa **27** |
| Permisos | Declarar uno no aprobado hace fallar la revisión de la app | Revisar `CAR_INFO`, `CAR_DRIVE`, `LLM_SERVICE` |

No aparecía: Waterfall, qué ocurre sin app de navegación, si TTS/STT funcionan en
el emulador, ni su encendido. La página de configuración del emulador que cita el
buscador ya no existe. Tampoco se sabe cómo se relaciona la **v2.1.5** instalada
en el Mac con la **v2.1.0** publicada.

### Páginas

- [Getting Started](https://document.pleos.ai/en/docs/pleos-only/getting-started)
- [Instalar el emulador](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Virtual Console: instalación](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator) y [guía](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)
- [Notas del emulador](https://document.pleos.ai/en/docs/pleos-only/release-note/emulator-release-note) y [del SDK](https://document.pleos.ai/en/docs/pleos-only/release-note/sdk-release-note)
- [Proyecto y acceso a los SDK](https://document.pleos.ai/en/docs/pleos-only/create-my-project)
- [NaviHelper](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [TextToSpeech](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [SpeechToText](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)
- [Gleo Interface](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts)

## Pasos siguientes, en el Mac

1. **Instalar el emulador oficial v2.1.0.** En Android Studio, *SDK Manager ›
   SDK Update Sites*, añadir
   `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   e instalar la imagen para API 34. Crear un dispositivo Automotive con arranque
   en frío y 4 GB, con **otro nombre**: la guía pide quitar los emuladores Pleos
   anteriores, pero el v2.1.5 es el de los vídeos de la demo.
2. **Probar en él** voz, escucha, navegación y Virtual Console, sin compilar a la vez.
3. **Comprobar el resto del Maven** (`SpeechToText`, y qué versiones hay de `Vehicle`).
4. **Sustituir los clientes manuales** de navegación y voz por los SDK oficiales y
   subir `minSdk` a 28.
5. **Decidir** qué hacer con el italiano y con el LLM (Gleo Interface o modelo propio).
6. **Preguntar a Pleos** lo que no está en la documentación: si Waterfall se puede
   usar para desarrollar, sus modelos de voz, cómo se enciende el coche y qué
   necesita `com.autoever.navi`.
7. **Actualizar** [PLEOS-SDK-SURFACE.md](PLEOS-SDK-SURFACE.md),
   [PLEOS-TEST.md](PLEOS-TEST.md), [AVANCE-ITINERARIOS.md](AVANCE-ITINERARIOS.md) y
   [NAVIGATION-AND-MAP-DECISIONS.md](NAVIGATION-AND-MAP-DECISIONS.md): varios dicen
   que las librerías no están publicadas.

## El PC

Se cortó varias veces el 23 y el 24 de noviembre, sin apagado ordenado ni errores
en los registros. La batería está dañada y podría explicar al menos algunos
cortes. En la última sesión el PC aguantó más de una hora, con el emulador abierto
buena parte del tiempo, sin cortes y con un máximo de 71 °C, pero no basta para
darlo por resuelto. Quedan pendientes la batería o el cargador y una pasada de
memtest86+, porque la BIOS anuncia 16 GB y Linux ve 12. **No conviene compilar en
este PC.**
