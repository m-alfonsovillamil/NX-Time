package com.nxtime.app.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Un mensaje para el usuario, que puede venir de dos sitios distintos.
 *
 * Los ViewModel no pueden llamar a `stringResource` -- eso necesita el
 * `Context` de la pantalla -- pero tampoco deben llevar el texto escrito
 * dentro: eso es lo que había antes, con 158 literales sueltos en
 * Kotlin, y es lo que hace imposible traducir la aplicación. La salida
 * es que el ViewModel diga *qué* mensaje quiere, y que la pantalla lo
 * resuelva al pintarlo.
 *
 * Hacen falta los dos casos:
 *
 *  - [Recurso] para lo que decide la propia app (validaciones, errores
 *    HTTP genéricos): es traducible y vive en strings.xml.
 *  - [Texto] para lo que escribe el backend en el campo `detail` del
 *    ProblemDetail. Ese texto ya viene redactado desde el servidor y la
 *    app no tiene forma de traducirlo; enseñarlo tal cual es justo el
 *    arreglo que perseguía ApiErrorParser.
 */
sealed interface MensajeUi {

    /** Un texto ya redactado, normalmente del backend. */
    data class Texto(val valor: String) : MensajeUi

    /** Una entrada de strings.xml, con sus argumentos si los lleva. */
    data class Recurso(
        @StringRes val id: Int,
        val argumentos: List<Any> = emptyList()
    ) : MensajeUi

    companion object {
        fun de(@StringRes id: Int, vararg argumentos: Any) =
            Recurso(id, argumentos.toList())
    }
}

/** Convierte el mensaje en el texto que se pinta. */
@Composable
fun MensajeUi.resolver(): String = when (this) {
    is MensajeUi.Texto -> valor
    is MensajeUi.Recurso ->
        if (argumentos.isEmpty()) stringResource(id)
        else stringResource(id, *argumentos.toTypedArray())
}
