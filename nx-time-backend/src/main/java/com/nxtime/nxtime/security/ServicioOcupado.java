package com.nxtime.nxtime.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.ObjectMapper;

/**
 * La respuesta de «ahora no puedo, vuelve en un momento» (503), para cuando la
 * base no contesta o no queda ninguna conexión libre (ADR 041).
 *
 * Una sola definición para los dos sitios desde los que se da: el filtro del
 * token, que responde antes de llegar a ningún controlador, y el manejador de
 * errores de los controladores.
 */
public final class ServicioOcupado {

    /** Poco: lo que se espera es que pase la punta, no que se arregle una avería. */
    public static final int REINTENTAR_EN_SEGUNDOS = 5;

    private ServicioOcupado() {
    }

    public static ProblemDetail problema() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio está muy ocupado ahora mismo. Vuelve a intentarlo en unos segundos.");
    }

    /** Para un filtro, que escribe la respuesta a mano. */
    public static void responder(HttpServletResponse respuesta, ObjectMapper objectMapper) throws IOException {
        respuesta.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        respuesta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(REINTENTAR_EN_SEGUNDOS));
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(respuesta.getWriter(), problema());
    }
}
