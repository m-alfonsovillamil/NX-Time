package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.AccessCode;
import com.nxtime.nxtime.domain.AccessCodeType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.EmailNotSentException;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.AccessCodeRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unitarios de los códigos de acceso (ADR 014).
 *
 * El PasswordEncoder es BCrypt DE VERDAD, con el coste mínimo: lo que hay
 * que demostrar es que se guarda el hash y no el código, y un mock que
 * devolviera "hash" no lo demostraría. Que los intentos fallidos se queden
 * guardados aunque la petición falle (el noRollbackFor) solo se ve con una
 * transacción real: está en {@code AccessCodeIT}.
 */
@ExtendWith(MockitoExtension.class)
class AccessCodeServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-09-12T08:00:00Z");
    private static final String EMAIL = "ana@nxtime.test";

    @Mock
    private AccessCodeRepository accessCodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EmailSender emailSender;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AccessCodeServiceImpl service;
    private User ana;

    @BeforeEach
    void setUp() {
        service = new AccessCodeServiceImpl(accessCodeRepository, userRepository, refreshTokenRepository,
                passwordEncoder, emailSender, Clock.fixed(AHORA, ZoneOffset.UTC));
        ana = User.builder().id(10L).email(EMAIL).nombre("Ana").activo(true).build();
    }

    /** El código que salió en el correo: la única forma de verlo, porque en la base solo queda el hash. */
    private Map<String, Object> variablesDelCorreo(String plantilla) {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.captor();
        verify(emailSender).enviarObligatorio(eq(EMAIL), anyString(), eq(plantilla), variables.capture());
        return variables.getValue();
    }

    private AccessCode codigoGuardado() {
        ArgumentCaptor<AccessCode> guardado = ArgumentCaptor.forClass(AccessCode.class);
        verify(accessCodeRepository).save(guardado.capture());
        return guardado.getValue();
    }

    private AccessCode vigente(String codigo) {
        return AccessCode.builder().id(1L).usuario(ana).tipo(AccessCodeType.RECUPERACION)
                .codigoHash(passwordEncoder.encode(codigo))
                .creadoEn(AHORA.minusSeconds(60)).expiraEn(AHORA.plusSeconds(600))
                .build();
    }

    private void anaTieneElCodigo(AccessCode codigo) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(accessCodeRepository.findFirstByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNullOrderByCreadoEnDesc(ana))
                .thenReturn(Optional.of(codigo));
    }

    // ---- solicitarRecuperacion ----

    @Test
    @DisplayName("Pedir un código para un correo sin cuenta no manda nada ni guarda nada, y no lanza")
    void solicitar_correoSinCuenta_noEnviaNiGuarda() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatCode(() -> service.solicitarRecuperacion(EMAIL)).doesNotThrowAnyException();

        verifyNoInteractions(emailSender);
        verify(accessCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una cuenta dada de baja no recibe código: que recupere la contraseña no la reactiva")
    void solicitar_cuentaDadaDeBaja_noEnvia() {
        ana.setActivo(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ana));

        service.solicitarRecuperacion(EMAIL);

        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("Una cuenta activa recibe un código de 6 dígitos, y de él solo se guarda el hash")
    void solicitar_cuentaActiva_enviaSeisDigitosYGuardaSoloElHash() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(accessCodeRepository.findByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNull(ana)).thenReturn(List.of());

        // Con espacios, como llega a veces desde un formulario.
        service.solicitarRecuperacion("  " + EMAIL + " ");

        Map<String, Object> variables = variablesDelCorreo("access-code-recovery");
        String codigo = (String) variables.get("codigo");
        assertThat(codigo).matches("\\d{6}");
        assertThat(variables).containsEntry("validez", "15 minutos");

        AccessCode guardado = codigoGuardado();
        assertThat(guardado.getCodigoHash()).isNotEqualTo(codigo).doesNotContain(codigo);
        assertThat(passwordEncoder.matches(codigo, guardado.getCodigoHash())).isTrue();
        assertThat(guardado.getTipo()).isEqualTo(AccessCodeType.RECUPERACION);
        assertThat(guardado.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("Un código nuevo anula los anteriores: solo vale el último que ha llegado")
    void solicitar_anulaLosCodigosAnteriores() {
        AccessCode anterior = vigente("111111");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(accessCodeRepository.findByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNull(ana)).thenReturn(List.of(anterior));

        service.solicitarRecuperacion(EMAIL);

        assertThat(anterior.getAnuladoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("Por encima de 3 códigos en una hora no se manda otro, y tampoco se dice")
    void solicitar_porEncimaDelLimite_noEnvia() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        when(accessCodeRepository.countByUsuarioAndCreadoEnAfter(ana, AHORA.minus(Duration.ofHours(1))))
                .thenReturn((long) AccessCodeServiceImpl.MAXIMO_CODIGOS_POR_HORA);

        assertThatCode(() -> service.solicitarRecuperacion(EMAIL)).doesNotThrowAnyException();

        verifyNoInteractions(emailSender);
        verify(accessCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si el correo no sale no se lanza (diría que la cuenta existe) y no se guarda el código")
    void solicitar_siElCorreoFalla_noLanzaNiGuarda() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
        doThrow(new EmailNotSentException("SMTP caído", new RuntimeException("Connection refused")))
                .when(emailSender).enviarObligatorio(anyString(), anyString(), anyString(), anyMap());

        assertThatCode(() -> service.solicitarRecuperacion(EMAIL)).doesNotThrowAnyException();

        verify(accessCodeRepository, never()).save(any());
        verify(accessCodeRepository, never()).findByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNull(any());
    }

    // ---- emitirCodigoDeAlta ----

    @Test
    @DisplayName("El alta manda el correo de bienvenida con el código y la empresa, válido un día")
    void emitirAlta_enviaCorreoDeAltaConCodigoYEmpresa() {
        when(accessCodeRepository.findByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNull(ana)).thenReturn(List.of());

        service.emitirCodigoDeAlta(ana, "Empresa Test");

        Map<String, Object> variables = variablesDelCorreo("access-code-welcome");
        assertThat(variables).containsEntry("nombreEmpresa", "Empresa Test").containsEntry("validez", "24 horas");
        AccessCode guardado = codigoGuardado();
        assertThat(passwordEncoder.matches((String) variables.get("codigo"), guardado.getCodigoHash())).isTrue();
        assertThat(guardado.getTipo()).isEqualTo(AccessCodeType.ALTA);
        assertThat(guardado.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Si el correo del alta no sale, lanza un 503 para que el alta se deshaga y quien la da se entere")
    void emitirAlta_siElCorreoFalla_lanza503() {
        when(accessCodeRepository.findByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNull(ana)).thenReturn(List.of());
        doThrow(new EmailNotSentException("SMTP caído", new RuntimeException("Connection refused")))
                .when(emailSender).enviarObligatorio(anyString(), anyString(), anyString(), anyMap());

        assertThatThrownBy(() -> service.emitirCodigoDeAlta(ana, "Empresa Test"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    // ---- confirmar ----

    @Test
    @DisplayName("El código correcto fija la contraseña, se marca como usado y cierra todas las sesiones")
    void confirmar_codigoCorrecto_fijaContrasenaYCierraSesiones() {
        AccessCode codigo = vigente("012345");
        anaTieneElCodigo(codigo);
        when(refreshTokenRepository.revocarTodasLasDe(ana)).thenReturn(2);

        service.confirmar(EMAIL, " 012345 ", "nuevaSegura123");

        assertThat(passwordEncoder.matches("nuevaSegura123", ana.getContrasena())).isTrue();
        assertThat(codigo.getUsadoEn()).isEqualTo(AHORA);
        verify(userRepository).save(ana);
        verify(refreshTokenRepository).revocarTodasLasDe(ana);
    }

    @Test
    @DisplayName("Un código incorrecto lanza 400 y suma un intento, sin tocar la contraseña")
    void confirmar_codigoIncorrecto_lanzaYSumaUnIntento() {
        AccessCode codigo = vigente("012345");
        anaTieneElCodigo(codigo);

        assertThatThrownBy(() -> service.confirmar(EMAIL, "999999", "nuevaSegura123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AccessCodeServiceImpl.CODIGO_NO_VALIDO);

        assertThat(codigo.getIntentosFallidos()).isEqualTo(1);
        assertThat(codigo.getAnuladoEn()).isNull();
        assertThat(ana.getContrasena()).isNull();
        verify(accessCodeRepository).save(codigo);
        verify(refreshTokenRepository, never()).revocarTodasLasDe(any());
    }

    @Test
    @DisplayName("El quinto fallo anula el código, y después ni el correcto vale")
    void confirmar_quintoFallo_anulaElCodigo() {
        AccessCode codigo = vigente("012345");
        codigo.setIntentosFallidos(AccessCode.MAXIMO_INTENTOS - 1);
        anaTieneElCodigo(codigo);

        assertThatThrownBy(() -> service.confirmar(EMAIL, "999999", "nuevaSegura123"))
                .isInstanceOf(BusinessException.class);
        assertThat(codigo.getIntentosFallidos()).isEqualTo(AccessCode.MAXIMO_INTENTOS);
        assertThat(codigo.getAnuladoEn()).isEqualTo(AHORA);

        assertThatThrownBy(() -> service.confirmar(EMAIL, "012345", "nuevaSegura123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AccessCodeServiceImpl.CODIGO_NO_VALIDO);
        assertThat(ana.getContrasena()).isNull();
    }

    @Test
    @DisplayName("Un código caducado no vale aunque sea el correcto, y no suma intentos")
    void confirmar_codigoCaducado_noVale() {
        AccessCode codigo = vigente("012345");
        codigo.setExpiraEn(AHORA.minusSeconds(1));
        anaTieneElCodigo(codigo);

        assertThatThrownBy(() -> service.confirmar(EMAIL, "012345", "nuevaSegura123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AccessCodeServiceImpl.CODIGO_NO_VALIDO);

        assertThat(ana.getContrasena()).isNull();
        verify(accessCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un correo sin cuenta da exactamente el mismo error que un código incorrecto")
    void confirmar_correoSinCuenta_mismoError() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmar(EMAIL, "012345", "nuevaSegura123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AccessCodeServiceImpl.CODIGO_NO_VALIDO)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("La validez se escribe como la lee una persona en el correo")
    void describir_escribeHorasOMinutos() {
        assertThat(AccessCodeServiceImpl.describir(Duration.ofHours(24))).isEqualTo("24 horas");
        assertThat(AccessCodeServiceImpl.describir(Duration.ofHours(1))).isEqualTo("1 hora");
        assertThat(AccessCodeServiceImpl.describir(Duration.ofMinutes(15))).isEqualTo("15 minutos");
    }
}
