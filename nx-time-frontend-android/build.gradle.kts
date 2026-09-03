/*
 * El bloque 'plugins' define las "herramientas" que usa el proyecto.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // El compilador de Compose. Desde Kotlin 2.0 va como plugin propio
    // y toma la versión del propio Kotlin, así que ya no hay que
    // emparejar a mano Kotlin <-> compilador de Compose.
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * `android { kotlinOptions { jvmTarget = "17" } }` quedó obsoleto en
 * Kotlin 2.2. El destino de la JVM se declara ahora aquí, en la
 * extensión de Kotlin y con un tipo en vez de una cadena suelta.
 */
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/*
 * El bloque 'android' es la configuración principal de la app.
 */

android {
    namespace = "com.nxtime.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nxtime.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Se activa la minificación (Fase 11): hasta ahora estaba a
            // false y proguard-rules.pro ni siquiera existía, aunque se
            // referenciaba aquí. Reduce el APK y ofusca el código.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    /*
     * Sabores de compilación (Fase 11): la URL del backend deja de estar
     * escrita a fuego en RetrofitClient.kt y pasa a BuildConfig.
     *
     *  - dev:  el emulador contra el backend local. 10.0.2.2 es la
     *          dirección con la que el emulador de Android ve el
     *          "localhost" del ordenador anfitrión; "localhost" a secas
     *          sería el propio emulador.
     *  - prod: el backend desplegado, por HTTPS.
     *
     * La URL de producción se lee de una propiedad de Gradle para no
     * fijarla en el repositorio; si no está, se usa un marcador evidente
     * en vez de una URL falsa que parezca buena.
     */
    flavorDimensions += "entorno"

    productFlavors {
        create("dev") {
            dimension = "entorno"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        create("prod") {
            dimension = "entorno"
            val urlProduccion = (project.findProperty("nxtime.prod.url") as String?)
                ?: "https://CONFIGURA-nxtime.prod.url.invalid/"
            buildConfigField("String", "BASE_URL", "\"$urlProduccion\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        /*
         * El "desugaring" permite usar java.time (Instant, LocalDate...)
         * por debajo de Android 8. Con minSdk 24 NO es opcional: sin
         * esto, cualquier pantalla que formatee una fecha reventaría en
         * los móviles más antiguos que la app dice soportar.
         */
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        // Necesario para los buildConfigField de los flavors: desde el
        // plugin de Android 8 hay que pedirlo explícitamente.
        buildConfig = true
    }

    /*
     * Lint no estaba configurado y el CI solo compilaba, así que nadie
     * veía sus avisos. Ahora rompe la compilación: es la red que detecta
     * textos sin traducir o contrastes malos sin tener que abrir la app.
     */
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = false
    }
}

/*
 * El bloque 'dependencies' define todas las librerías que la app necesita para funcionar.
 */

dependencies {

    /*
     * Esta es la librería que hace funcionar el "desugaring"
     */
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    /*
     * Dependencias de Retrofit:
     */
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    /*
     * Gson, declarado a propósito aunque converter-gson ya lo arrastre.
     *
     * ApiErrorParser usa Gson directamente para leer el "detail" del
     * ProblemDetail, y una dependencia que se usa en el código propio
     * debe declararse: la versión transitiva de converter-gson 2.9.0 es
     * la 2.8.5, anterior a `JsonParser.parseString` (2.8.6), así que
     * heredarla en silencio hacía que ese fichero no compilara.
     */
    implementation("com.google.code.gson:gson:2.11.0")

    /*
     * Jetpack Compose. El BOM fija de una vez las versiones de todas las
     * librerías de Compose que sean compatibles entre sí, por eso las de
     * abajo van sin número.
     */
    /*
     * BOM 2025.11.00 y no uno más nuevo: es el primero cuyo Material 3
     * es la 1.4.0, es decir, Expressive ya estable (formas, esquemas de
     * movimiento, LoadingIndicator, ButtonGroup...), y el último de esa
     * serie que sigue compilando contra `compileSdk 36` con AGP 8.x.
     * Del BOM 2025.12.00 en adelante, Compose pasa a la 1.10 y **exige
     * AGP 9.1+ y compileSdk 37**, que es otra migración distinta.
     */
    val composeBom = platform("androidx.compose:compose-bom:2025.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    /*
     * NavigationSuiteScaffold: una sola declaración de destinos que se
     * pinta como barra inferior en móvil y como raíl lateral en tablet o
     * plegable, sin escribir dos layouts. Va en el BOM, así que sin
     * número de versión.
     */
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Estas tres se quedan deliberadamente por debajo de su última
    // versión: lifecycle 2.10+, activity 1.12+ y navigation 2.10 piden
    // AGP 9.1, igual que el BOM de arriba.
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    // collectAsStateWithLifecycle: deja de recolectar el StateFlow
    // cuando la pantalla no está visible.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    /*
     * Dependencias base de Android.
     *
     * AppCompat ya no está: con la reescritura en Compose, MainActivity
     * hereda de ComponentActivity y el tema de arranque cuelga del de la
     * plataforma, así que nada del proyecto la usaba ya.
     */
    implementation("androidx.core:core-ktx:1.17.0")

    /*
     * Tests unitarios (JVM, sin emulador). JUnit 4 y no 5 porque es lo
     * que trae de serie el plugin de Android; JUnit 5 necesitaría un
     * plugin de terceros para tan poca ganancia aquí.
     */
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Turbine: para afirmar sobre lo que va emitiendo un StateFlow.
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}