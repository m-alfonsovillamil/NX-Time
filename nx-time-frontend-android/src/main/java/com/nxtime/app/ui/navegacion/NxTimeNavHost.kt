package com.nxtime.app.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nxtime.app.R
import com.nxtime.app.data.session.SessionManager
import com.nxtime.app.ui.acceso.LoginScreen
import com.nxtime.app.ui.acceso.RegistroEmpresaScreen
import com.nxtime.app.ui.auditoria.AuditoriaScreen
import com.nxtime.app.ui.auditoria.CorregirFichajeScreen
import com.nxtime.app.ui.ausencias.AusenciasScreen
import com.nxtime.app.ui.ausencias.SolicitudScreen
import com.nxtime.app.ui.fichar.FicharScreen
import com.nxtime.app.ui.gestion.AltaUsuarioScreen
import com.nxtime.app.ui.gestion.AusenciasEquipoScreen
import com.nxtime.app.ui.gestion.HistorialEquipoScreen
import com.nxtime.app.ui.gestion.PanelGestionScreen
import com.nxtime.app.ui.historial.HistorialScreen
import com.nxtime.app.ui.usuario.CambiarContrasenaScreen
import com.nxtime.app.ui.util.Permisos
import com.nxtime.app.ui.util.Rol
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    ALTA_USUARIO("alta/{$ARG_ES_GESTOR}"),
    AUDITORIA("auditoria/{$ARG_FICHAJE_ID}"),
    CORRECCION("correccion/{$ARG_FICHAJE_ID}?$ARG_NOMBRE={$ARG_NOMBRE}" +
            "&$ARG_ENTRADA={$ARG_ENTRADA}&$ARG_SALIDA={$ARG_SALIDA}");

    companion object {
        /** Ausencias del equipo, ya resueltas o pendientes de responder. */
        fun ausenciasEquipo(resueltas: Boolean) = "ausencias-equipo/$resueltas"

        /** Alta de un empleado o de otro gestor: el mismo formulario. */
        fun altaUsuario(esGestor: Boolean) = "alta/$esGestor"

        /** Línea temporal de cambios de un fichaje. */
        fun auditoria(fichajeId: Long) = "auditoria/$fichajeId"

        /**
         * Formulario de corrección.
         *
         * El nombre y las dos horas viajan en la ruta para poder
         * precargar el formulario sin volver a pedir el fichaje al
         * servidor: la pantalla anterior ya los tiene delante. Van
         * codificados porque un instante ISO lleva ":" y un nombre puede
         * llevar espacios o acentos, y sin codificar romperían la ruta.
         */
        fun correccion(
            fichajeId: Long,
            nombre: String,
            entradaIso: String?,
            salidaIso: String?
        ) = "correccion/$fichajeId" +
                "?$ARG_NOMBRE=${codificar(nombre)}" +
                "&$ARG_ENTRADA=${codificar(entradaIso)}" +
                "&$ARG_SALIDA=${codificar(salidaIso)}"

        /**
         * `URLEncoder` codifica el espacio como `+` (es codificación de
         * formulario), pero quien decodifica la ruta al otro lado es
         * Navigation, que solo entiende `%20`: sin esta sustitución,
         * "Lucía Moreno" llegaba a la pantalla como **"Lucía+Moreno"**.
         */
        private fun codificar(valor: String?): String =
            URLEncoder.encode(valor.orEmpty(), StandardCharsets.UTF_8.name())
                .replace("+", "%20")
    }
}

const val ARG_RESUELTAS = "resueltas"
const val ARG_ES_GESTOR = "esGestor"
const val ARG_FICHAJE_ID = "fichajeId"
const val ARG_NOMBRE = "nombre"
const val ARG_ENTRADA = "entrada"
const val ARG_SALIDA = "salida"

/**
 * Los destinos que salen en la barra de navegación.
 *
 * Son las cuatro zonas entre las que se salta constantemente. El resto
 * de pantallas (solicitar, cambiar contraseña, alta de usuario...) son
 * hojas: se entra desde una de estas cuatro y se vuelve con "atrás", así
 * que ocupar una pestaña con ellas solo restaría sitio.
 *
 * Sustituye al menú de tres puntos y al muro de botones que había al pie
 * de "Mi jornada": llegar al historial son ahora cero pasos en vez de
 * uno, y se ve de un vistazo qué zonas tiene la aplicación.
 */
