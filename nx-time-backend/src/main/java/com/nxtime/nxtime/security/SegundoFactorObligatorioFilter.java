package com.nxtime.nxtime.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Quien tiene que entrar con segundo factor y ha entrado sin él solo puede
 * activarlo (ADR 051).
 *
 * Se le deja entrar --si no, nunca podría activarlo--, pero su sesión no sirve
 * para nada más que lo de {@link #PERMITIDAS}: su perfil, activar el segundo
 * factor, cambiar la contraseña o cerrar sus sesiones, aceptar las condiciones
 * y el aviso de privacidad. Lo demás responde 403 con {@code codigo} =
 * {@value #CODIGO}, que los clientes reconocen para llevarle a activarlo.
 *
 * Se decide <b>por sesión</b>, no por persona: lo dice el access token
 * ({@link JwtService#conSegundoFactor}). Quien lo tiene activado y entra con
 * contraseña ya ha pasado por el código; quien entra con Google o Microsoft
 * también cuenta. Al activarlo, la sesión desde la que se activa pasa a
 * contar (ver {@code SegundoFactorServiceImpl#confirmarActivacion}).
 *
 * Va después de {@link JwtAuthenticationFilter}, que es quien sabe de quién es
 * la petición y deja dicho en {@link #ATRIBUTO} cómo entró.
 */
@Component
public class SegundoFactorObligatorioFilter extends OncePerRequestFilter {

    /** Lo que deja {@link JwtAuthenticationFilter}: si la sesión de la petición pasó por segundo factor. */
    public static final String ATRIBUTO = SegundoFactorObligatorioFilter.class.getName() + ".conSegundoFactor";

    public static final String CODIGO = "SEGUNDO_FACTOR_OBLIGATORIO";

    /** Las rutas que siguen abiertas: prefijos, salvo el perfil, que es exacto (GET). */
    static final List<String> PERMITIDAS = List.of(
            "/api/v1/perfil/segundo-factor",
            "/api/v1/perfil/aviso-de-privacidad",
            "/api/v1/usuario/",
            "/api/v1/empresa/condiciones",
            "/api/v1/dispositivos-push");

    private final SegundoFactorObligatorio obligatorio;
    private final ObjectMapper objectMapper;

    public SegundoFactorObligatorioFilter(SegundoFactorObligatorio obligatorio, ObjectMapper objectMapper) {
        this.obligatorio = obligatorio;
        this.objectMapper = objectMapper;
    }

    /** También en el despacho asíncrono, por lo mismo que el del JWT: ver allí. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof SecurityUser persona)
                || Boolean.TRUE.equals(request.getAttribute(ATRIBUTO))
                || permitida(request)
                || !obligatorio.exigido(persona.getUser())) {
            filterChain.doFilter(request, response);
            return;
        }
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Tienes que activar el segundo factor (un código de una aplicación de autenticación) "
                        + "antes de seguir. Lo tienes en Ajustes, en Segundo factor.");
        problema.setProperty("codigo", CODIGO);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problema);
    }

    private static boolean permitida(HttpServletRequest request) {
        String ruta = request.getServletPath();
        if ("/api/v1/perfil".equals(ruta)) {
            return "GET".equals(request.getMethod());
        }
        return PERMITIDAS.stream().anyMatch(ruta::startsWith);
    }
}
