package com.nxtime.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué ve cada rol.
 *
 * Esto lo decidía `FicharViewModel` con un único `setOf("GESTOR",
 * "RRHH", "ADMIN")`, y con esa brocha se pintaban permisos que el
 * backend distingue mucho más fino. El caso de abajo
 * `un gestor no puede crear gestores` es exactamente el defecto que
 * eso causaba: la app le ofrecía el botón y el backend respondía 403.
 *
 * La referencia es `RoleAuthorities.java`; si allí se mueve una
 * authority de rol, estos tests son los que tienen que fallar.
 */
class PermisosTest {

    @Test
    fun `el rol se traduce desde lo que guarda la sesion`() {
        assertEquals(Rol.EMPLEADO, Rol.de("EMPLEADO"))
        assertEquals(Rol.ADMIN, Rol.de("ADMIN"))
    }

    /**
     * Sin sesión, o con un rol que esta versión no conoce, no se enseña
     * nada: la alternativa —tratarlo como "todos los permisos"— abriría
     * pantallas de gestión a quien no debe verlas.
     */
    @Test
    fun `un rol desconocido o ausente no da ningun permiso`() {
        assertNull(Rol.de(null))
        assertNull(Rol.de("SUPERVISOR"))

        assertFalse(Permisos.puedeGestionarEquipo(null))
        assertFalse(Permisos.puedeCorregirFichajes(null))
        assertFalse(Permisos.puedeCrearGestores(null))
        assertFalse(Permisos.puedeExportarInformes(null))
        assertFalse(Permisos.puedeConfigurarEmpleados(null))
        assertFalse(Permisos.puedeGestionarCalendario(null))
    }

    @Test
    fun `un empleado solo ve lo suyo`() {
        val rol = Rol.EMPLEADO
        assertFalse(Permisos.puedeGestionarEquipo(rol))
        assertFalse(Permisos.puedeAprobarAusencias(rol))
        assertFalse(Permisos.puedeCrearEmpleados(rol))
        assertFalse(Permisos.puedeCorregirFichajes(rol))
        assertFalse(Permisos.puedeVerAuditoria(rol))
        assertFalse(Permisos.puedeExportarInformes(rol))
        assertFalse(Permisos.puedeCrearGestores(rol))
        assertFalse(Permisos.puedeConfigurarEmpleados(rol))
        // El calendario laboral lo lee todo el mundo, pero solo lo
        // cambia quien gestiona (`calendario:gestionar`, desde GESTOR).
        assertFalse(Permisos.puedeGestionarCalendario(rol))
        // Y las ausencias ajenas tampoco se ven desde una cuenta de
        // empleado, ni siquiera como bandas en el calendario.
        assertFalse(Permisos.puedeVerAusenciasDelEquipo(rol))
        // Los proyectos los LEE todo el mundo, pero repartir el trabajo
        // empieza en GESTOR (`proyecto:gestionar`).
        assertFalse(Permisos.puedeGestionarProyectos(rol))
    }

    @Test
    fun `un gestor lleva su equipo pero no el cumplimiento normativo`() {
        val rol = Rol.GESTOR
        assertTrue(Permisos.puedeGestionarEquipo(rol))
        assertTrue(Permisos.puedeAprobarAusencias(rol))
        assertTrue(Permisos.puedeCrearEmpleados(rol))
        assertTrue(Permisos.puedeVerPanelEmpresa(rol))
        // Quien decide si tus vacaciones se aprueban es quien sabe qué
        // días de convenio cierra el centro.
        assertTrue(Permisos.puedeGestionarCalendario(rol))
        assertTrue(Permisos.puedeVerAusenciasDelEquipo(rol))
        assertTrue(Permisos.puedeGestionarProyectos(rol))

        // Corregir un fichaje y exportar el informe mensual son
        // operaciones de cumplimiento (RD-ley 8/2019), reservadas a RRHH.
        assertFalse(Permisos.puedeCorregirFichajes(rol))
        assertFalse(Permisos.puedeVerAuditoria(rol))
        assertFalse(Permisos.puedeExportarInformes(rol))
        assertFalse(Permisos.puedeGestionarEmpleados(rol))
        // La jornada contractual y los días de vacaciones son un dato de
        // RRHH, no algo que decida quien lleva el equipo día a día.
        assertFalse(Permisos.puedeConfigurarEmpleados(rol))
    }

