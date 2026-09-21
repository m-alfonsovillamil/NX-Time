package com.nxtime.app.ui.util

/**
 * Qué puede hacer quien ha iniciado sesión, **según el servidor**.
 *
 * Hasta septiembre de 2026 esto era un espejo a mano de
 * `RoleAuthorities.java`: la app traducía el rol a capacidades con su
 * propia copia de la jerarquía. Funcionaba mientras alguien se acordara
 * de tocar los dos sitios, y el día que no se acordara el fallo sería
 * una pantalla ofrecida a quien luego recibe un 403 — exactamente el
 * defecto que ya ocurrió una vez con el botón "Crear gestor". Con la web
 * en camino habría un tercer espejo, en TypeScript.
 *
 * Así que ahora el servidor manda la lista resuelta (`authorities` en el
 * login, en el refresco y en `GET /api/v1/perfil`) y esto solo pregunta
 * si una cadena está dentro. El reparto de permisos vive en **un solo
 * fichero del proyecto**, y añadir una authority nueva ya no obliga a
 * tocar la app.
 *
 * **Esto no autoriza nada**: autoriza el `@PreAuthorize` de cada
 * endpoint contra el token. Lo de aquí solo decide qué se enseña.
 *
 * Cada función se llama como la authority que comprueba para que el
 * emparejamiento se lea de un vistazo; el nombre de la cadena es el
 * contrato y no se traduce.
 */
object Permisos {

    /** Ve el panel de gestión y el historial del equipo (`fichaje:leer:equipo`). */
    fun puedeGestionarEquipo(authorities: Set<String>): Boolean =
        "fichaje:leer:equipo" in authorities

    /** Aprueba o rechaza ausencias de su equipo (`ausencia:aprobar`). */
    fun puedeAprobarAusencias(authorities: Set<String>): Boolean =
        "ausencia:aprobar" in authorities

    /**
     * Ve las ausencias de sus compañeros (`ausencia:leer:equipo`).
     *
     * Aparte de [puedeAprobarAusencias] aunque hoy los mismos roles tengan
     * las dos: leer el calendario del equipo y decidir sobre sus
     * vacaciones son cosas distintas, y el backend las separa. Es lo que
     * decide si el calendario ofrece el interruptor "ver al equipo".
     */
    fun puedeVerAusenciasDelEquipo(authorities: Set<String>): Boolean =
        "ausencia:leer:equipo" in authorities

    /** Da de alta empleados (`empleado:crear`). */
    fun puedeCrearEmpleados(authorities: Set<String>): Boolean =
        "empleado:crear" in authorities

    /** Da de alta o de baja a un empleado (`empleado:gestionar`). */
    fun puedeGestionarEmpleados(authorities: Set<String>): Boolean =
        "empleado:gestionar" in authorities

    /**
     * Fija la jornada semanal y los días de vacaciones (`empleado:configurar`).
     *
     * Hoy la tienen los mismos roles que [puedeGestionarEmpleados], igual
     * que en el backend: son dos capacidades distintas sobre la misma
     * persona (desactivar su cuenta / fijar su jornada contractual) que de
     * momento coinciden. Ahora que la lista la manda el servidor, el día
     * que se repartan distinto la app se entera sola.
     */
    fun puedeConfigurarEmpleados(authorities: Set<String>): Boolean =
        "empleado:configurar" in authorities

    /** Corrige un fichaje cerrado (`fichaje:corregir`). */
    fun puedeCorregirFichajes(authorities: Set<String>): Boolean =
        "fichaje:corregir" in authorities

    /** Ve la traza de auditoría de un fichaje (`fichaje:auditoria`). */
    fun puedeVerAuditoria(authorities: Set<String>): Boolean =
        "fichaje:auditoria" in authorities

    /** Exporta los informes en Excel y PDF (`informe:exportar`). */
    fun puedeExportarInformes(authorities: Set<String>): Boolean =
        "informe:exportar" in authorities

    /**
     * Crea otros gestores (`gestor:crear`). Solo ADMIN: conceder poder de
     * gestión a otra persona es administrar la empresa, no gestionarla.
     */
    fun puedeCrearGestores(authorities: Set<String>): Boolean =
        "gestor:crear" in authorities

    /** Ve el panel de indicadores de la empresa (`fichaje:leer:equipo`). */
    fun puedeVerPanelEmpresa(authorities: Set<String>): Boolean =
        "fichaje:leer:equipo" in authorities

    /**
     * Añade y quita festivos del calendario laboral (`calendario:gestionar`).
     *
     * **Ojo**: esto no alcanza a los festivos NACIONALES. Esos son una
     * fila compartida por todas las empresas y los pone el sistema, así
     * que ahí el límite no lo marca el permiso sino el propio festivo --
     * por eso cada uno viaja con su campo `editable` y hay que mirar los
     * dos.
     */
    fun puedeGestionarCalendario(authorities: Set<String>): Boolean =
        "calendario:gestionar" in authorities

    /**
     * Crea proyectos y reparte asignaciones (`proyecto:gestionar`).
     *
     * Leerlos lo puede todo el mundo (`proyecto:leer`), porque saber en
     * qué proyecto estás es parte de tu propia ficha.
     */
    fun puedeGestionarProyectos(authorities: Set<String>): Boolean =
        "proyecto:gestionar" in authorities

    /**
     * Decide si un exceso de jornada cuenta como horas extra
     * (`horasextra:revisar`).
     *
     * VER los propios no pide nada — son tus horas, y el aviso te llega a
     * ti antes que a nadie —, así que no hay función para eso.
     *
     * **Ojo**: esto no alcanza a los avisos de uno mismo. Ni con la
     * authority se pueden revisar los propios, y eso no es una regla que
     * la app pueda comprobar: la aplica el servidor, que además ya los
     * deja fuera de la bandeja del equipo.
     */
    fun puedeRevisarHorasExtra(authorities: Set<String>): Boolean =
        "horasextra:revisar" in authorities

    /**
     * Lee e instruye las denuncias del canal interno (`denuncia:instruir`).
     *
     * **Solo ADMIN, y aquí eso es el requisito y no un reparto
     * conservador.** La Ley 2/2023 obliga a designar un Responsable del
     * Sistema Interno de Información; darle esta lectura también a un
     * GESTOR haría que la denuncia sobre un GESTOR la leyera él.
     *
     * PRESENTAR una denuncia no tiene función aquí porque la puede todo el
     * mundo (`denuncia:crear`): un canal al que no llega todo el mundo no
     * es un canal.
     */
    fun puedeInstruirDenuncias(authorities: Set<String>): Boolean =
        "denuncia:instruir" in authorities

    /**
     * Publica y cierra vacantes internas (`oferta:publicar`).
     *
     * LEERLAS y presentarse no pide nada (`oferta:leer`,
     * `candidatura:crear`): un tablón de vacantes internas al que no llega
     * la plantilla no es un tablón.
     */
    fun puedePublicarOfertas(authorities: Set<String>): Boolean =
        "oferta:publicar" in authorities

    /**
     * Decide sobre quien opta a una vacante (`candidatura:gestionar`).
     *
     * **Ojo**: esto no alcanza a la candidatura de uno mismo. Un GESTOR
     * puede optar a una vacante como cualquiera, y esa regla la aplica el
     * servidor: viaja resuelta en `puedoValorar`.
     */
    fun puedeValorarCandidaturas(authorities: Set<String>): Boolean =
        "candidatura:gestionar" in authorities
}
