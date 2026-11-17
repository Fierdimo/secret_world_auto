pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx no se publica en Maven Central. Se obtiene con
        // tools/fetch-tts-assets.sh y se consume desde aqui como repositorio,
        // no como fichero suelto: un `files(...aar)` rompe al ensamblar la
        // libreria que lo usa.
        flatDir { dirs("libs") }
    }
}

rootProject.name = "secret-world-auto"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:platform")
include(":core:designsystem")
include(":feature:voice")
include(":feature:discover")
include(":feature:parked")

// Banco de modelos de lenguaje: compara Gemma 3 1B (MediaPipe) con Qwen3 0.6B
// (LiteRT-LM) sobre el prompt real. No es parte del producto.
include(":spikes:llm-bench")
