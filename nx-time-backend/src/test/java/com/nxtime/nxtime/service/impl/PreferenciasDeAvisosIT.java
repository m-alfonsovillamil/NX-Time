package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.CategoriaDeAviso;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PreferenciasDeAvisos.CambioDePreferencia;
import com.nxtime.nxtime.dto.PreferenciasDeAvisos.PreferenciaDeAviso;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.PreferenciasDeAvisosService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Las preferencias de avisos contra Postgres (ADR 059): el UPSERT, el CHECK de
 * V55 y que quien nunca ha tocado nada lo recibe todo.
 *
 * <p>Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class PreferenciasDeAvisosIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "preferencias_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + testDb);
        }
        String testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime_app");
        registry.add("spring.datasource.password", () -> "nxtime_app");
        registry.add("spring.flyway.url", () -> testUrl);
        registry.add("spring.flyway.user", () -> "nxtime");
        registry.add("spring.flyway.password", () -> "nxtime");
    }

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PreferenciasDeAvisosService preferencias;

    private User ana;

    @BeforeEach
    void preparar() {
        Company empresa = companyRepository.save(Company.builder().nombre("Avisos " + System.nanoTime()).build());
        ana = userRepository.save(User.builder().nombre("Ana").apellidos("Pruebas")
                .email("Ana." + System.nanoTime() + "@nxtime.test").contrasena("x").rol(Role.EMPLEADO)
                .empresa(empresa).build());
    }

    @Test
    @DisplayName("Sin tocar nada, todo llega; tras apagar el correo de ausencias, solo eso deja de llegar")
    void apagarElCorreoDeAusencias() {
        assertThat(preferencias.mias(ana)).allMatch(p -> p.correo() && p.push());
        assertThat(preferencias.quiereCorreo(ana.getEmail(), NoticeType.AUSENCIA_RESUELTA)).isTrue();

        List<PreferenciaDeAviso> guardadas = preferencias.guardar(ana,
                List.of(new CambioDePreferencia(CategoriaDeAviso.AUSENCIAS, false, true)));
        // Dos veces: el segundo es un UPDATE, no un INSERT que choque.
        preferencias.guardar(ana, List.of(new CambioDePreferencia(CategoriaDeAviso.AUSENCIAS, false, true),
                new CambioDePreferencia(CategoriaDeAviso.OFERTAS, true, false)));

        assertThat(guardadas).filteredOn(p -> p.categoria() == CategoriaDeAviso.AUSENCIAS)
                .singleElement().returns(false, PreferenciaDeAviso::correo).returns(true, PreferenciaDeAviso::push);
        // Sin distinguir mayúsculas en el correo, como al entrar.
        assertThat(preferencias.quiereCorreo(ana.getEmail().toLowerCase(), NoticeType.AUSENCIA_RESUELTA)).isFalse();
        assertThat(preferencias.quierePush(ana.getId(), NoticeType.AUSENCIA_RESUELTA)).isTrue();
        assertThat(preferencias.quierePush(ana.getId(), NoticeType.OFERTA_PUBLICADA)).isFalse();
        assertThat(preferencias.quiereCorreo(ana.getEmail(), NoticeType.CORRECCION_RESUELTA)).isTrue();
        // Lo obligatorio llega siempre.
        assertThat(preferencias.quiereCorreo(ana.getEmail(), NoticeType.FIRMA_INVALIDADA)).isTrue();
    }

    @Test
    @DisplayName("Las obligatorias no se pueden apagar")
    void lasObligatoriasNo() {
        assertThatThrownBy(() -> preferencias.guardar(ana,
                List.of(new CambioDePreferencia(CategoriaDeAviso.OBLIGATORIOS, false, false))))
                .isInstanceOf(BusinessException.class);
        assertThat(preferencias.mias(ana)).filteredOn(p -> p.categoria() == CategoriaDeAviso.OBLIGATORIOS)
                .singleElement().returns(false, PreferenciaDeAviso::configurable);
    }
}
