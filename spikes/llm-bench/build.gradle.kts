plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.secretworld.auto.llmbench"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.secretworld.auto.llmbench"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        // Los moviles de prueba y el vehiculo son arm64-v8a.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        // Se mide en release: en debug la JVM va mas lenta y ensucia los tiempos
        // del lado Kotlin. Firmado con la clave de depuracion para instalarlo sin mas.
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    // El GemmaLanguageModel de produccion, sin copiarlo: la referencia es la de verdad.
    implementation(project(":feature:voice"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.litertlm.android)
}
