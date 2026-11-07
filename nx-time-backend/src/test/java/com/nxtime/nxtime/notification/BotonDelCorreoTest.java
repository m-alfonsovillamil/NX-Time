package com.nxtime.nxtime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nxtime.nxtime.config.CondicionesDelServicio;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

/** El botón de cada correo a su operativa (BotonDelCorreo y EmailSender#conBoton). */
class BotonDelCorreoTest {

    private static EmailSender conWeb(String web) {
        CondicionesDelServicio servicio = new CondicionesDelServicio(
                "NX Time", "Titular", "privacidad@ejemplo.test", web, "2026-11-01", true);
        return new EmailSender(mock(JavaMailSender.class), mock(TemplateEngine.class), servicio);
    }

    @Test
    @DisplayName("El recordatorio de firma lleva a la página de firmas de la web, con su texto")
    void elRecordatorioDeFirmaLlevaAFirmar() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("nombreDestinatario", "Ana");

        Map<String, Object> con = conWeb("https://nxtime-web.com/").completar("signature-reminder", variables);

        assertThat(con).containsEntry("botonUrl", "https://nxtime-web.com/firmas")
                .containsEntry("botonTexto", "Firmar mi registro")
                .containsEntry("nombreDestinatario", "Ana");
        assertThat(variables).as("no toca el mapa de quien llama").doesNotContainKey("botonUrl");
    }

    @Test
    @DisplayName("Cada botón lleva al mismo sitio que el aviso de la app que va con ese correo")
    void elDestinoEsElDelAviso() {
        assertThat(BotonDelCorreo.de("correction-requested")).get()
                .extracting(BotonDelCorreo.Boton::ruta).isEqualTo("correcciones/pendientes");
        assertThat(BotonDelCorreo.de("absence-requested")).get()
                .extracting(BotonDelCorreo.Boton::ruta).isEqualTo("ausencias-equipo/pendientes");
        BotonDelCorreo.todos().values().forEach(boton -> {
            assertThat(boton.ruta()).isNotBlank().doesNotStartWith("/");
            assertThat(boton.texto()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Cada plantilla con botón existe de verdad: un nombre mal escrito dejaría el correo sin él")
    void lasPlantillasExisten() {
        BotonDelCorreo.todos().keySet().forEach(plantilla -> assertThat(
                getClass().getClassLoader().getResource("templates/email/" + plantilla + ".html"))
                .as(plantilla).isNotNull());
    }

    @Test
    @DisplayName("Los correos de seguridad no llevan botón: no se acostumbra a nadie a pulsar para entrar")
    void losDeSeguridadNoLlevanBoton() {
        EmailSender sender = conWeb("https://nxtime-web.com");
        for (String plantilla : List.of("access-code-welcome", "access-code-recovery", "access-code-confirm",
                "second-factor", "account-already-exists", "sso-linked", "deletion-executed")) {
            assertThat(sender.completar(plantilla, Map.of())).as(plantilla).doesNotContainKey("botonUrl");
        }
    }

    @Test
    @DisplayName("Sin web configurada no hay botón (no se inventa una dirección)")
    void sinWebNoHayBoton() {
        assertThat(conWeb("").completar("signature-reminder", Map.of())).doesNotContainKey("botonUrl");
    }
}
