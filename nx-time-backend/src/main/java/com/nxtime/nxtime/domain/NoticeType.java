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
    BIENVENIDA("fichar");

    private final String rutaDestinoPorDefecto;

    NoticeType(String rutaDestinoPorDefecto) {
        this.rutaDestinoPorDefecto = rutaDestinoPorDefecto;
    }

    public String getRutaDestinoPorDefecto() {
        return rutaDestinoPorDefecto;
    }
}
