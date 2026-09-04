package com.nxtime.nxtime.domain;

/**
 * Qué es un adjunto (Fase B2). Se persiste como texto en
 * {@code adjuntos.tipo}, que tiene un {@code CHECK}: añadir un valor
 * aquí exige una migración.
 *
 * Cada tipo trae sus propias reglas porque no son el mismo problema: un
 * CV se guarda tal cual y se descarga, y una foto se reescala antes de
 * guardarla y se enseña en línea.
 */
public enum AttachmentType {

    /** Currículum. PDF, se guarda tal cual y se descarga. */
    CV("application/pdf"),

    /**
     * Foto de perfil. Entra como JPEG o PNG y **siempre sale como JPEG**:
     * el servidor la reescala a 256x256 antes de guardarla, así que el
     * MIME almacenado es siempre este, no el que traía el original.
     */
    FOTO("image/jpeg", "image/png");

    private final String[] mimesAceptados;

    AttachmentType(String... mimesAceptados) {
        this.mimesAceptados = mimesAceptados;
    }

    /**
     * Si este tipo acepta un contenido con ese MIME.
     *
     * El MIME que se compara aquí es el que se ha deducido de los
     * primeros bytes del fichero, nunca el que declaró el cliente: la
     * extensión y el {@code Content-Type} los elige quien sube.
     */
    public boolean acepta(String mime) {
        for (String aceptado : mimesAceptados) {
            if (aceptado.equals(mime)) {
                return true;
            }
        }
        return false;
    }

    public String descripcionDeLoAceptado() {
        return String.join(" o ", mimesAceptados);
    }
}
