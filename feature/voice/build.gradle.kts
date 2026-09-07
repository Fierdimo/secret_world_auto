plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.secretworld.auto.feature.voice"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    // Los modelos .onnx ya estan comprimidos; recomprimirlos no ahorra nada y
    // ademas impide mapearlos en memoria.
    androidResources {
        noCompress += listOf("onnx", "bin")
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:platform"))
    implementation(libs.kotlinx.coroutines.core)
    // sherpa-onnx: ver el repositorio flatDir en settings.gradle.kts
    implementation(group = "", name = "sherpa-onnx", ext = "aar")
}
