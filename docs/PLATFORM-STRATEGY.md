# Estrategia de plataformas: PLEOS, Android Auto y CarPlay

Decisión tomada: **PLEOS primero, Android Auto después, CarPlay al final si
sigue teniendo sentido.** Este documento explica por qué ese orden y no otro.

---

## 1. No son la misma clase de cosa

| | PLEOS / AAOS | Android Auto | CarPlay |
|---|---|---|---|
| Dónde corre la app | **En el coche** | En el teléfono | En el teléfono |
| La pantalla del coche es | El dispositivo | Un monitor externo | Un monitor externo |
| Sin teléfono | Funciona | No hay app | No hay app |
| Datos del vehículo | Velocidad, posición, estado, ADAS | Ninguno | Ninguno |
| Traspaso de navegación | Sí (NaviHelper) | No | No |
| Voz bidireccional propia | Sí (por confirmar, spike 1) | Limitada | No (pasa por Siri) |
| Lenguaje del cliente | Kotlin | Kotlin | Swift |
| Distribución | Pleos App Market | Play Store | Dentro de la app iOS actual |

CarPlay y Android Auto son **proyección**: el coche aporta pantalla, entrada y
altavoces, y no ejecuta nada nuestro. De ahí sale todo lo demás.

## 2. Qué se comparte y qué no

Se paga una vez, en el proyecto de PLEOS:

- Backend (sin cambios para la v1)
- Catálogo reescrito en formato conversacional
- Sistema de cuentas y vinculación
- Módulo `:core:domain` en Kotlin

Después:

**Android Auto es casi gratis.** Reutiliza `:core:domain` y `:core:data` enteros.
Lo nuevo es la capa de interfaz con plantillas y vivir en el teléfono.

**CarPlay ahorra menos.** Hereda backend, contenido y cuenta, igual que Android
Auto, pero el cliente es **100% Swift nuevo** y no reutiliza nada del Kotlin.
A cambio no necesita ficha nueva en la tienda: una app de CarPlay viaja dentro
de una app de iPhone existente, así que sería una capa nativa añadida a la app
de Secret World actual. Eso resuelve la distribución, pero implica escribir
Swift nativo dentro de un proyecto Expo, y solicitar a Apple un entitlement que
se concede caso por caso.

## 3. Por qué PLEOS primero

1. **Es la única plataforma donde existe el producto completo.** Datos del
   vehículo, voz en dos direcciones y traspaso de navegación. En las otras dos el
   acompañante se degrada a un narrador con botones.
2. **Hacerlo primero abarata las otras dos.** Empezar por CarPlay costaría la
   misma fundación, devolvería el producto más pobre y no enseñaría nada
   aplicable a los demás.
3. **Su tienda está prácticamente vacía.** La visibilidad relativa nunca volverá
   a ser tan barata.

## 4. La tensión que hay que reconocer

PLEOS llega a coches Hyundai, Kia y Genesis **de 2026 en adelante**. CarPlay y
Android Auto llegan a la flota que **ya existe hoy**, que es órdenes de magnitud
mayor. Si el criterio fuera alcance puro, el orden sería el inverso.

Se elige PLEOS igualmente porque es donde se puede construir el producto real y
donde hay menos competencia. Es un intercambio consciente de alcance por calidad
de producto, no un descuido.

## 5. La condición que sostiene todo el ahorro

`:core:domain` no puede tener **ni una** dependencia de Pleos. Se garantiza
haciéndolo librería Kotlin pura (sin plugin de Android) y con un test de
arquitectura en CI. Si esa regla se rompe, Android Auto deja de ser un descuento
y pasa a ser una reescritura.

## 6. Lo que no se hereda

El ahorro es de código, no de trámite. Cada plataforma tiene su tienda, su
revisión y sus permisos. Tres productos siguen siendo tres aprobaciones, y
CarPlay añade el entitlement de Apple.
