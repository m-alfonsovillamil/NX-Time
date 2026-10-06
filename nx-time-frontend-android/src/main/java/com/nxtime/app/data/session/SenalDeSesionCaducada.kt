package com.nxtime.app.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «La sesión ha muerto sola y todavía nadie ha llevado al login».
 *
 * Es un **estado**, no un evento, y esa es toda la razón de que exista.
 *
 * Antes era un `MutableSharedFlow` sin repetición: un aviso que se emite una
 * vez. Un `SharedFlow` así entrega solo a quien esté recogiendo en ese
 * instante; si no hay nadie, **el aviso se pierde** (el `extraBufferCapacity`
 * no lo guarda: solo vale cuando ya hay alguien suscrito). Y sí había un
 * momento sin nadie: al arrancar el proceso, `NxTimeApplication` vuelve a
 * registrar el token de push contra el servidor antes de que exista ninguna
 * pantalla. Con una sesión guardada que ya no valía, esa petición recibía un
 * 401, el refresco fallaba, la sesión se borraba y el aviso salía al vacío. La
 * pantalla, que ya había decidido empezar en «Mi jornada», se quedaba ahí para
 * siempre: sin token, con «Token de autenticación ausente, caducado o
 * inválido», y con un «Reintentar» que no podía arreglar nada.
 *
 * Un estado no se pierde: quien empieza a mirar tarde ve que hay una
 * caducidad pendiente y actúa. Se apaga al atenderla, para que girar el móvil
 * en el login (que recrea la pantalla y vuelve a mirar) no navegue otra vez y
 * borre lo que se estaba escribiendo.
 *
 * Aparte de `SessionManager` para poder probarlo sin Android.
 */
class SenalDeSesionCaducada {

    private val _pendiente = MutableStateFlow(false)

    /** `true` desde que la sesión caduca hasta que alguien lleva al login. */
    val pendiente: StateFlow<Boolean> = _pendiente.asStateFlow()

    /** La sesión acaba de morir sin que el usuario la cerrara. */
    fun avisar() {
        _pendiente.value = true
    }

    /** Ya se ha llevado al login, o se ha vuelto a entrar: no queda nada que hacer. */
    fun atendida() {
        _pendiente.value = false
    }
}
