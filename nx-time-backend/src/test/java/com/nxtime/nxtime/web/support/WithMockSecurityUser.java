package com.nxtime.nxtime.web.support;

import com.nxtime.nxtime.domain.Role;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Como {@code @WithMockUser}, pero con un principal {@code SecurityUser}
 * real en vez del {@code User} genérico de spring-security-test.
 *
 * Hace falta para los controladores que usan
 * {@code @AuthenticationPrincipal SecurityUser} (ManagerController,
 * UserController): con {@code @WithMockUser} el principal no es del tipo
 * esperado, así que Spring Security inyecta {@code null} sin avisar y el
 * controlador revienta con NullPointerException al llamar a
 * {@code manager.getUser()} -- no es un fallo del controlador, es un
 * desajuste del test. Las authorities se derivan del rol exactamente
 * igual que en producción ({@code RoleAuthorities.forRole(...)}, ver
 * {@link WithMockSecurityUserFactory}), no se declaran a mano.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockSecurityUserFactory.class)
public @interface WithMockSecurityUser {

    String email() default "test@nxtime.test";

    Role rol() default Role.GESTOR;

    long empresaId() default 1L;
}
