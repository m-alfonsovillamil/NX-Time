package com.nxtime.nxtime.notification;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
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
 * **No propaga los fallos de envío**: los registra y sigue. Es
 * deliberado y va de la mano de que las notificaciones se disparen
 * después del commit (ver {@link NotificationListener}): si el servidor
 * SMTP está caído, el empleado ya tiene su ausencia aprobada en la base
 * de datos y lo último que queremos es que un fallo de correo haga
 * parecer que la operación falló. El correo es un aviso, no parte de la
 * operación de negocio.
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

    @Value("${application.mail.from}")
    private String remitente;

    public EmailSender(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void enviar(String destinatario, String asunto, String plantilla, Map<String, Object> variables) {
        try {
            Context contexto = new Context();
            contexto.setVariables(variables);
            String cuerpo = templateEngine.process("email/" + plantilla, contexto);

            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpo, true); // true = HTML

            mailSender.send(mensaje);
            log.info("Correo '{}' enviado a {}", plantilla, destinatario);
        } catch (MailException | jakarta.mail.MessagingException e) {
            // A propósito no se relanza: ver el Javadoc de la clase.
            log.error("No se pudo enviar el correo '{}' a {}: {}", plantilla, destinatario, e.getMessage());
        }
    }
}
