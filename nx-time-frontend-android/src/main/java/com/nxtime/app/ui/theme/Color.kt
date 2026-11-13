package com.nxtime.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de la app, en los dos temas.
 *
 * Dirección visual "Fichaje", con la paleta **arena** (ADR 057): el fondo
 * es arena -- el reloj de arena, el tiempo --, las acciones van en azul
 * noche y las tarjetas en marfil, flotando sobre un fondo con degradado.
 * El verde petróleo, que hasta noviembre de 2026 era el primario, queda
 * para las pantallas de gestión, que así se distinguen de un vistazo de
 * las del empleado.
 *
 * El arena es un color de **fondo**, nunca de botón: es claro y poco
 * saturado, y un botón arena con texto blanco no se lee.
 *
 * **Todos los pares texto/fondo llegan a 4.5:1 (WCAG AA)**, comprobados
 * uno a uno antes de escribirlos. No es un detalle: una versión anterior
 * fijaba el fondo de 12 de las 13 pantallas a un gris claro sin variante
 * nocturna mientras el tema oscuro ponía el texto casi blanco, y el
 * contraste real era de **1.02:1**: la aplicación resultaba ilegible.
 *
 * El par más justo de esta paleta es `tertiary` sobre el final del
 * degradado claro, a 4.84:1. Si alguien oscurece el fondo o aclara el
 * petróleo, ese es el que cae primero.
 */

// ---- Tema claro ----
val LightPrimary = Color(0xFF1F3A5F)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD6E0EF)
val LightOnPrimaryContainer = Color(0xFF0B1E38)

val LightSecondary = Color(0xFF655A43)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE8DCC3)
val LightOnSecondaryContainer = Color(0xFF2A2415)

/**
 * El petróleo de gestión (antes, índigo).
 *
 * Va en el hueco de `tertiary` a propósito: Material ya reserva ese papel
 * para "un acento que no compite con el primario", que es exactamente
 * para lo que se usa aquí -- el panel de empresa y las acciones de RRHH.
 * Con el fondo arena, un índigo además del azul eran demasiados tonos.
 */
val LightTertiary = Color(0xFF0A6570)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFCDE7E8)
val LightOnTertiaryContainer = Color(0xFF00363B)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFF2EAD9)
val LightOnBackground = Color(0xFF1C2327)

/**
 * Las tarjetas son MARFIL y flotan sobre el degradado. Por eso
 * `surface` no coincide con `background`: esa diferencia es justo el
 * efecto que se busca. Marfil y no blanco: el blanco puro sobre arena
 * queda duro, como recuadros vacíos.
 */
val LightSurface = Color(0xFFFFFAF1)
val LightOnSurface = Color(0xFF1C2327)
val LightSurfaceVariant = Color(0xFFE4D8C2)
val LightOnSurfaceVariant = Color(0xFF565040)
val LightOutline = Color(0xFF7B7361)
val LightOutlineVariant = Color(0xFFD6CBB5)
val LightSurfaceContainer = Color(0xFFFFFAF1)
val LightSurfaceContainerHigh = Color(0xFFFAF3E6)

/**
 * Los tres escalones que faltaban de la escala de superficies.
 *
 * No son un capricho de completitud: Material 3 tiene **cinco**
 * `surfaceContainer*`, y esta paleta solo definía dos. Los otros tres se
 * quedaban con el valor por defecto de `lightColorScheme()`, que es la
 * paleta morada base de Material —nada que ver con este tema— y salían
 * en pantalla en cuanto un componente los usaba sin que nadie los
 * pidiera: un `Card` sin `containerColor` explícito coge
 * `surfaceContainerLow`, y el fondo de un `Switch` apagado coge
 * `surfaceContainerHighest`. El calendario de la fase C fue lo primero
 * que usó los dos, y la rejilla apareció **sobre un rosa** que no está
 * en ninguna parte de la línea visual.
 *
 * Siguen la dirección que ya marcaban los dos existentes: en tema claro,
 * cuanto más "alto" el contenedor, un punto más oscuro.
 */
val LightSurfaceContainerLowest = Color(0xFFFFFAF1)
val LightSurfaceContainerLow = Color(0xFFFFFAF1)
val LightSurfaceContainerHighest = Color(0xFFF3EAD9)

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
 *
 * Con la paleta arena el degradado va de arena clara a arena tostada, y
 * el primario (azul noche) sí aguanta encima: 8,2:1 en el peor punto.
 */
val LightFondoArriba = Color(0xFFF6EFE1)
val LightFondoAbajo = Color(0xFFE6D9C1)

// ---- Tema oscuro ----
val DarkPrimary = Color(0xFFA9C7F2)
val DarkOnPrimary = Color(0xFF0B2A4D)
val DarkPrimaryContainer = Color(0xFF284566)
val DarkOnPrimaryContainer = Color(0xFFD6E4FA)

val DarkSecondary = Color(0xFFD2C5A8)
val DarkOnSecondary = Color(0xFF372F1B)
val DarkSecondaryContainer = Color(0xFF4A412C)
val DarkOnSecondaryContainer = Color(0xFFEEE2C6)

val DarkTertiary = Color(0xFF5FD4DE)
val DarkOnTertiary = Color(0xFF00363B)
val DarkTertiaryContainer = Color(0xFF00525A)
val DarkOnTertiaryContainer = Color(0xFF8FF2FA)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF14120E)
val DarkOnBackground = Color(0xFFECE6DA)

/**
 * En oscuro las tarjetas **no** son blancas: son una superficie elevada.
 * Copiar el blanco del tema claro es el error clásico que deja la app
 * ilegible de noche.
 */
val DarkSurface = Color(0xFF201D18)
val DarkOnSurface = Color(0xFFECE6DA)
val DarkSurfaceVariant = Color(0xFF4B4538)
val DarkOnSurfaceVariant = Color(0xFFBDB4A2)
val DarkOutline = Color(0xFF978E7C)
val DarkOutlineVariant = Color(0xFF4B4538)
val DarkSurfaceContainer = Color(0xFF201D18)
val DarkSurfaceContainerHigh = Color(0xFF28241E)

/** Los mismos tres escalones que en claro, pero aclarando hacia arriba. */
val DarkSurfaceContainerLowest = Color(0xFF0F0D0A)
val DarkSurfaceContainerLow = Color(0xFF1A1713)
val DarkSurfaceContainerHighest = Color(0xFF332E26)

/**
 * El degradado oscuro va al revés que el claro -- de más oscuro arriba a
 * algo más claro abajo -- para que la barra de navegación no se hunda en
 * negro y siga leyéndose como una superficie.
 */
val DarkFondoArriba = Color(0xFF100E0B)
val DarkFondoAbajo = Color(0xFF2A2419)

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
