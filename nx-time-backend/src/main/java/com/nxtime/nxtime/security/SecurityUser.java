package com.nxtime.nxtime.security;

import com.nxtime.nxtime.domain.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adapta un {@link User} de dominio a lo que Spring Security necesita
 * (UserDetails), sin que la entidad de dominio tenga que implementar
 * esa interfaz ella misma. Antes de esta clase, User implementaba
 * UserDetails directamente (ver auditoría, defectos de diseño):
 * acoplaba el dominio a Spring Security y era la entidad que se
 * filtraba entera -- contraseña incluida -- cuando un controlador
 * devolvía otra entidad que la arrastraba anidada (ver defecto #1,
 * corregido en esta misma fase al dejar de exponer entidades JPA en
 * las respuestas).
 */
public class SecurityUser implements UserDetails {

    private final User user;

    public SecurityUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRol().name()));
    }

    @Override
    public String getPassword() {
        return user.getContrasena();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
