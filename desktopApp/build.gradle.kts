import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":presentation"))
    // A UI só enxerga o ViewModel; a implementação Ktor entra apenas para a
    // composição de dependências em `di/AppContainer`.
    implementation(project(":data"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing) // fornece Dispatchers.Main no Desktop

    runtimeOnly(libs.logback.classic)
}

compose.desktop {
    application {
        mainClass = "dev.kommander.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "kommander"
            packageVersion = "1.0.0"
            description = "Companion dashboard for background coding agents"
            // Ktor/CIO + logback precisam destes módulos no runtime empacotado (jlink).
            modules("java.naming", "java.management", "jdk.unsupported")
            linux {
                shortcut = true
            }
        }
    }
}
