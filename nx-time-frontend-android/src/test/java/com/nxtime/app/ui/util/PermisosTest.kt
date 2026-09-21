package com.nxtime.app.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué se le enseña a quien ha iniciado sesión.
 *
 * **Lo que este fichero comprueba cambió en la Fase C3.** Antes, la app
 * deducía los permisos del rol con su propia copia de la jerarquía del
 * backend, así que aquí se probaba esa copia: "un GESTOR no puede crear
 * gestores". Ahora el reparto lo manda el servidor resuelto
 * (`authorities` en el login, en el refresco y en `GET /api/v1/perfil`) y
 * la copia ya no existe, así que **ese reparto se prueba donde vive**, en
 * `RoleAuthoritiesTest` del backend.
 *
 * Lo que queda aquí es lo único que la app todavía puede equivocarse:
 *
 * 1. **El emparejamiento** entre cada función y la cadena que comprueba.
 *    Escribir `fichaje:corrgir` seguiría compilando y apagaría el botón
 *    para todo el mundo, en silencio.
 * 2. **Qué pasa sin lista**: sin sesión, o con una sesión abierta por una
 *    versión anterior de la app que no la guardó. La respuesta correcta
 *    es "no se enseña nada"; la contraria abriría pantallas de gestión a
 *    quien no debe verlas.
 *
 * El defecto histórico que motivó esta clase —la app ofrecía "Crear
 * gestor" a un GESTOR, que recibía un 403— ya no puede repetirse por
 * desincronización: si el servidor no manda `gestor:crear`, el botón no
 * sale.
 */
class PermisosTest {

    /*
     * Tal y como las manda el backend para cada rol. Son fixtures, no una
     * copia que autorice nada: si el reparto cambia allí, lo que falla es
     * RoleAuthoritiesTest, y la app se entera sola en tiempo de ejecución.
     */
    private val deEmpleado = setOf(
        "fichaje:leer", "fichaje:escribir", "ausencia:leer", "ausencia:escribir",
        "adjunto:subir", "calendario:leer", "proyecto:leer", "correccion:solicitar",
        "denuncia:crear", "oferta:leer", "candidatura:crear"
    )
    private val deGestor = deEmpleado + setOf(
        "fichaje:leer:equipo", "ausencia:aprobar", "ausencia:leer:equipo",
        "empleado:crear", "empleado:leer", "calendario:gestionar",
        "proyecto:gestionar", "correccion:aprobar", "horasextra:revisar",
        "oferta:publicar", "candidatura:gestionar"
    )
    private val deRrhh = deGestor + setOf(
        "empleado:gestionar", "empleado:configurar", "departamento:gestionar",
        "correccion:disputa:resolver", "fichaje:corregir", "fichaje:auditoria",
        "informe:exportar"
    )
    private val deAdmin = deRrhh + setOf("gestor:crear", "denuncia:instruir")

    /**
     * Sin lista no se enseña nada.
     *
     * Pasa de verdad, y no solo al estar sin sesión: una sesión abierta
     * con una versión anterior de la app no tiene la clave guardada. Lo
     * correcto es apagar el menú hasta que `GET /perfil` la rellene, no
     * abrirlo por si acaso.
     */
    @Test
    fun `sin authorities no se ofrece nada`() {
        val nada = emptySet<String>()
        assertFalse(Permisos.puedeGestionarEquipo(nada))
        assertFalse(Permisos.puedeAprobarAusencias(nada))
        assertFalse(Permisos.puedeCrearEmpleados(nada))
        assertFalse(Permisos.puedeGestionarEmpleados(nada))
        assertFalse(Permisos.puedeConfigurarEmpleados(nada))
        assertFalse(Permisos.puedeCorregirFichajes(nada))
        assertFalse(Permisos.puedeVerAuditoria(nada))
        assertFalse(Permisos.puedeExportarInformes(nada))
        assertFalse(Permisos.puedeCrearGestores(nada))
        assertFalse(Permisos.puedeVerPanelEmpresa(nada))
        assertFalse(Permisos.puedeGestionarCalendario(nada))
        assertFalse(Permisos.puedeGestionarProyectos(nada))
        assertFalse(Permisos.puedeRevisarHorasExtra(nada))
        assertFalse(Permisos.puedeInstruirDenuncias(nada))
        assertFalse(Permisos.puedePublicarOfertas(nada))
        assertFalse(Permisos.puedeValorarCandidaturas(nada))
        assertFalse(Permisos.puedeVerAusenciasDelEquipo(nada))
    }

