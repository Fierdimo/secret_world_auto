# Monetización — investigación de mercado

Versión 0.1. Septiembre 2026.

---

## 1. Qué confirma Pleos públicamente

De su material oficial, el App Market soporta tres vías de ingreso:

1. **Pagos de contenido digital y suscripciones** dentro de la app.
2. **Integración con servicios de pago del vehículo**: carga, aparcamiento y combustible.
3. **Publicidad adaptada al entorno de conducción.**

Además existe **Pleos Account**, un servicio de perfil de usuario con
sincronización, y una **App Market Console** en `appmarket.pleos.ai/console`
donde se gestionan las apps.

## 2. Qué NO es público (y hay que sacar de la Console)

Todo lo que determina si el negocio funciona:

| Incógnita | Por qué importa |
|---|---|
| Nombre y forma del SDK de facturación | Define el trabajo de integración |
| **Revenue share** | Define el margen. Google y Apple cobran 15–30%; si Pleos cobra más, cambia el precio |
| Países y monedas soportados | Nuestro público es turismo europeo |
| Soporte de periodos de prueba y precios promocionales | Herramienta básica de conversión |
| Si admite pagos externos o exige la pasarela propia | Determina si el modelo A de abajo es siquiera legal en su store |
| Validación servidor a servidor de compras | Necesario para unificar entitlements |

**Acción**: son preguntas para la App Market Console (acceso ya concedido) y
para `partnership@pleos.ai`. Es lo primero de la fase 0, antes que cualquier código.

## 3. Qué perdemos exactamente al no tener RevenueCat

Menos de lo que parece. RevenueCat aporta tres cosas y solo una es difícil de sustituir:

| Aporta | ¿Lo perdemos? |
|---|---|
| SDK cliente que abstrae App Store, Play y Huawei | Sí, pero en el coche solo hay una tienda: no hay nada que abstraer |
| Paywalls y experimentos A/B | Sí. En el coche un paywall visual es casi inútil de todas formas |
| **Servidor de entitlements: la verdad sobre quién es premium** | **No, si no vendemos dentro del coche.** Su API REST permite consultar el estado de un suscriptor desde nuestro servidor |

Esa última fila es la que abre el modelo recomendado.

## 4. Modelos de monetización evaluados

### Modelo A · No vender dentro del coche (recomendado para v1)

El coche es una **superficie de valor añadido para suscriptores existentes**, no
un punto de venta. Nivel gratuito limitado; el desbloqueo llega al vincular una
cuenta que ya tiene suscripción activa, comprada en el móvil.

- **Coste de implementación**: prácticamente cero. Sin SDK de facturación, sin
  comisión adicional, sin cambios de backend.
- **Riesgo de plataforma**: nulo. No dependemos de las condiciones de su pasarela.
- **Efecto de negocio**: convierte el coche en argumento de **retención** y de
  diferenciación de la suscripción móvil, que es donde ya sabemos cobrar.
- **Precedente de industria**: es lo que hace casi todo el mundo. Las grandes apps
  de contenido en Android Automotive no venden suscripciones dentro del vehículo;
  reconocen la que el usuario ya tiene. La monetización in-car sigue siendo inmadura.
- **Punto débil**: el conductor que descubre la app en el App Market y no tiene
  la cuenta. Se mitiga con "te enviamos un enlace al móvil" y una prueba generosa.

### Modelo B · Vender dentro del coche con la pasarela de Pleos

Necesario para capturar al usuario que llega desde el App Market sin conocernos.

- **Requiere**: SDK de facturación de Pleos, endpoint nuevo en backend para
  registrar y validar la compra, y unificación de entitlements entre cuatro
  orígenes (App Store, Play, Huawei, Pleos).
- **Es el único punto de todo el proyecto donde el backend sí tendría que cambiar.**
- **Recomendación**: v2, cuando el revenue share y la conversión real sean conocidos.

### Modelo C · B2B con el fabricante

En automoción el dinero grande no está en el usuario final, está en quien fabrica
el coche. Hyundai necesita contenido que diferencie su infotainment del de la
competencia, y ese es exactamente nuestro activo.

Formas posibles: licencia por vehículo, inclusión en el paquete de conectividad
del coche, pago por región, o preinstalación pagada.

- **Ventaja**: no depende de la conversión de usuarios ni de comisiones de store.
- **Es trabajo de negocio, no de desarrollo**, y puede avanzar en paralelo al código.
- **Debería perseguirse desde ya**, porque el argumento más fuerte que tenemos
  ("catálogo geolocalizado en 17 idiomas, listo") es más valioso para Hyundai
  ahora, con el App Market vacío, que dentro de dos años.

### Modelo D · Publicidad y contenido patrocinado

Pleos lo soporta explícitamente. Para un acompañante que habla, el formato
nativo es evidente: *"a dos kilómetros hay un sitio donde comer bien"*.
Anunciantes naturales: restauración, hoteles, turismo local, parking, carga.

- **Riesgo**: erosiona la confianza si no se separa con claridad lo editorial de
  lo pagado. Nuestro producto se sostiene sobre que la recomendación es honesta.
- **Recomendación**: fuera de la v1. Evaluar cuando haya volumen y con separación
  explícita.

### Modelo E · Comisión sobre servicios del vehículo

Pleos integra pagos de carga, aparcamiento y combustible. Un acompañante que
sugiere destinos está a un paso de la reserva: aparcamiento cerca del sitio
recomendado, carga mientras visitas.

- **Recomendación**: v2 o v3. Encaja de forma natural con "llévame ahí" y no
  compite con la suscripción.

## 5. Recomendación

1. **v1 con el modelo A.** Cero coste, cero riesgo de plataforma, cero cambios de
   backend, y coherente con la premisa de que este proyecto es un front end nuevo
   sobre un activo que ya tenemos.
2. **Preparar el modelo B para v2**, con la arquitectura lista: los entitlements
   se resuelven en servidor desde el día uno, aunque en v1 la respuesta venga de
   RevenueCat.
3. **Perseguir el modelo C desde ahora**, en paralelo y por la vía de negocio. Es
   donde está el dinero real de este mercado y no bloquea el desarrollo.
4. **D y E, después**, cuando haya usuarios que medir.

## 6. Lo que hay que averiguar para cerrar esto

- Revenue share de Pleos y países soportados.
- Si su política de store permite el modelo A, o exige que todo desbloqueo de
  contenido de pago pase por su pasarela. **Esta es la pregunta crítica**: si lo
  exige, el modelo A no es viable y la v1 cambia de forma.
- Si Pleos Account puede usarse para vincular con nuestra cuenta sin teclear.
- Qué apetito tiene Hyundai por acuerdos de contenido con terceros (modelo C).

## Hueco para socios en la ficha *(2026‑11‑18)*

La ficha de cada sitio tiene ya una sección **«Opiniones»** pensada para socios
como Tripadvisor: una tarjeta por proveedor con su nombre, la nota sobre 5, el
número de opiniones, el nivel de precio, una frase de una opinión si el socio
la deja mostrar, y el enlace «Abrir en …». Hoy no hay ningún socio conectado y
la sección no sale; conectar uno es rellenar `PartnerInfo`
(`feature/parked/.../PlaceDetailPanel.kt`), no rehacer la ficha.

Antes de conectar uno hay que revisar sus condiciones: atribución obligatoria,
qué datos se pueden guardar y si se pueden mostrar en un coche en marcha.
