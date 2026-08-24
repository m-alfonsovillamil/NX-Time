package com.nxtime.nxtime.seguridad

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/*
 * Intercepta cada petición HTTP entrante para verificar si trae un Token válido.
 */

@Component
class FiltroAutenticacionJwt(
    private val jwtServicio: JwtServicio,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    /*
     * Lógica de filtrado que se ejecuta una vez por cada petición.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        //PASO 1: Si la ruta empieza por /auth (Login o Registro), saltamos la validación. Estas rutas deben ser públicas para que el usuario pueda entrar.

        if (request.servletPath.startsWith("/auth")) {
            filterChain.doFilter(request, response)
            return
        }

        // PASO 2: Buscar la cabecera de autorización.
        val authHeader: String? = request.getHeader("Authorization")


        //PASO 3: Validación de formato. Si no hay cabecera o no empieza por "Bearer ", no hay token que procesar. Dejamos pasar la petición (Spring Security la rechazará más adelante si la ruta es privada).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        // PASO 4: Quitamos la palabra "Bearer " (7 caracteres) para obtener el token puro.
        val jwtToken = authHeader.substring(7)

        val userEmail = jwtServicio.extractUsername(jwtToken)

        // PASO 5: Verificación de usuario.
        if (SecurityContextHolder.getContext().authentication == null) {


            val userDetails: UserDetails = this.userDetailsService.loadUserByUsername(userEmail)

            // PASO 6: Validación del Token.
            if (jwtServicio.isTokenValid(jwtToken, userDetails)) {

                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities
                )
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)

                // PASO 7: Guardamos la autenticación en la memoria de Spring para esta petición. A partir de esta línea el usuario está "Logueado".
                SecurityContextHolder.getContext().authentication = authToken
            }
        }

        // PASO 8: Pasamos la petición al siguiente filtro o al controlador correspondiente.
        filterChain.doFilter(request, response)
    }
}