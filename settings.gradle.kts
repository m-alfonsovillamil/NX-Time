// El ÚNICO fichero de configuración de Gradle del monorepo.
//
// Hasta octubre de 2026 había dos: este y settings-docker.gradle.kts, que
// declaraba solo el backend y se copiaba encima de este en el Dockerfile y en
// el CI. Los dos repetían las versiones de los plugins, y con Spring Boot 3
// estuvieron meses desalineados (3.5.6 allí, 3.5.16 aquí): la imagen
// desplegada llevaba un Spring Boot que el CI no probaba.
//
// Ahora las versiones están escritas una sola vez, y quien solo quiere el
// backend lo pide con la variable de entorno NXTIME_SOLO_BACKEND=true (la
// ponen el Dockerfile, ci.yml y e2e.yml). Hace falta pedirlo: Gradle configura
// todo proyecto declarado aquí aunque no participe en la tarea, y el módulo de
// Android necesita el SDK de Android, que en la imagen del backend no está (ni
// siquiera se copia su carpeta).
//
// La variable se lee dos veces, aquí abajo y más abajo, porque Gradle evalúa
// el bloque pluginManagement aparte y antes que el resto del fichero: no ve
// nada declarado fuera de él.
pluginManagement {
    repositories {
        // Le dice a Gradle dónde buscar los plugins de Android y Spring.
        // google() solo sirve los de Android.
        if (System.getenv("NXTIME_SOLO_BACKEND") != "true") google()
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
        id("com.android.application") version "9.4.1"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
        // Push (Fase B5): lee google-services.json y genera la configuración
        // de Firebase de cada variante.
        id("com.google.gms.google-services") version "4.5.0"
        id("org.springframework.boot") version "4.1.1"
        id("io.spring.dependency-management") version "1.1.7"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

// Permite que Gradle descargue automáticamente un JDK 21 si la máquina
// solo tiene instalada otra versión (ver toolchain en
// nx-time-backend/build.gradle.kts).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

val soloBackend = System.getenv("NXTIME_SOLO_BACKEND") == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Define dónde buscar las dependencias (librerías)
        if (!soloBackend) google()
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

// Nombre del proyecto raíz
rootProject.name = "NX-Time"

// Los módulos, con los nombres exactos de las carpetas. La web no es un
// proyecto de Gradle (ADR 021).
include(":nx-time-backend")
if (!soloBackend) include(":nx-time-frontend-android")
