package com.nxtime.nxtime.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Añade un identificador de correlación a cada petición (leído de la
 * cabecera X-Request-Id si el cliente lo manda, o generado si no) y lo
 * mete en el MDC de SLF4J bajo la clave "requestId", para que todas
 * las líneas de log de esa petición -- en cualquier clase -- queden
 * agrupadas por ese id (ver logging.pattern.console en application.yml).
 * También se devuelve en la cabecera de respuesta, para poder
 * correlacionar un fallo que reporta el cliente con las líneas de log
 * del servidor.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final java.util.regex.Pattern ID_ACEPTABLE = java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        // Solo uno con pinta de identificador (revisión de seguridad del
        // 17/10/2026): lo escribe quien llama, va en cada línea de registro y
        // vuelve en la respuesta, y antes se aceptaba cualquier cosa, saltos de
        // línea y megas incluidos.
        if (requestId == null || !ID_ACEPTABLE.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
