package com.nxtime.nxtime.domain;

/**
 * Tipo de aviso dentro de la aplicación (Fase A). Se persiste como
 * texto ({@code @Enumerated(EnumType.STRING)}) en {@code avisos.tipo}.
 *
 * <b>Añadir un valor aquí exige una migración</b>: la columna tiene un
 * {@code CHECK (tipo IN (...))} en {@code V6__avisos.sql} y el primer
 * INSERT con un valor que no esté en esa lista revienta. El
 * acoplamiento es deliberado -- es lo que impide que un typo meta
 * basura en la columna -- pero hay que recordarlo en cada fase que
 * añada avisos nuevos.
 *
 * Cada valor lleva su destino lógico canónico, que es lo que acaba en
 * {@code avisos.ruta_destino} (ver {@link Notice}). Vive aquí y no
 * suelto en quien publica el aviso para que no haya dos listas que se
 * puedan desincronizar: el listener y el sembrador de datos de demo
 * preguntan al enum.
 *
 * Es un destino POR DEFECTO, no una imposición: {@link
 * com.nxtime.nxtime.dto.CreateNoticeCommand} sigue llevando su propia
 * ruta, porque en cuanto un aviso tenga que apuntar a un elemento
 * concreto ("la corrección 42") el destino dejará de depender solo del
 * tipo.
 */
public enum NoticeType {

    /** Alguien de tu equipo ha pedido una ausencia y te toca resolverla. */
    AUSENCIA_SOLICITADA("ausencias-equipo/pendientes"),

    /** Tu petición de ausencia ha sido aprobada o rechazada. */
    AUSENCIA_RESUELTA("ausencias"),

    /** Te acaban de dar de alta en una empresa. */
    BIENVENIDA("fichar"),

    // Fase E. El destino es lógico, no una ruta de Compose: lo traduce
    // DestinoDeAviso.kt en Android.

    /** Te toca resolver una corrección. */
    CORRECCION_SOLICITADA("correcciones/pendientes"),

    /** Han resuelto la corrección que pediste. */
    CORRECCION_RESUELTA("historial"),

    /** Una corrección ha acabado en disputa y la resuelve RRHH. */
    CORRECCION_EN_DISPUTA("correcciones/pendientes"),

    // Fase F.

    /** Una jornada o una semana se ha pasado del umbral. */
    HORAS_EXTRA_DETECTADAS("horas-extra"),

    /** La bolsa anual de 80 h (art. 35.2 ET) se está agotando. */
    BOLSA_HORAS_EXTRA_AL_LIMITE("horas-extra"),

    /**
     * Resumen de lo que ha encontrado el barrido nocturno, para quien
     * revisa. <b>Uno por empresa y por noche</b>, no uno por exceso.
     *
     * El aviso individual ({@link #HORAS_EXTRA_DETECTADAS}) va solo al
     * empleado, y eso no cambia: una empresa mediana genera decenas de
     * excesos al mes, y un correo por cada uno a cada gestor es spam por
     * diseño. Pero el otro extremo tampoco valía: los gestores solo se
     * enteraban si se les ocurría abrir la bandeja, así que un exceso
     * podía quedarse semanas sin revisar sin que nadie lo supiera.
     *
     * Lo que resuelve la tensión es el nivel de agregación, no el canal:
     * un aviso que dice "esta noche han salido 7 avisos nuevos, de 4
     * personas" y lleva a la bandeja que ya existe. Su frecuencia máxima
     * es una vez al día, y las noches sin excesos no mandan nada.
     */
    RESUMEN_HORAS_EXTRA("horas-extra"),

    // Fase G. Los dos llevan un título genérico y un cuerpo sin
    // contenido: un aviso de denuncia dice QUE hay algo, nunca QUÉ. Ver
    // ComplaintServiceImpl.

    /** Ha entrado una denuncia en el canal. Solo la ve quien instruye. */
    DENUNCIA_RECIBIDA("canal-denuncias"),

    /**
     * Se ha movido algo en una denuncia tuya.
     *
     * Solo puede llegar si te identificaste al presentarla: a un
     * denunciante anónimo no hay a quién avisar, y esa es exactamente la
     * contrapartida del anonimato.
     */
    DENUNCIA_ACTUALIZADA("denuncias"),

    // Fase H.

    /**
     * Hay una vacante interna nueva. Va a TODA la plantilla.
     *
     * Es la excepción a lo que decidió la fase F —que un aviso por cada
     * hecho detectado es spam por diseño— y la diferencia es el volumen:
     * los excesos de jornada salen a decenas al mes, y una vacante
     * interna a unas pocas al año. Con esa frecuencia, avisar es lo que
     * hace que el tablón exista: uno que nadie sabe que está ahí no
     * sirve para nada, y quien podría dar el paso no se entera.
     */
    OFERTA_PUBLICADA("ofertas"),

    /** Alguien se ha presentado a una oferta tuya. */
    CANDIDATURA_RECIBIDA("gestion-ofertas"),

    /** Han movido tu candidatura. */
    CANDIDATURA_ACTUALIZADA("mis-candidaturas"),

    // 09/2026: borrado de datos personales (ADR 016).

    /** Alguien ha pedido que se borren sus datos. A quien puede ejecutarlo. */
    BORRADO_SOLICITADO("borrados"),

    /**
     * No se ha ejecutado tu borrado, y por qué. No hay aviso de "ejecutado":
     * a esas alturas la cuenta está desactivada y no podría leerlo. Ese va
     * solo por correo.
     */
    BORRADO_RECHAZADO("ajustes"),

    // 09/2026

    /**
     * Alguien ha empezado a trabajar en un festivo o con una ausencia
     * aprobada. A quien aprueba ausencias; lleva al historial del equipo.
     */
    TRABAJO_EN_DIA_NO_LABORABLE("equipo"),

    // B1. A quien lleva los contratos: la jornada es suya, el cuadrante lo
    // puede poner un gestor. Ver ScheduleServiceImpl.asignar.
    CUADRANTE_DISTINTO_DE_JORNADA("equipo"),

    // B2. La incidencia, a quien la tiene (para que la explique); el
    // resumen, a quien revisa. Los dos a la misma pantalla: enseña lo propio
    // y, a quien tiene el permiso, la bandeja del equipo.
    INCIDENCIA_DETECTADA("incidencias"),

    RESUMEN_INCIDENCIAS("incidencias");

    private final String rutaDestinoPorDefecto;

    NoticeType(String rutaDestinoPorDefecto) {
        this.rutaDestinoPorDefecto = rutaDestinoPorDefecto;
    }

    public String getRutaDestinoPorDefecto() {
        return rutaDestinoPorDefecto;
    }
}
