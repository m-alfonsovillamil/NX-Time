package com.nxtime.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Las formas de la app.
 *
 * **Este fichero no existía.** `NxTimeTheme` nunca pasaba un `Shapes()`,
 * así que toda la aplicación usaba los redondeos por defecto de Material
 * -- 4 / 8 / 12 / 16 / 28 dp -- sin que nadie lo hubiera decidido. Era la
 * palanca de diseño más barata del proyecto: un solo fichero alcanza a
 * las once pantallas de golpe, porque casi todo (tarjetas, botones,
 * campos, diálogos, menús) lee de aquí.
 *
 * La escala es generosa a propósito, que es de lo que va la dirección
 * "Fichaje": las tarjetas flotan sobre un fondo con degradado y unas
 * esquinas apretadas las devolverían al aspecto de tabla.
 *
 * | Rol | Antes | Ahora | Dónde se ve |
 * |---|---|---|---|
 * | `extraSmall` | 4 | 8 | distintivos de estado de una ausencia |
 * | `small` | 8 | 16 | banners de error, chips |
 * | `medium` | 12 | 20 | tarjetas de jornada, de ausencia, indicadores |
 * | `large` | 16 | 28 | hojas y contenedores grandes |
 * | `extraLarge` | 28 | 36 | diálogos |
 *
 * El botón de fichar **no** sale de aquí: se anima entre dos formas según
 * el estado de la jornada, y eso vive en `FicharScreen`.
 */
val NxTimeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/**
 * El redondeo de las tarjetas que se dibujan a mano con `Card`, que en
 * Material 3 usa `shapes.medium` por defecto pero que aquí conviene tener
 * con nombre: varias pantallas lo necesitan para que un contenedor y lo
 * que lleva dentro coincidan en la esquina.
 */
val RadioTarjeta = 20.dp

/** Las esquinas del botón de fichar cuando hay una jornada abierta. */
val RadioBotonActivo = 44.dp

/**
 * La sombra de las tarjetas de contenido.
 *
 * En la dirección "Fichaje" las tarjetas **flotan** sobre el degradado, y
 * en el tema claro son blancas sobre un fondo casi blanco: sin sombra no
 * se despegan del fondo y el efecto no existe. `Card` viene de fábrica a
 * elevación 0, así que hay que pedirlo.
 *
 * Se queda en 3 dp, no más: una sombra marcada en una lista de doce
 * jornadas ensucia más de lo que ordena. En el tema oscuro apenas se ve,
 * y ahí el trabajo lo hace la diferencia de tono entre `surface` y el
 * fondo.
 */
@Composable
fun elevacionDeTarjeta() = CardDefaults.cardElevation(defaultElevation = 3.dp)
