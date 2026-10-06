package com.nxtime.nxtime.security.sso;

/**
 * El SSO no ha salido. Lleva el motivo, que es lo que se le cuenta a la persona.
 *
 * No es una {@code BusinessException}: casi siempre salta en mitad de una
 * redirección del navegador, donde no hay a quién devolverle un JSON de error.
 * Quien la recoge manda al navegador de vuelta a la web (o a la app) con el
 * motivo en la URL, y es allí donde se convierte en un texto.
 */
public class SsoException extends RuntimeException {

    public enum Motivo {

        /** La persona dio a «Cancelar» en la pantalla del proveedor. */
        CANCELADO("cancelado"),

        /** Su correo es suyo, pero nadie le ha dado de alta en NX Time. */
        SIN_CUENTA("sin-cuenta"),

        /**
         * El proveedor no garantiza que el correo sea de quien entra (una cuenta
         * de Microsoft de una organización que no ha verificado su dominio). No
         * se dice si ese correo tiene cuenta: no ha demostrado que sea suyo.
         */
        CORREO_SIN_VERIFICAR("correo-sin-verificar"),

        CUENTA_INACTIVA("cuenta-inactiva"),

        /** Al vincular: esa cuenta del proveedor ya es de otra persona. */
        YA_VINCULADA("ya-vinculada"),

        /** Al vincular: ya tiene otra cuenta de ese proveedor. */
        YA_TIENE_OTRA("ya-tiene-otra"),

        /** Al vincular: no había sesión de NX Time con la que hacerlo. */
        SIN_SESION("sin-sesion"),

        /** Ese proveedor no está configurado en este servidor. */
        NO_DISPONIBLE("no-disponible"),

        /** Todo lo demás: el estado no cuadra, el proveedor no contesta, el token no valida. */
        FALLO("fallo");

        private final String id;

        Motivo(String id) {
            this.id = id;
        }

        /** Lo que viaja en la URL de vuelta ({@code ?sso=sin-cuenta}). */
        public String id() {
            return id;
        }
    }

    private final Motivo motivo;

    public SsoException(Motivo motivo) {
        this(motivo, motivo.id(), null);
    }

    public SsoException(Motivo motivo, String detalle) {
        this(motivo, detalle, null);
    }

    public SsoException(Motivo motivo, String detalle, Throwable causa) {
        super(detalle, causa);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
