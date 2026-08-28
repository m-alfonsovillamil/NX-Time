// Settings de Gradle usado SOLO por el Dockerfile (Fase 7): construye
// ÚNICAMENTE :nx-time-backend, sin declarar :nx-time-frontend-android.
//
// El settings.gradle.kts normal incluye los dos módulos del monorepo, y
// Gradle configura (evalúa el build.gradle.kts de) todo proyecto
// declarado en settings aunque no participe en la tarea pedida, salvo
// que se pida explícitamente lo contrario. Eso significa que construir
// solo el backend con el settings.gradle.kts de siempre intentaría
// también configurar el módulo Android -- que aplica el plugin
// com.android.application y necesita el Android SDK (sdk.dir en
// local.properties), ausente a propósito en la imagen de build del
// backend. Un settings.gradle.kts alternativo que ni siquiera declara
// ese módulo evita el problema de raíz, sin tocar el settings.gradle.kts
// real que usa Android Studio / el resto del monorepo.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.springframework.boot") version "3.5.6"
        id("io.spring.dependency-management") version "1.1.7"
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

rootProject.name = "NX-Time"

include(":nx-time-backend")
