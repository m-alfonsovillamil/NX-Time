package com.nxtime.nxtime.web.support;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.security.SecurityUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockSecurityUserFactory implements WithSecurityContextFactory<WithMockSecurityUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockSecurityUser annotation) {
        Company empresa = Company.builder().id(annotation.empresaId()).nombre("Empresa Test").build();
        User user = User.builder()
                .id(1L)
                .email(annotation.email())
                .nombre("Usuario Test")
                .rol(annotation.rol())
                .empresa(empresa)
                .activo(true)
                .build();

        SecurityUser principal = new SecurityUser(user);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
