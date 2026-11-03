# Banco de modelos de lenguaje — Gemma 3 1B frente a Qwen3 0.6B

Responde a una pregunta: **¿puede un modelo más pequeño sustituir a Gemma 3 1B
en la app sin entender peor?**

| | Gemma 3 1B (el de la app) | Qwen3 0.6B |
|---|---|---|
| Fichero | `gemma3-1b-it-int4.task` | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` |
| Tamaño | 555 MB | **343 MB** |
| Motor | MediaPipe LLM Inference 0.10.35 | **LiteRT-LM 0.18.1** |
| Prefijo procesado una sola vez | Sí, clonando la sesión | **No**: LiteRT-LM 0.18 no clona sesiones |

Lo que se compara es **lo que hace la app**, no un prompt de juguete: el
`LlmIntentResolver` y el `IntentPrompt` de `:core:domain`, y para Gemma el
`GemmaLanguageModel` de producción. Qwen va por
[LiteRtLanguageModel.kt](src/main/kotlin/com/secretworld/auto/llmbench/LiteRtLanguageModel.kt),
que imita a Gemma: continuación en crudo sin plantilla de chat, muestreo
determinista y corte en la primera línea.

**No es parte del producto.** Si Qwen gana, la integración se hace en
`:feature:voice` aparte.

## Uso

Con un móvil Android conectado por USB y la depuración activada:

```bash
spikes/llm-bench/run.sh            # los dos modelos, uno detrás de otro
spikes/llm-bench/run.sh qwen       # solo uno
```

El script compila el APK si hace falta, lo instala y descarga Qwen si no está en
`dist/models/` (verifica su sha256). Saca Gemma del zip de la demo y copia cada
modelo al teléfono solo si no está ya. Después ejecuta cada modelo en un proceso
nuevo y deja en [results/](results/) el log y un TSV por modelo.

## Qué mide

[24 frases](src/main/kotlin/com/secretworld/auto/llmbench/BenchCases.kt): 12 en
italiano, 6 en inglés y 6 en español, con la orden esperada para cada una. Los
lugares son el Panteón, la Fontana de Trevi, el Castel Sant'Angelo y la Piazza
Navona, a propósito distintos de los de los ejemplos del prompt.

- **Aciertos** por idioma. Una búsqueda cuenta como acierto con cualquier
  palabra; las demás órdenes tienen que coincidir, lugar incluido.
- **Tiempo por orden**: la mediana, el máximo y la primera orden.
- **Memoria** (PSS) antes y después de cargar el modelo.
- **La frase que diría**, para juzgar a mano si contesta en el idioma del conductor.

## Resultado (2026‑11‑03): Qwen3 0.6B descartado

Medido en un Huawei Y9 2019 (JKM-LX3, Kirin 710, Android 8.1, 2,7 GB de RAM).
La prueba se canceló a la tercera frase, porque lo visto ya bastaba.

| | Qwen3 0.6B |
|---|---|
| Aciertos | **1 de 3** |
| Tiempo por orden | **60 a 72 s**; la app cancela a los 8 s |
| Carga | 4 a 11 s |
| Memoria | De 360 a 730 MB, según la ejecución |

- **Copia los ejemplos del prompt** en vez de entender la frase. Ante "che cosa
  c'è da vedere qua intorno" contestó `find_places shawarma … find_places coffee`.
  Ante "portami al castello" eligió `tell_me_about 2` (otra herramienta y otro
  lugar) y se inventó un "old market" sacado de otro ejemplo.
- **Contesta en inglés** a frases en italiano.
- **Leer el prefijo** (unos 700 tokens) cuesta más de 15 s en este procesador, y
  LiteRT-LM no permite reutilizarlo entre órdenes.

Gemma no pudo servir de referencia en este teléfono: MediaPipe necesita Android 9.

**Decisión: la demo sigue con Gemma 3 1B dentro del APK.** Ninguna alternativa
compensa ahí. Gemma 4 E2B (2,58 GB) llevaría el APK a unos 3 GB. Qwen3.5 0.8B
(965 MB) suma unos 400 MB sin una mejora demostrada. El producto usará la IA de
Pleos a través de la interfaz `LanguageModel`, así que el modelo de la demo es
provisional por diseño. Ver
[../../docs/PLEOS-SDK-SURFACE.md](../../docs/PLEOS-SDK-SURFACE.md).

## Límites

- Un teléfono no es el coche. Sirve para comparar los dos modelos entre sí, no
  para prometer tiempos en Pleos.
- Las frases no pasan antes por la gramática. En la app el modelo solo ve lo que
  la gramática no entiende, así que aquí se le exige más que en el uso real.
