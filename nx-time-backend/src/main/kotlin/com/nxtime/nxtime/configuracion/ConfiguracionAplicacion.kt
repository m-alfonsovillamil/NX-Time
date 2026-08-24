package com.nxtime.nxtime.configuracion

import com.nxtime.nxtime.repositorio.UsuarioRepositorio
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Configuración de los componentes base de la aplicación (Beans).
 */

@Configuration
class ConfiguracionAplicacion(

    private val usuarioRepositorio: UsuarioRepositorio
) {

    /**
     * Define el algoritmo de encriptación para las contraseñas.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    /**
     * Le dice a Spring cómo buscar a un usuario por su email.
     */
    @Bean
    fun userDetailsService(): UserDetailsService {
        return UserDetailsService { email ->

            usuarioRepositorio.findByEmail(email)

                .orElseThrow { UsernameNotFoundException("Usuario no encontrado con email: $email") }
        }
    }

    /**
     *  Proveedor que verifica la identidad
     */
    @Bean
    fun authenticationProvider(): AuthenticationProvider {
        val authProvider = DaoAuthenticationProvider()

        authProvider.setUserDetailsService(userDetailsService())

        authProvider.setPasswordEncoder(passwordEncoder())
        return authProvider
    }

    /**
     * Lo usaremos en nuestro servicio para hacer el login.
     */
    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager {
        return config.authenticationManager
    }
}