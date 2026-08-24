pluginManagement {
    repositories {
        // Le dice a Gradle dónde buscar los plugins de Android y Spring
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // Define las versiones de los plugins para que los módulos las usen
    plugins {
        id("com.android.application") version "8.4.1"
        id("org.jetbrains.kotlin.android") version "1.9.25"
        id("org.springframework.boot") version "3.5.6"
        id("io.spring.dependency-management") version "1.1.7"
        kotlin("jvm") version "1.9.25"
        kotlin("plugin.spring") version "1.9.25"
        kotlin("plugin.jpa") version "1.9.25"
    }
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