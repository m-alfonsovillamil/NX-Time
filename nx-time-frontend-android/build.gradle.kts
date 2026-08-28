/*
 * El bloque 'plugins' define las "herramientas" que usa el proyecto.
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

                /*
                 * Habilita el "desugaring", permite usar APIs modernas de Java (como 'java.time.LocalDate') en versiones antiguas de Android.
                 */

        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    /*
     * Activa 'ViewBinding'
     */

    buildFeatures {
        viewBinding = true
        // Necesario para los buildConfigField de los flavors: desde el
        // plugin de Android 8 hay que pedirlo explícitamente.
        buildConfig = true
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
     * Ayudan a que la app sobreviva a giros de pantalla y a separar la lógica.
     */
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.8.1")

    /*
     * Dependencias Base de Android:
     */
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Dependencias de Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}