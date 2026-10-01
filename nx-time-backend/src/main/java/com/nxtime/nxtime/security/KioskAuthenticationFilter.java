package com.nxtime.nxtime.security;

import com.nxtime.nxtime.audit.Canonico;
import com.nxtime.nxtime.repository.KioskRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a un kiosco por su token (ADR 033).
 *
 * <ul>
 *   <li><b>Solo mira {@code /kiosco/}</b>: con un token de kiosco no se llega a
 *       nada de {@code /api/v1/**}, ni aunque alguien lo copie de la tablet.</li>
 *   <li><b>Su propio esquema, {@code Authorization: Kiosco <token>}</b>, y no
 *       {@code Bearer}: así {@link JwtAuthenticationFilter} ni lo intenta leer
 *       como un JWT, y un JWT de una persona no pasa por aquí.</li>
 *   <li>Del token solo se guarda el SHA-256, como del refresh (ADR 019). Uno
 *       revocado deja de valer en la siguiente petición.</li>
 * </ul>
 *
 * Un token que no vale no responde aquí: se sigue sin autenticación y el 401 lo
 * da la cadena, como con un JWT caducado.
 */
@Component
public class KioskAuthenticationFilter extends OncePerRequestFilter {

    public static final String ESQUEMA = "Kiosco ";

    private final KioskRepository kioskRepository;

    public KioskAuthenticationFilter(KioskRepository kioskRepository) {
        this.kioskRepository = kioskRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/kiosco/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith(ESQUEMA)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = cabecera.substring(ESQUEMA.length()).trim();
            kioskRepository.findByTokenHash(Canonico.sha256(token))
                    .filter(kiosco -> kiosco.activo())
                    .ifPresent(kiosco -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(new KioskPrincipal(kiosco), null,
                                    List.of(new SimpleGrantedAuthority(KioskPrincipal.AUTHORITY)))));
        }
        filterChain.doFilter(request, response);
    }
}