enum class DestinoPrincipal(
    val pantalla: Pantalla,
    val icono: ImageVector,
    @param:StringRes val etiqueta: Int
) {
    JORNADA(Pantalla.FICHAR, Icons.Default.Schedule, R.string.barra_jornada),
    HISTORIAL(Pantalla.HISTORIAL, Icons.AutoMirrored.Filled.ListAlt, R.string.barra_historial),
    AUSENCIAS(Pantalla.AUSENCIAS, Icons.Default.EventBusy, R.string.barra_ausencias),
    GESTION(Pantalla.GESTION, Icons.Default.Groups, R.string.barra_gestion)
}

/**
 * Grafo de navegación de la aplicación.
 *
 * @param sesionIniciada si ya hay un token guardado. Se decide fuera,
 *   en [com.nxtime.app.MainActivity], y solo se lee una vez: si se
 *   consultara aquí en cada recomposición, cerrar sesión provocaría una
 *   carrera entre el borrado del token y la propia navegación.
 * @param sessionManager para saber el rol (decide si la pestaña de
 *   gestión existe) y para enterarse de que la sesión ha caducado.
 */
@OptIn(
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class
)
@Composable
fun NxTimeNavHost(
    sesionIniciada: Boolean,
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController()
) {
    val inicio = if (sesionIniciada) Pantalla.FICHAR.ruta else Pantalla.LOGIN.ruta

    /*
     * El rol se guarda en estado y se relee SOLO al entrar o salir de la
     * sesión, por el mismo motivo que `sesionIniciada` se lee una vez:
     * consultarlo en cada recomposición devolvería null a mitad del
     * cierre de sesión y la barra parpadearía mientras se navega.
     */
    var rol by remember { mutableStateOf(Rol.de(sessionManager.fetchUserRole())) }

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
        rol = Rol.de(sessionManager.fetchUserRole())
        navController.navigate(destino.ruta) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    /*
     * Sesión caducada: el refresh token ya no vale y el `Authenticator`
     * de RetrofitClient acaba de borrar la sesión. Antes esto no llevaba
     * a ninguna parte y la app se quedaba en "Mi jornada" con el nombre
     * cacheado y un banner de error, sin salida salvo cerrar sesión a
     * mano.
     */
    LaunchedEffect(sessionManager) {
        sessionManager.sesionCaducada.collect { entrarA(Pantalla.LOGIN) }
    }

    /* Salto entre pestañas: ni apila ni recarga. */
    fun irAPestana(destino: DestinoPrincipal) {
        navController.navigate(destino.pantalla.ruta) {
            // "Mi jornada" es la raíz de la zona con sesión, así que es
            // ella la que ancla la pila: sin esto, ir y volver entre
            // pestañas dejaría un rastro de pantallas que el botón
            // "atrás" tendría que deshacer una a una.
            popUpTo(Pantalla.FICHAR.ruta) { saveState = true }
            launchSingleTop = true
            // Devuelve la lista donde se dejó, con su desplazamiento.
            restoreState = true
        }
    }

    val entradaActual by navController.currentBackStackEntryAsState()
    val rutaActual = entradaActual?.destination?.route

    val destinos = DestinoPrincipal.entries.filter {
        it != DestinoPrincipal.GESTION || Permisos.puedeGestionarEquipo(rol)
    }
    val destinoActual = destinos.firstOrNull { it.pantalla.ruta == rutaActual }

    NavigationSuiteScaffold(
        /*
         * La barra solo existe en los cuatro destinos principales. En el
         * login, en un formulario o en una pantalla de detalle estorba:
         * ofrecería saltar a otra zona en mitad de algo a medio hacer, y
         * en el login llevaría a pantallas sin sesión.
         *
         * Cuando sí toca, el tipo lo decide el tamaño de la ventana:
         * barra abajo en un móvil, raíl al lado en tablet o plegable
         * abierto. Es todo lo que hay que escribir para que la app se
         * adapte; no hay un segundo layout en ninguna parte.
         */
        navigationSuiteType = if (destinoActual == null) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
        },
        navigationItems = {
            destinos.forEach { destino ->
                NavigationSuiteItem(
                    selected = destino == destinoActual,
                    onClick = { irAPestana(destino) },
                    // Sin descripción: la etiqueta de al lado ya dice lo
                    // mismo, y anunciarlo dos veces molesta a un lector
                    // de pantalla más de lo que ayuda.
                    icon = { Icon(destino.icono, contentDescription = null) },
                    label = { Text(stringResource(destino.etiqueta)) }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = inicio,
            /*
             * Movimiento en vez del corte seco de antes. Entrar en una
             * pantalla la desliza desde la derecha y volver la devuelve
             * por donde vino, que es lo que hace entender de un vistazo
             * si se está profundizando o retrocediendo.
             */
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(DURACION_TRANSICION)
                ) + fadeIn(tween(DURACION_TRANSICION))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(DURACION_TRANSICION)
                ) + fadeOut(tween(DURACION_TRANSICION))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(DURACION_TRANSICION)
                ) + fadeIn(tween(DURACION_TRANSICION))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(DURACION_TRANSICION)
                ) + fadeOut(tween(DURACION_TRANSICION))
            }
        ) {

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
                    onIrSolicitud = { navController.navigate(Pantalla.SOLICITUD.ruta) },
                    onIrContrasena = { navController.navigate(Pantalla.CONTRASENA.ruta) },
                    onCerrarSesion = { entrarA(Pantalla.LOGIN) }
                )
            }

            // Historial, ausencias y gestión son destinos de la barra:
            // se llega a ellos por la pestaña, no con una flecha de
            // volver, así que ya no reciben `onVolver`.
            composable(Pantalla.HISTORIAL.ruta) {
                HistorialScreen()
            }

            composable(Pantalla.AUSENCIAS.ruta) {
                AusenciasScreen(
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
                    // Solo ADMIN tiene la authority `gestor:crear`. Antes
                    // la opción se le ofrecía a cualquier gestor y el
                    // backend respondía 403 sin falta.
                    puedeCrearGestores = Permisos.puedeCrearGestores(rol),
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
                HistorialEquipoScreen(
                    onVolver = navController::navigateUp,
                    // Corregir y auditar son operaciones de cumplimiento
                    // normativo: `fichaje:corregir` y `fichaje:auditoria`
                    // las tienen RRHH y ADMIN, no un GESTOR cualquiera.
                    puedeCorregir = Permisos.puedeCorregirFichajes(rol),
                    puedeAuditar = Permisos.puedeVerAuditoria(rol),
                    onCorregir = { registro ->
                        navController.navigate(
                            Pantalla.correccion(
                                fichajeId = registro.id,
                                nombre = registro.usuario.nombre,
                                entradaIso = registro.horaEntrada,
                                salidaIso = registro.horaSalida
                            )
                        )
                    },
                    onVerAuditoria = { registro ->
                        navController.navigate(Pantalla.auditoria(registro.id))
                    }
                )
            }

            composable(
                route = Pantalla.AUDITORIA.ruta,
                arguments = listOf(navArgument(ARG_FICHAJE_ID) { type = NavType.LongType })
            ) {
                AuditoriaScreen(onVolver = navController::navigateUp)
            }

            composable(
                route = Pantalla.CORRECCION.ruta,
                arguments = listOf(
                    navArgument(ARG_FICHAJE_ID) { type = NavType.LongType },
                    navArgument(ARG_NOMBRE) { type = NavType.StringType; defaultValue = "" },
                    navArgument(ARG_ENTRADA) { type = NavType.StringType; defaultValue = "" },
                    navArgument(ARG_SALIDA) { type = NavType.StringType; defaultValue = "" }
                )
            ) { entrada ->
                CorregirFichajeScreen(
                    nombreEmpleado = entrada.arguments?.getString(ARG_NOMBRE).orEmpty(),
                    entradaIso = entrada.arguments?.getString(ARG_ENTRADA)?.ifBlank { null },
                    salidaIso = entrada.arguments?.getString(ARG_SALIDA)?.ifBlank { null },
                    // Tras corregir se vuelve al historial, que recarga
                    // al reanudarse (ver HistorialEquipoScreen): el
                    // fichaje corregido es OTRO registro, y dejar en
                    // pantalla el original ya anulado engañaría.
                    onCorregido = navController::navigateUp,
                    onVolver = navController::navigateUp
                )
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
}

/** Milisegundos de las transiciones entre pantallas. */
private const val DURACION_TRANSICION = 300
