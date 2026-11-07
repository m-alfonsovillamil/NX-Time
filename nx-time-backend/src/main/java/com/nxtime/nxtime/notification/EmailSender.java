package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.config.CondicionesDelServicio;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renderiza una plantilla Thymeleaf y la envía como correo HTML
 * (Fase 10).
 *
 * Hay dos formas de enviar, y la diferencia es a propósito:
 *
 * - {@link #enviar} **no propaga los fallos de envío**: los registra y
 *   sigue. Va de la mano de que las notificaciones se disparen después del
 *   commit (ver {@link NotificationListener}): si el servidor SMTP está
 *   caído, el empleado ya tiene su ausencia aprobada en la base de datos y
 *   lo último que queremos es que un fallo de correo haga parecer que la
 *   operación falló. Ese correo es un aviso, no parte de la operación.
 *
 * - {@link #enviarObligatorio} (09/2026) **sí los propaga**. Es la del
 *   código de acceso (ver ADR 014): si ese correo no sale, su destinatario
 *   no puede entrar, y quien da el alta tiene que enterarse en ese momento
 *   en vez de dejar creada una cuenta en la que nadie puede entrar.
 *
 * El contrapunto está en la auditoría de fichajes, que sí corre ANTES
 * del commit y sí tumba la operación si falla: allí la traza es un
 * requisito legal, aquí es una cortesía. La diferencia es intencionada.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final CondicionesDelServicio servicio;

    @Value("${application.mail.from}")
    private String remitente;

    public EmailSender(JavaMailSender mailSender, TemplateEngine templateEngine, CondicionesDelServicio servicio) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.servicio = servicio;
    }

    public void enviar(String destinatario, String asunto, String plantilla, Map<String, Object> variables) {
        try {
            enviarObligatorio(destinatario, asunto, plantilla, variables);
        } catch (EmailNotSentException e) {
            // A propósito no se relanza: ver el Javadoc de la clase.
            // Sin el destinatario (revisión de seguridad del 17/10/2026): los
            // registros acaban en Render y en Sentry, y no son sitio para correos.
            log.error("No se pudo enviar el correo '{}': {}", plantilla, e.getCause().getMessage());
        }
    }

    /**
     * Como {@link #enviar}, pero si el correo no sale lo dice.
     *
     * @throws EmailNotSentException si el servidor de correo lo rechaza o no responde
     */
    public void enviarObligatorio(
            String destinatario, String asunto, String plantilla, Map<String, Object> variables) {
        try {
            Context contexto = new Context();
            contexto.setVariables(completar(plantilla, variables));
            String cuerpo = templateEngine.process("email/" + plantilla, contexto);

            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpo, true); // true = HTML

            mailSender.send(mensaje);
            log.info("Correo '{}' enviado", plantilla);
        } catch (MailException | jakarta.mail.MessagingException e) {
            throw new EmailNotSentException("No se pudo enviar el correo '" + plantilla + "'", e);
        }
    }

    /** Dónde está el icono de la marca en la web (public/iconos de la web). */
    static final String RUTA_DEL_ICONO = "/iconos/icono-192.png";

    /**
     * Las variables de quien llama, más lo que pinta el marco común
     * (layout.html) en todos los correos, para que ninguna plantilla ni ninguna
     * llamada tenga que acordarse:
     *
     * <ul>
     *   <li>{@code producto}, {@code titular} y {@code web}: de la configuración
     *       del titular, el único sitio del nombre (ver CondicionesDelServicio).
     *       Si quien llama ya los manda, valen los suyos.</li>
     *   <li>{@code urlIcono}: el icono de la N, servido por la web.</li>
     *   <li>{@code botonUrl} y {@code botonTexto}: el botón a la operativa, si
     *       esa plantilla tiene uno ({@link BotonDelCorreo}).</li>
     * </ul>
     *
     * Sin web configurada no hay icono ni botón: no se inventa una dirección.
     */
    Map<String, Object> completar(String plantilla, Map<String, Object> variables) {
        Map<String, Object> todas = new HashMap<>(variables);
        todas.putIfAbsent("producto", servicio.producto());
        todas.putIfAbsent("titular", servicio.titular());
        String web = servicio.web();
        if (web == null || web.isBlank()) {
            return todas;
        }
        todas.putIfAbsent("web", web);
        todas.putIfAbsent("urlIcono", web + RUTA_DEL_ICONO);
        BotonDelCorreo.de(plantilla).ifPresent(boton -> {
            todas.put("botonUrl", web + "/" + boton.ruta());
            todas.put("botonTexto", boton.texto());
        });
        return todas;
    }
}
