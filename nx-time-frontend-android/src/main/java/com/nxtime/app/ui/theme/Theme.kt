package com.nxtime.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EsquemaClaro = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh
)

private val EsquemaOscuro = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh
)

/**
 * Da acceso a los colores de estado de jornada desde cualquier pantalla,
 * ya resueltos para el tema activo.
 */
val LocalColoresJornada = staticCompositionLocalOf { ColoresJornadaClaro }

/**
 * Tema de la aplicación.
 *
 * Deliberadamente **no** usa color dinámico (el que Android 12+ saca del
 * fondo de pantalla): NX Time es una herramienta corporativa y el color
 * es parte de su identidad, no una preferencia del usuario. Además, con
 * color dinámico las capturas del README saldrían distintas en cada
 * móvil.
 *
 * El tema oscuro sigue al del sistema. Aquí eso es seguro por primera
 * vez: al venir TODOS los colores de `MaterialTheme.colorScheme`, ya no
 * existe ningún fondo fijo que pueda quedarse claro con el texto en
 * blanco, que es lo que hacía ilegible a la versión anterior.
 */
@Composable
fun NxTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val esquema = if (darkTheme) EsquemaOscuro else EsquemaClaro
    val coloresJornada = if (darkTheme) ColoresJornadaOscuro else ColoresJornadaClaro

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Los iconos de la barra de estado se invierten con el tema:
            // antes estaban fijados a "oscuros" en el tema base, así que
            // en modo oscuro desaparecían sobre fondo oscuro.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalColoresJornada provides coloresJornada) {
        MaterialTheme(
            colorScheme = esquema,
            typography = NxTimeTypography,
            content = content
        )
    }
}