    /**
     * Una authority que esta versión de la app no conoce no enciende nada.
     *
     * Importa porque el bloque de funcionalidades nuevas va a añadir
     * varias en el servidor antes de que la app las use: recibirlas no
     * puede desbloquear una pantalla por parecido de nombre.
     */
    @Test
    fun `una authority desconocida no enciende ninguna pantalla`() {
        val futuras = setOf("cuadrante:gestionar", "analitica:leer", "firma:visar")
        assertFalse(Permisos.puedeGestionarEquipo(futuras))
        assertFalse(Permisos.puedeCorregirFichajes(futuras))
        assertFalse(Permisos.puedeCrearGestores(futuras))
    }

    @Test
    fun `cada funcion mira la authority que dice mirar`() {
        // El emparejamiento, uno a uno: es lo único que la app puede
        // escribir mal ahora, y una errata apagaría el botón en silencio.
        assertTrue(Permisos.puedeGestionarEquipo(setOf("fichaje:leer:equipo")))
        assertTrue(Permisos.puedeVerPanelEmpresa(setOf("fichaje:leer:equipo")))
        assertTrue(Permisos.puedeAprobarAusencias(setOf("ausencia:aprobar")))
        assertTrue(Permisos.puedeVerAusenciasDelEquipo(setOf("ausencia:leer:equipo")))
        assertTrue(Permisos.puedeCrearEmpleados(setOf("empleado:crear")))
        assertTrue(Permisos.puedeGestionarEmpleados(setOf("empleado:gestionar")))
        assertTrue(Permisos.puedeConfigurarEmpleados(setOf("empleado:configurar")))
        assertTrue(Permisos.puedeCorregirFichajes(setOf("fichaje:corregir")))
        assertTrue(Permisos.puedeVerAuditoria(setOf("fichaje:auditoria")))
        assertTrue(Permisos.puedeExportarInformes(setOf("informe:exportar")))
        assertTrue(Permisos.puedeCrearGestores(setOf("gestor:crear")))
        assertTrue(Permisos.puedeGestionarCalendario(setOf("calendario:gestionar")))
        assertTrue(Permisos.puedeGestionarProyectos(setOf("proyecto:gestionar")))
        assertTrue(Permisos.puedeRevisarHorasExtra(setOf("horasextra:revisar")))
        assertTrue(Permisos.puedeInstruirDenuncias(setOf("denuncia:instruir")))
        assertTrue(Permisos.puedePublicarOfertas(setOf("oferta:publicar")))
        assertTrue(Permisos.puedeValorarCandidaturas(setOf("candidatura:gestionar")))
    }

    /**
     * Dos funciones que comprueban authorities distintas aunque hoy los
     * mismos roles tengan las dos.
     *
     * Desactivar una cuenta (`empleado:gestionar`) y fijar la jornada
     * contractual (`empleado:configurar`) no son la misma operación. Ahora
     * que la lista la manda el servidor, el día que se repartan distinto
     * la app se entera sola, sin tocar una línea.
     */
    @Test
    fun `dar de baja y configurar la ficha son permisos distintos`() {
        assertTrue(Permisos.puedeGestionarEmpleados(setOf("empleado:gestionar")))
        assertFalse(Permisos.puedeConfigurarEmpleados(setOf("empleado:gestionar")))
        assertTrue(Permisos.puedeConfigurarEmpleados(setOf("empleado:configurar")))
        assertFalse(Permisos.puedeGestionarEmpleados(setOf("empleado:configurar")))
    }

    @Test
    fun `un empleado solo ve lo suyo`() {
        assertFalse(Permisos.puedeGestionarEquipo(deEmpleado))
        assertFalse(Permisos.puedeAprobarAusencias(deEmpleado))
        assertFalse(Permisos.puedeCrearEmpleados(deEmpleado))
        assertFalse(Permisos.puedeCorregirFichajes(deEmpleado))
        assertFalse(Permisos.puedeVerAuditoria(deEmpleado))
        assertFalse(Permisos.puedeExportarInformes(deEmpleado))
        assertFalse(Permisos.puedeCrearGestores(deEmpleado))
        assertFalse(Permisos.puedeConfigurarEmpleados(deEmpleado))
        // El calendario laboral lo lee todo el mundo, pero solo lo cambia
        // quien gestiona; los proyectos, igual. Por eso no hay función
        // para "leer": se comprueba que la de gestionar NO se enciende.
        assertFalse(Permisos.puedeGestionarCalendario(deEmpleado))
        assertFalse(Permisos.puedeGestionarProyectos(deEmpleado))
        assertFalse(Permisos.puedeVerAusenciasDelEquipo(deEmpleado))
    }

