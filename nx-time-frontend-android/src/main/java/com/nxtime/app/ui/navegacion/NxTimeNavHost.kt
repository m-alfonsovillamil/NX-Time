package com.nxtime.app.ui.navegacion

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nxtime.app.ui.acceso.LoginScreen
import com.nxtime.app.ui.acceso.RegistroEmpresaScreen
import com.nxtime.app.ui.ausencias.AusenciasScreen
import com.nxtime.app.ui.ausencias.SolicitudScreen
import com.nxtime.app.ui.fichar.FicharScreen
import com.nxtime.app.ui.gestion.AltaUsuarioScreen
import com.nxtime.app.ui.gestion.AusenciasEquipoScreen
import com.nxtime.app.ui.gestion.HistorialEquipoScreen
import com.nxtime.app.ui.gestion.PanelGestionScreen
import com.nxtime.app.ui.historial.HistorialScreen
import com.nxtime.app.ui.usuario.CambiarContrasenaScreen

/**
 * Las pantallas de la aplicación.
 *
 * Antes esto vivía repartido entre doce entradas `<activity>` del
 * manifiesto y los `Intent` sueltos de cada Activity, así que no había
 * ningún sitio donde ver de un vistazo qué pantallas existen ni cómo se
 * llega a ellas. Un enum lo pone en un único lugar y, de paso, hace
 * imposible navegar a una ruta que no existe: escribir mal un destino
 * deja de compilar en vez de fallar al pulsar el botón.
 */
enum class Pantalla(val ruta: String) {
    LOGIN("login"),
    REGISTRO_EMPRESA("registro"),
    FICHAR("fichar"),
    HISTORIAL("historial"),
    AUSENCIAS("ausencias"),
    SOLICITUD("solicitud"),
    CONTRASENA("contrasena"),
    GESTION("gestion"),
    EQUIPO("equipo"),
    AUSENCIAS_EQUIPO("ausencias-equipo/{$ARG_RESUELTAS}"),
    ALTA_USUARIO("alta/{$ARG_ES_GESTOR}");

    companion object {
        /** Ausencias del equipo, ya resueltas o pendientes de responder. */
        fun ausenciasEquipo(resueltas: Boolean) = "ausencias-equipo/$resueltas"

        /** Alta de un empleado o de otro gestor: el mismo formulario. */
        fun altaUsuario(esGestor: Boolean) = "alta/$esGestor"
    }
}

const val ARG_RESUELTAS = "resueltas"
const val ARG_ES_GESTOR = "esGestor"

/**
 * Grafo de navegación de la aplicación.
 *
 * @param sesionIniciada si ya hay un token guardado. Se decide fuera,
 *   en [com.nxtime.app.MainActivity], y solo se lee una vez: si se
 *   consultara aquí en cada recomposición, cerrar sesión provocaría una
 *   carrera entre el borrado del token y la propia navegación.
 */
@Composable
fun NxTimeNavHost(
    sesionIniciada: Boolean,
    navController: NavHostController = rememberNavController()
) {
    val inicio = if (sesionIniciada) Pantalla.FICHAR.ruta else Pantalla.LOGIN.ruta

    /*
     * Entrar y salir de la sesión vacían la pila de atrás.
     *
     * Es lo que antes conseguía el `finish()` de `irAHome()`: sin esto,
     * el botón "atrás" desde la pantalla de fichar devolvería al login
     * con la sesión ya iniciada, y desde el login tras cerrar sesión
     * devolvería a una pantalla que ya no puede cargar nada porque el
     * token está borrado.
     */
    fun entrarA(destino: Pantalla) {
        navController.navigate(destino.ruta) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = inicio) {

        composable(Pantalla.LOGIN.ruta) {
            LoginScreen(
                onAccesoConcedido = { entrarA(Pantalla.FICHAR) },
                onIrRegistroEmpresa = { navController.navigate(Pantalla.REGISTRO_EMPRESA.ruta) }
            )
        }

        composable(Pantalla.REGISTRO_EMPRESA.ruta) {
            RegistroEmpresaScreen(
                onRegistrado = { entrarA(Pantalla.FICHAR) },
                onVolver = navController::navigateUp
            )
        }

        composable(Pantalla.FICHAR.ruta) {
            FicharScreen(
                onIrHistorial = { navController.navigate(Pantalla.HISTORIAL.ruta) },
                onIrAusencias = { navController.navigate(Pantalla.AUSENCIAS.ruta) },
                onIrSolicitud = { navController.navigate(Pantalla.SOLICITUD.ruta) },
                onIrContrasena = { navController.navigate(Pantalla.CONTRASENA.ruta) },
                onIrGestion = { navController.navigate(Pantalla.GESTION.ruta) },
                onCerrarSesion = { entrarA(Pantalla.LOGIN) }
            )
        }

        composable(Pantalla.HISTORIAL.ruta) {
            HistorialScreen(onVolver = navController::navigateUp)
        }

        composable(Pantalla.AUSENCIAS.ruta) {
            AusenciasScreen(
                onVolver = navController::navigateUp,
                onIrSolicitud = { navController.navigate(Pantalla.SOLICITUD.ruta) }
            )
        }

        composable(Pantalla.SOLICITUD.ruta) {
            SolicitudScreen(
                onEnviada = navController::navigateUp,
                onVolver = navController::navigateUp
            )
        }

        composable(Pantalla.CONTRASENA.ruta) {
            CambiarContrasenaScreen(
                onCambiada = navController::navigateUp,
                onVolver = navController::navigateUp
            )
        }

        composable(Pantalla.GESTION.ruta) {
            PanelGestionScreen(
                onVolver = navController::navigateUp,
                onIrHistorialEquipo = { navController.navigate(Pantalla.EQUIPO.ruta) },
                onIrPendientes = {
                    navController.navigate(Pantalla.ausenciasEquipo(resueltas = false))
                },
                onIrResueltas = {
                    navController.navigate(Pantalla.ausenciasEquipo(resueltas = true))
                },
                onIrAltaEmpleado = {
                    navController.navigate(Pantalla.altaUsuario(esGestor = false))
                },
                onIrAltaGestor = {
                    navController.navigate(Pantalla.altaUsuario(esGestor = true))
                }
            )
        }

        composable(Pantalla.EQUIPO.ruta) {
            HistorialEquipoScreen(onVolver = navController::navigateUp)
        }

        composable(
            route = Pantalla.AUSENCIAS_EQUIPO.ruta,
            arguments = listOf(navArgument(ARG_RESUELTAS) { type = NavType.BoolType })
        ) { entrada ->
            AusenciasEquipoScreen(
                resueltas = entrada.arguments?.getBoolean(ARG_RESUELTAS) ?: false,
                onVolver = navController::navigateUp
            )
        }

        composable(
            route = Pantalla.ALTA_USUARIO.ruta,
            arguments = listOf(navArgument(ARG_ES_GESTOR) { type = NavType.BoolType })
        ) { entrada ->
            AltaUsuarioScreen(
                esGestor = entrada.arguments?.getBoolean(ARG_ES_GESTOR) ?: false,
                onCreado = navController::navigateUp,
                onVolver = navController::navigateUp
            )
        }
    }
}
