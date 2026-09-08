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

    /**
     * Ve las ausencias de sus compañeros (`ausencia:leer:equipo`).
     *
     * Aparte de [puedeAprobarAusencias] aunque hoy coincidan: leer el
     * calendario del equipo y decidir sobre sus vacaciones son dos cosas
     * distintas, y el backend las separa en dos authorities. Es lo que
     * decide si el calendario ofrece el interruptor "ver al equipo".
     */
    fun puedeVerAusenciasDelEquipo(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /** Da de alta empleados (`empleado:crear`). */
    fun puedeCrearEmpleados(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /** Da de alta o de baja a un empleado (`empleado:gestionar`). */
    fun puedeGestionarEmpleados(rol: Rol?): Boolean = alMenos(rol, Rol.RRHH)

    /**
     * Fija la jornada semanal y los días de vacaciones (`empleado:configurar`).
     *
     * Hoy coincide rol a rol con [puedeGestionarEmpleados], igual que en
     * el backend: son dos capacidades distintas sobre la misma persona
     * (desactivar su cuenta / fijar su jornada contractual) que de
     * momento tienen los mismos roles. Se mantienen separadas para que
     * el día que se repartan distinto solo cambie esta línea.
     */
    fun puedeConfigurarEmpleados(rol: Rol?): Boolean = alMenos(rol, Rol.RRHH)

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

    /**
     * Añade y quita festivos del calendario laboral (`calendario:gestionar`).
     *
     * Empieza en GESTOR: quien ya decide si tus vacaciones se aprueban es
     * quien sabe qué días de convenio cierra el centro.
     *
     * **Ojo**: esto no alcanza a los festivos NACIONALES. Esos son una
     * fila compartida por todas las empresas y los pone el sistema, así
     * que ahí el límite no lo marca el rol sino el propio festivo -- por
     * eso cada uno viaja con su campo `editable` y hay que mirar los dos.
     */
    fun puedeGestionarCalendario(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /**
     * Crea proyectos y reparte asignaciones (`proyecto:gestionar`).
     *
     * Leerlos lo puede todo el mundo (`proyecto:leer`), porque saber en
     * qué proyecto estás es parte de tu propia ficha; repartir el trabajo
     * empieza en GESTOR.
     */
    fun puedeGestionarProyectos(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /**
     * Decide si un exceso de jornada cuenta como horas extra
     * (`horasextra:revisar`).
     *
     * VER los propios no pide nada — son tus horas, y el aviso te llega a
     * ti antes que a nadie —, así que no hay función para eso. Lo que
     * empieza en GESTOR es decidir, porque saber si las once horas del
     * martes fueron una intensiva pactada es conocimiento de quien lleva
     * el equipo.
     *
     * **Ojo**: esto no alcanza a los avisos de uno mismo. Ni con la
     * authority se pueden revisar los propios, y no es una regla que la
     * app pueda comprobar con el rol: la aplica el servidor, que además
     * ya los deja fuera de la bandeja del equipo.
     */
    fun puedeRevisarHorasExtra(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /**
     * Lee e instruye las denuncias del canal interno
     * (`denuncia:instruir`).
     *
     * **Solo ADMIN, y aquí eso es el requisito y no un reparto
     * conservador.** La Ley 2/2023 obliga a designar un Responsable del
     * Sistema Interno de Información; darle esta lectura también a un
     * GESTOR haría que la denuncia sobre un GESTOR la leyera él. Es la
     * única capacidad del proyecto en la que "que no baje de ADMIN" es
     * el motivo de existir, y no un efecto de dónde se escribió la línea.
     *
     * PRESENTAR una denuncia no tiene función aquí porque la puede todo
     * el mundo (`denuncia:crear`): un canal al que no llega todo el
     * mundo no es un canal.
     */
    fun puedeInstruirDenuncias(rol: Rol?): Boolean = alMenos(rol, Rol.ADMIN)

    /**
     * Publica y cierra vacantes internas (`oferta:publicar`).
     *
     * LEERLAS y presentarse no pide nada (`oferta:leer`,
     * `candidatura:crear`): un tablón de vacantes internas al que no
     * llega la plantilla no es un tablón, así que no hay función para
     * eso.
     */
    fun puedePublicarOfertas(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    /**
     * Decide sobre quien opta a una vacante (`candidatura:gestionar`).
     *
     * Aparte de [puedePublicarOfertas] aunque hoy coincidan, por el
     * motivo de siempre: anunciar una vacante y decidir sobre las
     * personas que se presentan son operaciones distintas.
     *
     * **Ojo**: esto no alcanza a la candidatura de uno mismo. Ni con la
     * authority se puede valorar la propia — un GESTOR puede optar a una
     * vacante como cualquiera —, y no es una regla que la app pueda
     * comprobar con el rol: la aplica el servidor y viaja resuelta en
     * `puedoValorar`.
     */
    fun puedeValorarCandidaturas(rol: Rol?): Boolean = alMenos(rol, Rol.GESTOR)

    private fun alMenos(rol: Rol?, minimo: Rol): Boolean =
        rol != null && rol.ordinal >= minimo.ordinal
}