    @Test
    fun `un gestor lleva su equipo pero no el cumplimiento normativo`() {
        assertTrue(Permisos.puedeGestionarEquipo(deGestor))
        assertTrue(Permisos.puedeAprobarAusencias(deGestor))
        assertTrue(Permisos.puedeCrearEmpleados(deGestor))
        assertTrue(Permisos.puedeVerPanelEmpresa(deGestor))
        assertTrue(Permisos.puedeGestionarCalendario(deGestor))
        assertTrue(Permisos.puedeVerAusenciasDelEquipo(deGestor))
        assertTrue(Permisos.puedeGestionarProyectos(deGestor))

        // Corregir un fichaje y exportar el informe mensual son
        // operaciones de cumplimiento (RD-ley 8/2019), reservadas a RRHH.
        assertFalse(Permisos.puedeCorregirFichajes(deGestor))
        assertFalse(Permisos.puedeVerAuditoria(deGestor))
        assertFalse(Permisos.puedeExportarInformes(deGestor))
        assertFalse(Permisos.puedeGestionarEmpleados(deGestor))
        assertFalse(Permisos.puedeConfigurarEmpleados(deGestor))
        // El defecto histórico: "Crear gestor" exige `gestor:crear`, que
        // solo tiene ADMIN.
        assertFalse(Permisos.puedeCrearGestores(deGestor))
    }

    @Test
    fun `rrhh corrige fichajes y exporta informes, pero no crea gestores`() {
        assertTrue(Permisos.puedeCorregirFichajes(deRrhh))
        assertTrue(Permisos.puedeVerAuditoria(deRrhh))
        assertTrue(Permisos.puedeExportarInformes(deRrhh))
        assertTrue(Permisos.puedeGestionarEmpleados(deRrhh))
        assertTrue(Permisos.puedeConfigurarEmpleados(deRrhh))
        assertFalse(Permisos.puedeCrearGestores(deRrhh))
        // Que RRHH tampoco instruya denuncias no es un olvido: ver abajo.
        assertFalse(Permisos.puedeInstruirDenuncias(deRrhh))
    }

    /**
     * El ADMIN es quien funda la empresa, así que una empresa recién
     * creada solo tiene ese rol. Dejarlo fuera del panel lo cerraría
     * fuera de su propia gestión.
     */
    @Test
    fun `el admin lo puede todo`() {
        assertTrue(Permisos.puedeGestionarEquipo(deAdmin))
        assertTrue(Permisos.puedeAprobarAusencias(deAdmin))
        assertTrue(Permisos.puedeCrearEmpleados(deAdmin))
        assertTrue(Permisos.puedeGestionarEmpleados(deAdmin))
        assertTrue(Permisos.puedeConfigurarEmpleados(deAdmin))
        assertTrue(Permisos.puedeCorregirFichajes(deAdmin))
        assertTrue(Permisos.puedeVerAuditoria(deAdmin))
        assertTrue(Permisos.puedeExportarInformes(deAdmin))
        assertTrue(Permisos.puedeCrearGestores(deAdmin))
        assertTrue(Permisos.puedeVerPanelEmpresa(deAdmin))
        assertTrue(Permisos.puedeGestionarCalendario(deAdmin))
        assertTrue(Permisos.puedeGestionarProyectos(deAdmin))
        assertTrue(Permisos.puedeInstruirDenuncias(deAdmin))
        assertTrue(Permisos.puedePublicarOfertas(deAdmin))
        assertTrue(Permisos.puedeValorarCandidaturas(deAdmin))
    }

    /**
     * Publicar vacantes y valorar a quien opta empiezan en GESTOR (Fase
     * H). LEERLAS no pide nada, y por eso no hay función: un tablón de
     * vacantes internas al que no llega la plantilla no es un tablón.
     */
    @Test
    fun `las ofertas internas se publican desde gestor, pero las lee todo el mundo`() {
        assertFalse(Permisos.puedePublicarOfertas(deEmpleado))
        assertFalse(Permisos.puedeValorarCandidaturas(deEmpleado))
        assertTrue(Permisos.puedePublicarOfertas(deGestor))
        assertTrue(Permisos.puedeValorarCandidaturas(deGestor))
    }

    /**
     * El canal de denuncias no lo lee nadie más que el ADMIN (Fase G).
     *
     * La Ley 2/2023 obliga a designar un Responsable del Sistema Interno
     * de Información, y dársela también a un GESTOR haría que la denuncia
     * sobre un GESTOR la leyera él.
     */
    @Test
    fun `solo el admin instruye denuncias`() {
        assertFalse(Permisos.puedeInstruirDenuncias(deEmpleado))
        assertFalse(Permisos.puedeInstruirDenuncias(deGestor))
        assertFalse(Permisos.puedeInstruirDenuncias(deRrhh))
        assertTrue(Permisos.puedeInstruirDenuncias(deAdmin))
    }
}
