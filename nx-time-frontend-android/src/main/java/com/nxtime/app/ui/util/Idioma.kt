package com.nxtime.app.ui.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Fija el idioma de la aplicación en español, sin depender del que tenga
 * configurado el dispositivo.
 *
 * El problema que resuelve se ve en el selector de fechas de "Solicitar
 * ausencia": en un móvil en inglés, el diálogo de Material salía con
 * "Select date", "August 2026" y las iniciales `S M T W T F S`, mientras
 * el título de la pantalla y los botones ("Cancelar", "Guardar") estaban
 * en español. Dos idiomas en la misma pantalla.
 *
 * La causa es que los textos propios viven en `res/values` -- la carpeta
 * *por defecto*, la que se usa cuando ninguna otra encaja -- así que
 * salen en español pase lo que pase. Los componentes de Material, en
 * cambio, sí traen `values-es`, `values-en` y decenas más, y resuelven
 * contra el idioma del dispositivo. Resultado: la app nunca se traduce y
 * los componentes sí.
 *
 * Se fija el idioma en vez de traducir la app porque NX Time es una
 * herramienta de jornada laboral española: es la misma decisión, y por
 * el mismo motivo, que la de [DateFormats], que clava `Europe/Madrid`
 * para que un empleado de viaje siga viendo su jornada en la hora de su
 * centro de trabajo.
 *
 * Se hace por `Configuration` y no con `AppCompatDelegate.setApplicationLocales`
 * a propósito: esa API arrastraría AppCompat de vuelta, y la MainActivity
 * pasó a `ComponentActivity` justo para quitárselo de encima.
 *
 * No se toca `Locale.setDefault`: eso es estado global del proceso y aquí
 * no hace falta. Compose resuelve tanto los textos como el calendario a
 * partir de la `Configuration` del contexto de la Activity, que es
 * exactamente lo que devuelve esta función.
 */
fun Context.enEspanol(): Context {
    val espanol = Locale.forLanguageTag(IDIOMA_APP)
    val configuracion = Configuration(resources.configuration)

    // setLocales y no setLocale: la lista completa es lo que consultan
    // los componentes del framework para elegir traducción, y dejar ahí
    // el idioma anterior haría que algunos siguieran resolviendo con él.
    configuracion.setLocales(LocaleList(espanol))

    return createConfigurationContext(configuracion)
}

/** El único idioma de la aplicación. */
private const val IDIOMA_APP = "es-ES"
