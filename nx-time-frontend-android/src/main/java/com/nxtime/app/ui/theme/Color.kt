package com.nxtime.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de la app, en los dos temas.
 *
 * Dirección visual "Fichaje": el verde azulado de siempre, pero más
 * luminoso (`#0E7C86` en vez de `#006970`), superficies que flotan sobre
 * un fondo con degradado, y una segunda familia de color -- índigo --
 * reservada a las pantallas de gestión, para que se distingan de un
 * vistazo de las del empleado.
 *
 * **Todos los pares texto/fondo llegan a 4.5:1 (WCAG AA)**, comprobados
 * uno a uno antes de escribirlos. No es un detalle: una versión anterior
 * fijaba el fondo de 12 de las 13 pantallas a un gris claro sin variante
 * nocturna mientras el tema oscuro ponía el texto casi blanco, y el
 * contraste real era de **1.02:1**: la aplicación resultaba ilegible.
 *
 * El par más justo de esta paleta es `primary` sobre el inicio del
 * degradado claro, a 4.67:1. Si alguien aclara el fondo o oscurece el
 * primario, ese es el que cae primero.
 */

// ---- Tema claro ----
val LightPrimary = Color(0xFF0E7C86)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFCDEBEE)
val LightOnPrimaryContainer = Color(0xFF00363B)

val LightSecondary = Color(0xFF44646A)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFC9E7EB)
val LightOnSecondaryContainer = Color(0xFF042023)

/**
 * El índigo de gestión.
 *
 * Va en el hueco de `tertiary` a propósito: Material ya reserva ese papel
 * para "un acento que no compite con el primario", que es exactamente
 * para lo que se usa aquí -- el panel de empresa y las acciones de RRHH.
 */
val LightTertiary = Color(0xFF5B5BD6)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFE2E1FF)
val LightOnTertiaryContainer = Color(0xFF161268)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFF2FAFB)
val LightOnBackground = Color(0xFF0F1B1D)

/**
 * Las tarjetas son BLANCAS y flotan sobre el degradado. Por eso
 * `surface` no coincide con `background`, que es lo que hacía la paleta
 * anterior: aquí esa diferencia es justo el efecto que se busca.
 */
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0F1B1D)
val LightSurfaceVariant = Color(0xFFD9E7E9)
val LightOnSurfaceVariant = Color(0xFF4A5C5E)
val LightOutline = Color(0xFF6C7E80)
val LightOutlineVariant = Color(0xFFBFD2D4)
val LightSurfaceContainer = Color(0xFFFFFFFF)
val LightSurfaceContainerHigh = Color(0xFFF4FAFB)

/**
 * Los dos extremos del degradado del fondo.
 *
 * El salto es deliberadamente visible. La primera versión iba de
 * `#F2FAFB` a `#E4F1F3` y era tan sutil que no se leía como una
 * decisión, sino como un artefacto de renderizado: se pagaba el coste
 * (rehacer el tema oscuro, scaffolds transparentes) sin cobrar el
 * beneficio. O se nota, o sobra.
 *
 * Hasta dónde puede llegar lo fija el contraste, y ahí hubo que elegir:
 * con texto `primary` puesto directamente sobre el fondo, el degradado
 * más oscuro que aún daba 4,5:1 saltaba **menos** que el original
 * invisible. No se puede tener las dos cosas. La solución no fue
 * suavizar el degradado sino **dejar de poner teal sobre el fondo**: el
 * saludo va en tinta y "Solicitar ausencia" pasó a botón tonal. Así el
 * salto de luminancia es de 0,143 -- casi el doble que antes -- y lo que
 * queda encima llega a 5,8:1 en el peor caso.
 */
val LightFondoArriba = Color(0xFFF4FCFD)
val LightFondoAbajo = Color(0xFFD9EDF0)

// ---- Tema oscuro ----
val DarkPrimary = Color(0xFF5FD4DE)
val DarkOnPrimary = Color(0xFF00363B)
val DarkPrimaryContainer = Color(0xFF00525A)
val DarkOnPrimaryContainer = Color(0xFF8FF2FA)

val DarkSecondary = Color(0xFFACCBD0)
val DarkOnSecondary = Color(0xFF163438)
val DarkSecondaryContainer = Color(0xFF2D4B4F)
val DarkOnSecondaryContainer = Color(0xFFC9E7EB)

val DarkTertiary = Color(0xFFAFAFF7)
val DarkOnTertiary = Color(0xFF1C1C63)
val DarkTertiaryContainer = Color(0xFF3A3A9E)
val DarkOnTertiaryContainer = Color(0xFFE2E1FF)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF0C1416)
val DarkOnBackground = Color(0xFFE2EBEC)

/**
 * En oscuro las tarjetas **no** son blancas: son una superficie elevada.
 * Copiar el blanco del tema claro es el error clásico que deja la app
 * ilegible de noche.
 */
val DarkSurface = Color(0xFF182325)
val DarkOnSurface = Color(0xFFE2EBEC)
val DarkSurfaceVariant = Color(0xFF3E4E50)
val DarkOnSurfaceVariant = Color(0xFFA8BABB)
val DarkOutline = Color(0xFF869899)
val DarkOutlineVariant = Color(0xFF3E4E50)
val DarkSurfaceContainer = Color(0xFF182325)
val DarkSurfaceContainerHigh = Color(0xFF1F2C2E)

/**
 * El degradado oscuro va al revés que el claro -- de más oscuro arriba a
 * algo más claro abajo -- para que la barra de navegación no se hunda en
 * negro y siga leyéndose como una superficie.
 */
val DarkFondoArriba = Color(0xFF0A1113)
val DarkFondoAbajo = Color(0xFF16292C)

/**
 * Colores del estado de la jornada.
 *
 * No salen del esquema de Material porque no son "el color de la marca":
 * significan algo (trabajando, en pausa, parado) y tienen que seguir
 * significando lo mismo en ambos temas. Por eso cada uno lleva su pareja
 * clara y oscura, en vez del color único sin variante nocturna de antes.
 *
 * **Esta dirección los conserva tal cual.** Que el color del botón sea el
 * estado de la jornada -- se ve de lejos si estás fichando sin leer nada
 * -- es la mejor idea del diseño anterior y no se toca.
 */
data class ColoresJornada(
    val trabajando: Color,
    val onTrabajando: Color,
    val enPausa: Color,
    val onEnPausa: Color,
    val parado: Color,
    val onParado: Color
)

val ColoresJornadaClaro = ColoresJornada(
    trabajando = Color(0xFF2E6B34), onTrabajando = Color(0xFFFFFFFF),
    enPausa = Color(0xFF8A5100), onEnPausa = Color(0xFFFFFFFF),
    parado = Color(0xFFB3261E), onParado = Color(0xFFFFFFFF)
)

val ColoresJornadaOscuro = ColoresJornada(
    trabajando = Color(0xFF7ADB84), onTrabajando = Color(0xFF07230B),
    enPausa = Color(0xFFFFB865), onEnPausa = Color(0xFF4A2800),
    parado = Color(0xFFFFB4AB), onParado = Color(0xFF601410)
)
