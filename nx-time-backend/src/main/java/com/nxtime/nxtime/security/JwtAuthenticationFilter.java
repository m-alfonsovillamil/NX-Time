package com.nxtime.nxtime.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * OJO: no envuelve en try/catch la lectura del token (ver auditoría,
 * defecto #3). Se mantiene tal cual en esta fase de migración pura; se
 * corrige en la Fase 4.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
            }
        }

        // PASO 8: Pasamos la petición al siguiente filtro o al controlador correspondiente.
        filterChain.doFilter(request, response);
    }
}
