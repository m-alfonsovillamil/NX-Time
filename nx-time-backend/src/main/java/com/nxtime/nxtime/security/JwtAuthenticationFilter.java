package com.nxtime.nxtime.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Intercepta cada petición HTTP entrante para verificar si trae un Token válido.
 *
 * Desde la Fase 4 envuelve la lectura/validación del token en
 * try/catch (ver auditoría, defecto #3): un token corrupto o caducado
 * antes reventaba el filtro con una excepción no controlada, que
 * acababa en 500/403 según el caso. Ahora, si el token no es válido,
 * simplemente no se autentica la petición (como si no hubiera token) y
 * se deja que Spring Security decida más abajo -- lo que para una ruta
 * protegida significa que {@link RestAuthenticationEntryPoint} responde
 * 401 con ProblemDetail.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // PASO 1: Si la ruta empieza por /auth (Login o Registro), saltamos la validación.
        if (request.getServletPath().startsWith("/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        // PASO 2: Buscar la cabecera de autorización.
        String authHeader = request.getHeader("Authorization");

        // PASO 3: Validación de formato.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // PASO 4: Quitamos la palabra "Bearer " (7 caracteres) para obtener el token puro.
        String jwtToken = authHeader.substring(7);

        try {
            String userEmail = jwtService.extractUsername(jwtToken);

            // PASO 5: Verificación de usuario.
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // PASO 6: Validación del Token.
                if (jwtService.isTokenValid(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // PASO 7: Guardamos la autenticación en la memoria de Spring para esta petición.
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    log.warn("Token JWT inválido o caducado para {}", userEmail);
                }
            }
        } catch (RuntimeException e) {
            // Token corrupto, mal formado, con firma inválida o caducado
            // (jjwt lanza ExpiredJwtException, MalformedJwtException,
            // SignatureException... todas JwtException); o un usuario que
            // ya no existe (UsernameNotFoundException). En cualquier
            // caso, no se autentica la petición; para una ruta
            // protegida, Spring Security responderá 401 más abajo.
            log.warn("Token JWT no se pudo procesar: {}", e.getMessage());
        }

        // PASO 8: Pasamos la petición al siguiente filtro o al controlador correspondiente.
        filterChain.doFilter(request, response);
    }
}
