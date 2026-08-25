package com.nxtime.nxtime.security;

import com.nxtime.nxtime.domain.RoleAuthorities;
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
 * corregido en la Fase 2).
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
        return RoleAuthorities.forRole(user.getRol()).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
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

    // Desde la Fase 4: un usuario dado de baja (activo = false) ya no
    // puede autenticarse. Antes esto estaba cableado a "true" sin
    // excepción (ver auditoría, defectos de diseño).
    @Override
    public boolean isEnabled() {
        return user.isActivo();
    }
}
