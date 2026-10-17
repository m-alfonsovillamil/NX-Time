package com.nxtime.nxtime.config;

import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.OperadoresDePlataforma;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
                // Sin el correo: el mensaje acaba en los registros, y serían
                // una lista de qué direcciones se están probando.
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));
    }

    // Spring Security 7 le ha dado la vuelta: el constructor recibe el
    // UserDetailsService, que es lo obligatorio, y el PasswordEncoder se pone
    // aparte. En la 6.3 era justo al revés.
    //
    // Al entrar, la persona se lee en SU transacción, corta, y no con el
    // userDetailsService() de arriba a secas (ADR 041). No es un detalle:
    // una consulta declarada en un repositorio no abre transacción, y fuera
    // de una, Spring deja la conexión cogida hasta que acaba el método que la
    // rodea. El login comprueba después la contraseña (BCrypt, lento a
    // propósito) y abre otra transacción para guardar la sesión: sin esto,
    // cada entrada retenía una conexión mientras pedía otra, y con cinco
    // entradas a la vez el pool de cinco se quedaba bloqueado. Salió
    // ejecutando la prueba de carga, con trescientas personas a la vez; los
    // tests pasaban.
    //
    // El filtro del token sigue usando el de arriba: ahí no hay ningún método
    // alrededor, la conexión se suelta al acabar la consulta, y una
    // transacción sería un viaje más a la base en cada petición.
    @Bean
    public AuthenticationProvider authenticationProvider(PlatformTransactionManager transacciones) {
        TransactionTemplate lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        UserDetailsService alEntrar =
                email -> lectura.execute(estado -> userDetailsService().loadUserByUsername(email));

        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(alEntrar);
        authProvider.setPasswordEncoder(passwordEncoder());
        // Si la cuenta está de baja se mira DESPUÉS de la contraseña, no antes
        // como hace Spring por defecto (revisión de seguridad del 17/10/2026,
        // plan J). Antes, una cuenta de baja respondía sin hacer el BCrypt, en
        // un milisegundo, y cronometrando se sabía qué correos tienen cuenta
        // aunque esté de baja. Ahora cuesta lo mismo que cualquier otra, y
        // «está de baja» solo se lo encuentra quien sabe la contraseña.
        authProvider.setPreAuthenticationChecks(usuario -> { });
        authProvider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
