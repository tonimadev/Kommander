plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    // O domínio só conhece Kotlin puro + coroutines (Flow). Nada de Ktor/Compose aqui.
    api(libs.kotlinx.coroutines.core)
}
