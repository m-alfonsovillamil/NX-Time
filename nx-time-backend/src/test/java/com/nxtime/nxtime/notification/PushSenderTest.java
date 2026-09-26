package com.nxtime.nxtime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.repository.PushDeviceRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/** Qué hace {@link PushSender} con cada aviso (Fase B5). Sin Google: la pasarela es un mock. */
@ExtendWith(MockitoExtension.class)
class PushSenderTest {

    @Mock
    private PushDeviceRepository deviceRepository;

    @Mock
    private PushGateway gateway;

    @Mock
    private ObjectProvider<PushGateway> proveedor;

    private final NotificationEvents.NoticePublished evento =
            new NotificationEvents.NoticePublished(10L, NoticeType.CORRECCION_RESUELTA, "historial");

    private PushSender sender(boolean conPasarela) {
        when(proveedor.getIfAvailable()).thenReturn(conPasarela ? gateway : null);
        return new PushSender(deviceRepository, proveedor);
    }

    @Test
    @DisplayName("Sin credenciales de Firebase no hace nada, ni siquiera consulta la base")
    void sinPasarelaNoHaceNada() {
        sender(false).enviar(evento);

        verify(deviceRepository, never()).findTokensDeUsuarioActivo(anyLong());
    }

    @Test
    @DisplayName("Sin dispositivos no llama a Google")
    void sinDispositivosNoEnvia() {
        when(deviceRepository.findTokensDeUsuarioActivo(10L)).thenReturn(List.of());

        sender(true).enviar(evento);

        verify(gateway, never()).enviar(any(), any());
    }

    @Test
    @DisplayName("Manda el texto genérico del tipo y la ruta, y borra los tokens que ya no valen")
    void enviaYLimpia() {
        when(deviceRepository.findTokensDeUsuarioActivo(10L)).thenReturn(List.of("a", "b"));
        when(gateway.enviar(List.of("a", "b"), Map.of(
                "tipo", "CORRECCION_RESUELTA",
                "titulo", TextoDePush.TITULO,
                "cuerpo", TextoDePush.de(NoticeType.CORRECCION_RESUELTA),
                "ruta", "historial"))).thenReturn(List.of("b"));

        sender(true).enviar(evento);

        verify(deviceRepository).deleteByTokenIn(List.of("b"));
    }

    @Test
    @DisplayName("Si Google falla, el fallo no sube: el aviso y el correo ya están hechos")
    void unFalloNoSube() {
        when(deviceRepository.findTokensDeUsuarioActivo(10L)).thenReturn(List.of("a"));
        when(gateway.enviar(any(), any())).thenThrow(new IllegalStateException("FCM caído"));

        sender(true).enviar(evento);

        verify(deviceRepository, never()).deleteByTokenIn(any());
    }

    @Test
    @DisplayName("Todo tipo de aviso tiene un texto de push corto que no cita a nadie")
    void todosLosTiposTienenTexto() {
        assertThat(Arrays.stream(NoticeType.values()).map(TextoDePush::de))
                .allSatisfy(texto -> assertThat(texto).isNotBlank().hasSizeLessThanOrEqualTo(80));
    }
}
