package com.nxtime.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Adonde el navegador devuelve a la app tras un SSO: `nxtime://sso?…` (ADR 036).
 *
 * No pinta nada. Apunta con qué se ha vuelto, trae al frente la actividad de
 * siempre y se quita de en medio.
 *
 * **Es una actividad aparte, y no un `intent-filter` más en `MainActivity`**,
 * por lo que hay debajo al volver: la pestaña del navegador sigue encima de
 * `MainActivity`, en su misma tarea. Si el enlace lo atendiera ella con su modo
 * de lanzamiento normal, Android crearía una SEGUNDA `MainActivity` encima de
 * la pestaña, y al dar atrás la persona volvería al navegador y, detrás, a la
 * pantalla de acceso de antes. Desde aquí se la llama con `CLEAR_TOP`, que
 * cierra la pestaña y recupera la que ya estaba, sin cambiarle a `MainActivity`
 * cómo se abre desde el icono o desde un push.
 */
class VueltaDeSsoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vuelta = intent?.data
        (application as NxTimeApplication).accesoSso.recibir(
            codigo = vuelta?.getQueryParameter("codigo"),
            error = vuelta?.getQueryParameter("error"),
            vinculo = vuelta?.getQueryParameter("vinculo")
        )
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
