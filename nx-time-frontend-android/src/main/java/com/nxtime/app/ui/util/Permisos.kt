package com.nxtime.app.ui.util

/**
 * Los roles del backend, vistos desde la app.
 *
 * El valor de cada constante es EXACTAMENTE la cadena que viaja en el
 * campo `rol` del JSON de autenticación y que `SessionManager` guarda,
 * así que el nombre no se puede traducir ni "arreglar" sin romper el
 * emparejamiento.
 */
enum class Rol {
    EMPLEADO,
    GESTOR,
    RRHH,
    ADMIN;

    companion object {
        /**
         * Traduce lo guardado en la sesión. Devuelve `null` si no hay rol
         * o si es uno que esta versión de la app no conoce: quien llame
         * debe tratar ese caso como "sin permisos", nunca como "todos".
         */
        fun de(valor: String?): Rol? = entries.firstOrNull { it.name == valor }
    }
}

/**
 * Qué puede hacer cada rol, según el backend.
 *
 * Esto es un espejo deliberado de `RoleAuthorities.java`, que es donde
 * vive la verdad: allí cada rol se traduce a authorities granulares
 * ("fichaje:corregir", "informe:exportar"...) y cada endpoint las exige
 * con un `@PreAuthorize`. **La copia no autoriza nada**, solo decide qué
 * se enseña; el servidor sigue siendo quien deja pasar o devuelve 403.
 *
 * Hace falta porque la app solo sabía distinguir "es de gestión o no"
 * (el antiguo `ROLES_DE_GESTION` de `FicharViewModel`), y con esa brocha
 * se le ofrecía a un GESTOR el botón "Crear gestor", que exige la
 * authority `gestor:crear` y solo tiene ADMIN: pulsarlo daba 403 sin
 * falta. Con la jerarquía EMPLEADO < GESTOR < RRHH < ADMIN, comparar por
 * `ordinal` basta y evita repetir conjuntos de roles por toda la interfaz.
 */
object Permisos {

    /** Ve el panel de gestión y el historial del equipo. */
    fun puedeGestionarEquipo(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /** Aprueba o rechaza ausencias de su equipo (`ausencia:aprobar`). */
    fun puedeAprobarAusencias(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /** Da de alta empleados (`empleado:crear`). */
    fun puedeCrearEmpleados(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /** Da de alta o de baja a un empleado (`empleado:gestionar`). */
    fun puedeGestionarEmpleados(rol: Rol?): Boolean = alMenos(rol, Rol.RRHH)

    /** Corrige un fichaje cerrado (`fichaje:corregir`). */
    fun puedeCorregirFichajes(rol: Rol?): Boolean = alMenos(rol, Rol.RRHH)

    /** Ve la traza de auditoría de un fichaje (`fichaje:auditoria`). */
    fun puedeVerAuditoria(rol: Rol?): Boolean = alMenos(rol, Rol.RRHH)

    /** Exporta los informes en Excel y PDF (`informe:exportar`). */
    fun puedeExportarInformes(rol: Rol?): Boolean = alMenos(rol, Rol.RRHH)

    /**
     * Crea otros gestores (`gestor:crear`). Solo ADMIN: conceder poder de
     * gestión a otra persona es administrar la empresa, no gestionarla.
     */
    fun puedeCrearGestores(rol: Rol?): Boolean = alMenos(rol, Rol.ADMIN)

    /** Ve el panel de indicadores de la empresa (`fichaje:leer:equipo`). */
    fun puedeVerPanelEmpresa(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    private fun alMenos(rol: Rol?, minimo: Rol): Boolean =
        rol != null && rol.ordinal >= minimo.ordinal
}
