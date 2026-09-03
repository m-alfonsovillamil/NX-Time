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
    }

    @Test
    fun `un gestor lleva su equipo pero no el cumplimiento normativo`() {
        val rol = Rol.GESTOR
        assertTrue(Permisos.puedeGestionarEquipo(rol))
        assertTrue(Permisos.puedeAprobarAusencias(rol))
        assertTrue(Permisos.puedeCrearEmpleados(rol))
        assertTrue(Permisos.puedeVerPanelEmpresa(rol))

        // Corregir un fichaje y exportar el informe mensual son
        // operaciones de cumplimiento (RD-ley 8/2019), reservadas a RRHH.
        assertFalse(Permisos.puedeCorregirFichajes(rol))
        assertFalse(Permisos.puedeVerAuditoria(rol))
        assertFalse(Permisos.puedeExportarInformes(rol))
        assertFalse(Permisos.puedeGestionarEmpleados(rol))
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
        assertTrue(Permisos.puedeCorregirFichajes(rol))
        assertTrue(Permisos.puedeVerAuditoria(rol))
        assertTrue(Permisos.puedeExportarInformes(rol))
        assertTrue(Permisos.puedeCrearGestores(rol))
        assertTrue(Permisos.puedeVerPanelEmpresa(rol))
    }
}
