# Reglas de ProGuard/R8 para el APK de release (Fase 11).
#
# Este fichero se referenciaba en build.gradle.kts desde el principio
# pero no existía, y la minificación estaba desactivada. Al activarla
# (isMinifyEnabled = true) hacen falta reglas para lo que R8 no puede
# deducir por su cuenta: todo lo que se resuelve por REFLEXIÓN.

# ---------------------------------------------------------------------
# Gson (serialización de los DTOs)
# ---------------------------------------------------------------------
# Gson lee los nombres de los campos por reflexión para casarlos con el
# JSON. Si R8 renombra "fechaInicio" a "a", el JSON del backend deja de
# encajar y los campos llegan a null EN SILENCIO -- sin excepción, sin
# error: simplemente datos vacíos. Es el fallo clásico de un release
# minificado que funcionaba perfectamente en debug.
-keep class com.nxtime.app.data.dto.** { *; }

# Los tipos genéricos se borran al compilar; Gson los necesita para
# deserializar List<RespuestaAusencia> y compañía.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ---------------------------------------------------------------------
# Retrofit + OkHttp
# ---------------------------------------------------------------------
# Retrofit construye las implementaciones de ApiService por reflexión a
# partir de las anotaciones de los métodos.
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface com.nxtime.app.data.network.ApiService

# Las funciones 'suspend' de Kotlin llevan un Continuation como último
# parámetro; Retrofit lo detecta por la firma.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 en modo completo -- el de serie desde AGP 8 -- BORRA la firma genérica
# de las clases que ninguna regla conserva. Retrofit lee de esa firma el
# `Response<RespuestaAutenticacion>` de cada endpoint, y sin ella revienta
# al primer uso de ApiService, antes de mandar nada.
#
# Retrofit trae estas reglas dentro del jar desde la 2.10.0; la 2.9.0 que
# usa el proyecto no, así que van aquí. Se descubrió el 11/09/2026 al
# instalar por primera vez un APK de release firmado: el login respondía
# "No se ha podido conectar con el servidor" al instante, porque
# ApiErrorParser.mensajeDeRed traduce cualquier excepción a eso. En debug
# no hay minificación y nunca falló.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Avisos de dependencias opcionales que no usamos (OkHttp las declara
# para entornos donde sí están disponibles).
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------
# Enums del contrato con el backend
# ---------------------------------------------------------------------
# Gson serializa los enums por el NOMBRE de la constante ("VACACIONES",
# "APROBADA", "PAUSA_INICIO"...). Si R8 los renombra, el backend recibe
# valores que no reconoce y responde 400.
-keepclassmembers enum com.nxtime.app.data.dto.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------
# ViewBinding
# ---------------------------------------------------------------------
# Las clases de binding se generan e instancian por reflexión desde
# inflate().
-keep class com.nxtime.app.databinding.** { *; }
