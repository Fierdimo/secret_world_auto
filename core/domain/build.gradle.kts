plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}

/**
 * :core:domain no puede depender de Android ni de Pleos. El plugin de Kotlin puro
 * ya impide compilar contra ellos, pero esta tarea lo hace explícito y falla en CI
 * con un mensaje que se entiende, en lugar de con un error de compilación críptico.
 */
val checkDomainPurity by tasks.registering {
    group = "verification"
    description = "Falla si :core:domain importa Android o SDKs de plataforma."
    val sources = fileTree("src") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val prohibido = Regex("""^\s*import\s+(android[.x]|androidx?\.|ai\.pleos|com\.pleos|pleos\.)""")
        val fallos = mutableListOf<String>()
        sources.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (prohibido.containsMatchIn(line)) fallos += "${f.relativeTo(projectDir)}:${i + 1}: $line"
            }
        }
        if (fallos.isNotEmpty()) {
            throw GradleException(
                "El dominio debe seguir siendo Kotlin puro. Importaciones prohibidas:\n" +
                    fallos.joinToString("\n")
            )
        }
    }
}

tasks.named("check") { dependsOn(checkDomainPurity) }
