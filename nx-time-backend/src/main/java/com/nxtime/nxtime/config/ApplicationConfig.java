package com.nxtime.nxtime.config;

import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.OperadoresDePlataforma;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración de los componentes base de la aplicación (Beans).
 */
@Configuration
public class ApplicationConfig {

    private final UserRepository userRepository;
    private final OperadoresDePlataforma operadores;

    public ApplicationConfig(UserRepository userRepository, OperadoresDePlataforma operadores) {
        this.userRepository = userRepository;
        this.operadores = operadores;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> userRepository.findByEmail(email)
                .map(operadores::principalDe)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));
    }

    // Spring Security 7 le ha dado la vuelta: el constructor recibe el
    // UserDetailsService, que es lo obligatorio, y el PasswordEncoder se pone
    // aparte. En la 6.3 era justo al revés.
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
