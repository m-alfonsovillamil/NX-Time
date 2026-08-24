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
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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