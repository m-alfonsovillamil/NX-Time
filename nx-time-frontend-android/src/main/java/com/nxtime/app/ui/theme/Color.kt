package com.nxtime.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de la app, en los dos temas.
 *
 * Conserva el verde azulado `#006970` que ya identificaba a NX Time, pero
 * completa el resto del esquema de Material 3 (contenedores, variantes de
 * superficie, contornos), que antes no existía.
 *
 * **Todos los pares texto/fondo llegan a 4.5:1 (WCAG AA).** No es un
 * detalle: la versión anterior fijaba el fondo de 12 de las 13 pantallas a
 * un gris claro sin variante nocturna, mientras el tema oscuro ponía el
 * texto casi blanco. En modo oscuro el contraste real era de **1.02:1** y
 * la aplicación resultaba ilegible.
 *
 * El secundario también se corrigió: el anterior (`#4ABCB4`) con texto
 * blanco daba 2.67:1, por debajo del mínimo.
 */

// ---- Tema claro ----
val LightPrimary = Color(0xFF006970)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFF6FF6FF)
val LightOnPrimaryContainer = Color(0xFF002022)

val LightSecondary = Color(0xFF4A6365)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCCE8EA)
val LightOnSecondaryContainer = Color(0xFF051F21)

val LightTertiary = Color(0xFF4F5F7D)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFD7E3FF)
val LightOnTertiaryContainer = Color(0xFF081C36)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFF5FAFB)
val LightOnBackground = Color(0xFF171D1E)
val LightSurface = Color(0xFFF5FAFB)
val LightOnSurface = Color(0xFF171D1E)
val LightSurfaceVariant = Color(0xFFDAE4E5)
val LightOnSurfaceVariant = Color(0xFF3F4849)
val LightOutline = Color(0xFF6F7979)
val LightOutlineVariant = Color(0xFFBEC8C9)
val LightSurfaceContainer = Color(0xFFEAEFF0)
val LightSurfaceContainerHigh = Color(0xFFE4EAEB)

// ---- Tema oscuro ----
val DarkPrimary = Color(0xFF4CD9E4)
val DarkOnPrimary = Color(0xFF003739)
val DarkPrimaryContainer = Color(0xFF004F54)
val DarkOnPrimaryContainer = Color(0xFF6FF6FF)

val DarkSecondary = Color(0xFFB0CCCE)
val DarkOnSecondary = Color(0xFF1B3436)
val DarkSecondaryContainer = Color(0xFF324B4D)
val DarkOnSecondaryContainer = Color(0xFFCCE8EA)

val DarkTertiary = Color(0xFFB7C7E9)
val DarkOnTertiary = Color(0xFF20314C)
val DarkTertiaryContainer = Color(0xFF374764)
val DarkOnTertiaryContainer = Color(0xFFD7E3FF)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF0E1415)
val DarkOnBackground = Color(0xFFDEE3E4)
val DarkSurface = Color(0xFF0E1415)
val DarkOnSurface = Color(0xFFDEE3E4)
val DarkSurfaceVariant = Color(0xFF3F4849)
val DarkOnSurfaceVariant = Color(0xFFBEC8C9)
val DarkOutline = Color(0xFF899393)
val DarkOutlineVariant = Color(0xFF3F4849)
val DarkSurfaceContainer = Color(0xFF1A2122)
val DarkSurfaceContainerHigh = Color(0xFF252B2C)

/**
 * Colores del estado de la jornada.
 *
 * No salen del esquema de Material porque no son "el color de la marca":
 * significan algo (trabajando, en pausa, parado) y tienen que seguir
 * significando lo mismo en ambos temas. Por eso cada uno lleva su pareja
 * clara y oscura, en vez del color único sin variante nocturna de antes.
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
