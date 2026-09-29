plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

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
    }

    buildFeatures {
        compose = true
    }

    flavorDimensions += "target"
    productFlavors {
        // El producto, para Pleos Connect.
        create("vehicle") {
            dimension = "target"
        }
        // Banco de pruebas para tablet o movil: mismo codigo, sin exigir
        // hardware de automocion, y con la voz inglesa dentro del APK para
        // que baste instalar y pulsar.
        create("bench") {
            dimension = "target"
            applicationIdSuffix = ".bench"
            versionNameSuffix = "-bench"
        }
        // La demo para el cliente: el banco de pruebas en un movil, con tema
        // claro, en italiano y con todo dentro del APK (ver sourceSets).
        create("demo") {
            dimension = "target"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            // Se envia como APK suelto, no por tienda: el release va firmado con
            // la clave de depuracion para que se pueda instalar sin mas. Release
            // y no debug porque Compose en debug va a tirones.
            signingConfig = signingConfigs.getByName("debug")
        }
        // Mapbox se enlaza SOLO en el banco de pruebas y la demo. El APK del vehículo no lo
        // lleva: allí se usará el servicio de mapas del propio dispositivo, que es
        // además lo que evita el contrato de automoción.
    }

    sourceSets {
        // La demo comparte con bench el codigo y el manifiesto. Los assets no:
        // lleva los suyos (modelos, LLM y voz italiana, ver
        // tools/fetch-demo-assets.sh) y no las voces inglesa y espanola.
        getByName("demo") {
            kotlin.directories += "src/bench/kotlin"
            manifest.srcFile("src/bench/AndroidManifest.xml")
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
    "benchImplementation"(libs.mapbox.maps)
    "benchImplementation"(libs.mapbox.compose)
    "demoImplementation"(libs.mapbox.maps)
    "demoImplementation"(libs.mapbox.compose)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
