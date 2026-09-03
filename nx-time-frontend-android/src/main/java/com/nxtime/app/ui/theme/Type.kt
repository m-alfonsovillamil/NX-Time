package com.nxtime.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nxtime.app.R

/**
 * Sora, la cara de la marca.
 *
 * Se usa **solo en titulares y cifras**, no en todo. El cuerpo sigue con
 * la fuente del sistema a propósito: es la que el usuario ya tiene
 * configurada, incluido el tamaño accesible que haya elegido, y cambiarla
 * por una nuestra en textos largos es quitarle esa decisión.
 *
 * Van tres pesos empaquetados (135 KB en total) en vez de la variable
 * completa: la app solo usa 400, 600 y 700.
 */
private val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold)
)

/**
 * Tipografía de la app.
 *
 * Parte de la escala por defecto de Material 3 y solo ajusta lo que la
 * app necesita de verdad, en lugar de redefinir los quince estilos por
 * costumbre.
 *
 * El reparto es deliberado:
 *
 *  - `displayMedium`, `headline*` y `title*` van con **Sora**: son el
 *    saludo, el cronómetro, los títulos de pantalla y los rótulos de las
 *    tarjetas. Lo que se mira, no lo que se lee.
 *  - `body*` y `label*` se quedan con la del **sistema**.
 *
 * `displayMedium` es el cronómetro. Lleva `letterSpacing` negativo porque
 * a ese tamaño las cifras de Sora respiran de sobra, y las cifras de esta
 * familia ya son tabulares: "01:33:26" no baila al pasar de segundo,
 * que es justo lo que arruina un cronómetro.
 */
val NxTimeTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)
