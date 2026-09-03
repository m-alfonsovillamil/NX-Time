pluginManagement {
    repositories {
        // Le dice a Gradle dónde buscar los plugins de Android y Spring
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // Define las versiones de los plugins para que los módulos las usen.
    // kotlin("jvm"/"plugin.spring"/"plugin.jpa") ya no hacen falta aquí:
    // el backend es 100% Java desde la Fase 1 del plan de
    // profesionalización. Solo quedan los de Kotlin/Compose para el
    // módulo :nx-time-frontend-android.
    //
    // Kotlin 2.x hace falta para Compose: desde esa versión el
    // compilador de Compose es un plugin de Gradle propio
    // ("plugin.compose") en vez del antiguo
    // composeOptions.kotlinCompilerExtensionVersion, que obligaba a
    // emparejar a mano cada versión de Kotlin con la suya de Compose.
    // Subirlo es seguro: el backend no tiene ni un fichero .kt.
    //
    // AGP se queda en la línea 8.x (la 8.13.2 es la última) y no salta a
    // la 9.x a propósito. Lo que se persigue aquí es Material 3
    // Expressive, y para eso basta con el BOM de Compose 2025.11.00 (ver
    // el porqué de esa versión en build.gradle.kts del módulo Android).
    // Del BOM 2025.12.00 en adelante sí haría falta AGP 9.1 y
    // `compileSdk 37`, que es una migración distinta: meterla en el
    // mismo PR haría imposible saber cuál de las dos rompió qué.
    plugins {
        id("com.android.application") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "2.2.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
        id("org.springframework.boot") version "3.5.6"
        id("io.spring.dependency-management") version "1.1.7"
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
}

// Permite que Gradle descargue automáticamente un JDK 21 si la máquina
// solo tiene instalada otra versión (ver toolchain en
// nx-time-backend/build.gradle.kts).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Define dónde buscar las dependencias (librerías)
        google()
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

// Nombre del proyecto raíz
rootProject.name = "NX-Time"

// Incluye ambos módulos, usando los nombres exactos de las carpetas
include(":nx-time-backend")
include(":nx-time-frontend-android")