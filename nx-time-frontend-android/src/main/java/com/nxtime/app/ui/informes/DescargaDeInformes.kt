package com.nxtime.app.ui.informes

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File

/**
 * Guarda el cuerpo de una descarga en la caché de la app y devuelve el
 * fichero.
 *
 * Los dos endpoints de informes devuelven un flujo con
 * `Content-Disposition` **y exigen el token**, así que no vale pasarle la
 * URL a `DownloadManager` a pelo: la petición saldría sin cabecera de
 * autorización y volvería con un 401. Se descarga con Retrofit, que ya
 * lleva el interceptor de sesión, y se escribe aquí.
 *
 * Va a `cacheDir` y no al almacenamiento compartido a propósito: un
 * informe de jornada es un documento con datos personales de toda la
 * plantilla, y dejarlo en la carpeta de descargas lo expondría a
 * cualquier app con permiso de lectura. Desde la caché se comparte con un
 * `FileProvider`, que concede acceso solo a la app elegida y solo
 * mientras dure el intent.
 */
suspend fun guardarEnCache(
    context: Context,
    cuerpo: ResponseBody,
    nombreFichero: String
): File = withContext(Dispatchers.IO) {
    val carpeta = File(context.cacheDir, "informes").apply { mkdirs() }
    val destino = File(carpeta, nombreFichero)
    cuerpo.byteStream().use { entrada ->
        destino.outputStream().use { salida -> entrada.copyTo(salida) }
    }
    destino
}

/**
 * Abre el informe con la app que el usuario elija.
 *
 * `FLAG_GRANT_READ_URI_PERMISSION` es lo que hace que el visor pueda leer
 * un fichero que vive dentro de la caché de NX Time; sin esa bandera, el
 * `content://` de un FileProvider es inaccesible para cualquier otra app
 * y el visor abre en blanco.
 */
fun compartirInforme(context: Context, fichero: File, tipoMime: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.informes",
        fichero
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, tipoMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, fichero.name))
}

const val MIME_EXCEL =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
const val MIME_PDF = "application/pdf"
