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
    // profesionalización. Solo queda "org.jetbrains.kotlin.android" para
    // el módulo :nx-time-frontend-android.
    plugins {
        id("com.android.application") version "8.4.1"
        id("org.jetbrains.kotlin.android") version "1.9.25"
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