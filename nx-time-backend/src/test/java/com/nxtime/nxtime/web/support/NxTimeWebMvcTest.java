package com.nxtime.nxtime.web.support;

import com.nxtime.nxtime.security.JwtAuthenticationFilter;
import com.nxtime.nxtime.security.LoginRateLimitFilter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AliasFor;

/**
 * {@code @WebMvcTest} de los controladores de este proyecto, con
 * {@link JwtAuthenticationFilter} y {@link LoginRateLimitFilter}
 * excluidos del escaneo.
 *
 * {@code @WebMvcTest} detecta automáticamente cualquier bean
 * {@code Filter} del paquete base (no solo {@code @Controller}/
 * {@code @ControllerAdvice}), así que sin esta exclusión intentaría
 * instanciar esos dos filtros reales -- y con ellos, sus dependencias
 * (JwtService, UserDetailsService...), ajenas por completo a lo que un
 * test de controlador necesita comprobar. En su lugar se usa
 * {@link WebMvcTestSecurityConfig}, una cadena de seguridad mínima que
 * solo evalúa {@code @PreAuthorize} contra el principal que ponga
 * {@code @WithMockUser}/{@code @WithMockSecurityUser}; el filtro JWT real
 * ya se prueba de punta a punta en {@code ApiContractTest}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@WebMvcTest(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtAuthenticationFilter.class, LoginRateLimitFilter.class}))
public @interface NxTimeWebMvcTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