    /**
     * El defecto que motivó esta clase: la app le enseñaba a un GESTOR
     * la opción "Crear gestor", que exige la authority `gestor:crear`,
     * y solo la tiene ADMIN. Pulsarla daba 403 sin excepción.
     */
    @Test
    fun `un gestor no puede crear gestores`() {
        assertFalse(Permisos.puedeCrearGestores(Rol.GESTOR))
        assertFalse(Permisos.puedeCrearGestores(Rol.RRHH))
        assertTrue(Permisos.puedeCrearGestores(Rol.ADMIN))
    }

    @Test
    fun `rrhh corrige fichajes y exporta informes, pero no crea gestores`() {
        val rol = Rol.RRHH
        assertTrue(Permisos.puedeCorregirFichajes(rol))
        assertTrue(Permisos.puedeVerAuditoria(rol))
        assertTrue(Permisos.puedeExportarInformes(rol))
        assertTrue(Permisos.puedeGestionarEmpleados(rol))
        assertTrue(Permisos.puedeConfigurarEmpleados(rol))
        assertFalse(Permisos.puedeCrearGestores(rol))
    }

    /**
     * El ADMIN es quien funda la empresa, así que una empresa recién
     * creada solo tiene ese rol. Dejarlo fuera del panel lo cerraría
     * fuera de su propia gestión.
     */
    @Test
    fun `el admin lo puede todo`() {
        val rol = Rol.ADMIN
        assertTrue(Permisos.puedeGestionarEquipo(rol))
        assertTrue(Permisos.puedeAprobarAusencias(rol))
        assertTrue(Permisos.puedeCrearEmpleados(rol))
        assertTrue(Permisos.puedeGestionarEmpleados(rol))
        assertTrue(Permisos.puedeConfigurarEmpleados(rol))
        assertTrue(Permisos.puedeCorregirFichajes(rol))
        assertTrue(Permisos.puedeVerAuditoria(rol))
        assertTrue(Permisos.puedeExportarInformes(rol))
        assertTrue(Permisos.puedeCrearGestores(rol))
        assertTrue(Permisos.puedeVerPanelEmpresa(rol))
        assertTrue(Permisos.puedeGestionarCalendario(rol))
        assertTrue(Permisos.puedeGestionarProyectos(rol))
        assertTrue(Permisos.puedeInstruirDenuncias(rol))
    }

    /**
     * El canal de denuncias no lo lee nadie más que el ADMIN (Fase G).
     *
     * Es la única capacidad del proyecto en la que "que no baje" es el
     * requisito y no un reparto conservador: la Ley 2/2023 obliga a
     * designar un Responsable del Sistema Interno de Información, y
     * dársela también a un GESTOR haría que la denuncia sobre un GESTOR
     * la leyera él. Que RRHH tampoco la tenga no es un olvido.
     */
    @Test
    fun `solo el admin instruye denuncias`() {
        assertFalse(Permisos.puedeInstruirDenuncias(Rol.EMPLEADO))
        assertFalse(Permisos.puedeInstruirDenuncias(Rol.GESTOR))
        assertFalse(Permisos.puedeInstruirDenuncias(Rol.RRHH))
        assertTrue(Permisos.puedeInstruirDenuncias(Rol.ADMIN))
        // Un rol que esta version no conozca se trata como "sin
        // permisos", nunca como "todos".
        assertFalse(Permisos.puedeInstruirDenuncias(Rol.de("RESPONSABLE_CANAL")))
    }
}
