package com.nxtime.nxtime.security;

import com.nxtime.nxtime.domain.Emails;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Quién mantiene esta instalación y puede verla entera (ADR 040).
 *
 * <b>No es un rol.</b> Los roles son de una empresa y se conceden desde dentro
 * de la aplicación; {@code plataforma:ver} cruza todas las empresas, y no hay
 * pantalla ni endpoint que lo conceda. Lo tienen las cuentas cuyo correo esté
 * en {@code PLATAFORMA_OPERADORES} (separados por comas), que se pone donde se
 * ponen las demás variables del servicio. Vacía, que es como viene, no lo
 * tiene nadie.
 *
 * <b>Por qué fiarse del correo.</b> Una cuenta solo la usa quien tiene su
 * buzón: quien registra una empresa confirma el correo con un código antes de
 * poder entrar, a quien dan de alta le llega el suyo para elegir contraseña, y
 * por SSO solo se entra con el correo que el proveedor garantiza. Y el correo
 * de una cuenta no se puede cambiar. Aun así, aquí se exige además que la
 * cuenta esté activa y con el correo confirmado: el día que alguna de esas
 * cosas cambie, que esto no sea lo que falle.
 *
 * Es la pieza por la que pasan los cuatro sitios que resuelven permisos: el
 * principal de cada petición, las dos respuestas de sesión y el perfil. Así lo
 * que autoriza el servidor y lo que se le enseña al cliente no pueden decir
 * cosas distintas.
 */
@Component
public class OperadoresDePlataforma {

    private static final Logger log = LoggerFactory.getLogger(OperadoresDePlataforma.class);

    private final Set<String> correos;

    public OperadoresDePlataforma(@Value("${application.plataforma.operadores:}") String lista) {
        this.correos = Arrays.stream(lista.split(","))
                .map(Emails::normalizar)
                .filter(correo -> !correo.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        // Cuántos y no cuáles: son correos de personas, y el log lo lee más gente.
        log.info("Panel de plataforma: {} operador(es) configurado(s).", correos.size());
    }

    public boolean es(User usuario) {
        return usuario != null
                && usuario.getEmail() != null
                && usuario.isActivo()
                && !usuario.correoPendienteDeConfirmar()
                && correos.contains(Emails.normalizar(usuario.getEmail()));
    }

    /** Las authorities de una persona, ordenadas, para mandárselas al cliente. */
    public List<String> authoritiesDe(User usuario) {
        List<String> delRol = RoleAuthorities.enOrden(usuario.getRol());
        if (!es(usuario)) {
            return delRol;
        }
        return Stream.concat(delRol.stream(), RoleAuthorities.FUERA_DE_LOS_ROLES.stream()).sorted().toList();
    }

    /** El principal de una persona, con lo de su rol y lo que le toque de aquí. */
    public SecurityUser principalDe(User usuario) {
        return new SecurityUser(usuario, es(usuario) ? List.copyOf(RoleAuthorities.FUERA_DE_LOS_ROLES) : List.of());
    }
}
