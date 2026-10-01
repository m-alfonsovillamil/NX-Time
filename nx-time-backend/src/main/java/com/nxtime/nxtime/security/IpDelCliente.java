package com.nxtime.nxtime.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La IP de quien llama, contando X-Forwarded-For desde el final.
 *
 * X-Forwarded-For se lee de izquierda a derecha como "quien llamó primero, y
 * luego cada proxy por el que pasó". El PRIMER valor lo escribe el cliente y
 * por lo tanto se lo puede inventar; cada proxy AÑADE al final la dirección que
 * él ha visto. La única entrada en la que se puede confiar es la que puso el
 * último proxy de confianza, así que se cuenta desde el final tantas posiciones
 * como proxies haya.
 *
 * Con un proxy delante (Render) y la cabecera "1.2.3.4, 198.51.100.7", la buena
 * es 198.51.100.7 -- la que vio Render --, no la 1.2.3.4 que mandó quien
 * llamaba.
 *
 * Si la cabecera trae menos entradas de las que debería, se usa
 * getRemoteAddr(): es la del salto inmediato y no se puede falsificar.
 *
 * Estaba escrito dentro de {@link LoginRateLimitFilter}; la auditoría de los
 * fichajes guardaba en cambio {@code getRemoteAddr()}, que detrás de Render es
 * la del proxy y no dice nada (fase K1, ADR 033). Un solo sitio para las dos.
 */
public final class IpDelCliente {

    /** El valor por defecto de {@code application.security.rate-limit.trusted-proxies}: Render. */
    public static final String PROXIES_DE_CONFIANZA = "${application.security.rate-limit.trusted-proxies:1}";

    private IpDelCliente() {
    }

    public static String de(HttpServletRequest request, int proxiesDeConfianza) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank() && proxiesDeConfianza > 0) {
            String[] saltos = forwardedFor.split(",");
            int posicion = saltos.length - proxiesDeConfianza;
            if (posicion >= 0 && posicion < saltos.length) {
                String ip = saltos[posicion].trim();
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
