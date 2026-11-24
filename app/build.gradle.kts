import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Las credenciales de la app en Pleos (*My Project*), fuera del repositorio:
// `pleos.clientId` y `pleos.clientSecret` en local.properties. Sin ellas, la voz
// y la escucha de Pleos van solo en el dispositivo.
val pleosKeys = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun pleosKey(name: String): String = "\"" + pleosKeys.getProperty("pleos.$name", "").replace("\"", "") + "\""

android {
    namespace = "com.secretworld.auto"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.secretworld.auto"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // El vehiculo es arm64-v8a. Sin esto se empaquetan las cuatro ABIs de
        // sherpa-onnx y el APK pasa de ~34 MB a 124. Y ojo: puesto en un modulo
        // de libreria no filtra nada, tiene que estar aqui.
        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField("String", "PLEOS_CLIENT_ID", "\"\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "target"
    productFlavors {
        // El producto, para Pleos Connect.
        create("vehicle") {
            dimension = "target"
            buildConfigField("boolean", "PLEOS_SERVICES", "false")
        }
        // Banco de pruebas para tablet o movil: mismo codigo, sin exigir
        // hardware de automocion, y con la voz inglesa dentro del APK para
        // que baste instalar y pulsar.
        create("bench") {
            dimension = "target"
            applicationIdSuffix = ".bench"
            versionNameSuffix = "-bench"
            buildConfigField("boolean", "PLEOS_SERVICES", "false")
        }
        // La demo para el cliente: el banco de pruebas en un movil, con tema
        // claro, en italiano y con todo dentro del APK (ver sourceSets).
        create("demo") {
            dimension = "target"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "PLEOS_SERVICES", "false")
            // Se envia como APK suelto, no por tienda: el release va firmado con
            // la clave de depuracion para que se pueda instalar sin mas. Release
            // y no debug porque Compose en debug va a tirones.
            signingConfig = signingConfigs.getByName("debug")
        }
        // Para probar en el emulador de Pleos —la imagen Connect-W, x86_64—: la
        // demo sin nada de voz ni de IA dentro. Habla, escucha y piensa con los
        // servicios de la plataforma (ver docs/PLEOS-SDK-SURFACE.md), así que el
        // APK no lleva modelos ni los motores nativos que los ejecutan.
        create("pleos") {
            dimension = "target"
            applicationIdSuffix = ".pleos"
            versionNameSuffix = "-pleos"
            buildConfigField("boolean", "PLEOS_SERVICES", "true")
            buildConfigField("String", "PLEOS_CLIENT_ID", pleosKey("clientId"))
            buildConfigField("String", "PLEOS_CLIENT_SECRET", pleosKey("clientSecret"))
            ndk {
                abiFilters += "x86_64"
            }
            signingConfig = signingConfigs.getByName("debug")
        }
        // El mapa es de OpenStreetMap, con MapLibre, en el banco de pruebas, la
        // demo y Pleos. El APK del vehículo no lleva mapa propio: allí lo pone el
        // navegador del sistema.
    }

    sourceSets {
        // La demo comparte con bench el codigo y el manifiesto. Los assets no:
        // lleva los suyos (modelos, LLM y voz italiana, ver
        // tools/fetch-demo-assets.sh) y no las voces inglesa y espanola.
        getByName("demo") {
            kotlin.directories += "src/bench/kotlin"
            manifest.srcFile("src/bench/AndroidManifest.xml")
        }
        // El mismo código que la demo, con su propio manifiesto: el de un coche.
        getByName("pleos") {
            kotlin.directories += "src/bench/kotlin"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        // targetSdk 34 es deliberado: es la API de Pleos Connect (AAOS 14). Subirlo
        // por encima del sistema sobre el que corre la app no aporta nada.
        disable += "OldTargetApi"
        abortOnError = true
    }
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:platform"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:voice"))
    implementation(project(":feature:discover"))
    implementation(project(":feature:parked"))
    // El mapa: OpenStreetMap con MapLibre. Sin Mapbox, que no tenemos licenciado.
    "benchImplementation"(libs.maplibre)
    "demoImplementation"(libs.maplibre)
    "pleosImplementation"(libs.maplibre)
    // Para la pantalla de verificación (PleosCheckActivity), que los usa tal cual.
    "pleosImplementation"(libs.pleos.tts)
    "pleosImplementation"(libs.pleos.stt)

    // El navegador de Pleos: el destino se le pide a su servicio de navegación.
    implementation(libs.pleos.navihelper)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
}

// Sin los motores nativos de voz y de LLM en el sabor pleos: allí no se usan y
// son la mayor parte del peso. Sus clases siguen en el APK, pero nunca se cargan.
androidComponents {
    onVariants(selector().withFlavor("target" to "pleos")) { variant ->
        variant.packaging.jniLibs.excludes.addAll(
            listOf(
                "**/libllm_inference_engine_jni.so",
                "**/libonnxruntime.so",
                "**/libsherpa-onnx-jni.so",
                "**/libsherpa-onnx-c-api.so",
                "**/libsherpa-onnx-cxx-api.so",
            ),
        )
    }
}
