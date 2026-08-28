package com.nxtime.nxtime.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renderiza de verdad las plantillas de correo (Fase 10).
 *
 * Hace falta porque los errores de Thymeleaf -- una expresión mal
 * escrita, un fragmento que no existe, una variable mal referenciada --
 * NO se detectan al compilar: las plantillas son ficheros de recursos,
 * no código. Sin un test así, un fallo de plantilla solo aparecería al
 * mandar el correo, y como {@link EmailSender} se traga los fallos a
 * propósito, ni siquiera rompería nada de forma visible: los correos
 * simplemente dejarían de llegar en silencio.
 *
 * Monta el motor a mano en vez de levantar todo el contexto de Spring:
 * lo que se prueba es la plantilla, no el cableado.
 *
 * Tiene que ser un {@link SpringTemplateEngine}, no el
 * {@code TemplateEngine} plano de Thymeleaf: el plano evalúa las
 * expresiones con OGNL, que ni siquiera está en el classpath de un
 * proyecto Spring Boot (usa SpringEL). Además de fallar por
 * ClassNotFoundException, un motor plano estaría validando las
 * plantillas con un lenguaje de expresiones DISTINTO del que usa la
 * aplicación en producción, que es justo lo contrario de lo que este
 * test pretende.
 */
class EmailTemplateRenderingTest {

    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        // #temporals (el formateo de LocalDate que usan las plantillas)
        // no necesita registrar nada: desde Thymeleaf 3.1 el antiguo
        // módulo "extras-java8time" está integrado en el núcleo.
    }

    private String render(String plantilla, Map<String, Object> variables) {
        Context contexto = new Context();
        contexto.setVariables(variables);
        return templateEngine.process("email/" + plantilla, contexto);
    }

    @Test
    @DisplayName("La plantilla de petición nueva renderiza con los datos del empleado")
    void absenceRequested_renderiza() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombreGestor", "Marta");
        vars.put("nombreEmpleado", "Ana");
        vars.put("tipo", "VACACIONES");
        vars.put("fechaInicio", LocalDate.of(2026, 6, 1));
        vars.put("fechaFin", LocalDate.of(2026, 6, 5));
        vars.put("motivo", "Viaje familiar");

        String html = render("absence-requested", vars);

        assertThat(html).contains("Marta").contains("Ana").contains("VACACIONES");
        assertThat(html).contains("01/06/2026").contains("05/06/2026");
        assertThat(html).contains("Viaje familiar");
        assertThat(html).contains("NX Time"); // viene del marco común
    }

    @Test
    @DisplayName("Un motivo nulo no rompe la plantilla ni deja un hueco raro")
    void absenceRequested_motivoNulo_noRompe() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombreGestor", "Marta");
        vars.put("nombreEmpleado", "Ana");
        vars.put("tipo", "MEDICO");
        vars.put("fechaInicio", LocalDate.of(2026, 6, 1));
        vars.put("fechaFin", LocalDate.of(2026, 6, 1));
        vars.put("motivo", null);

        String html = render("absence-requested", vars);

        assertThat(html).contains("Ana");
        assertThat(html).doesNotContain("Motivo:");
    }

    @Test
    @DisplayName("La plantilla de resolución distingue aprobada de rechazada")
    void absenceResolved_distingueAprobadaDeRechazada() {
        Map<String, Object> base = new HashMap<>();
        base.put("nombreEmpleado", "Ana");
        base.put("tipo", "VACACIONES");
        base.put("fechaInicio", LocalDate.of(2026, 6, 1));
        base.put("fechaFin", LocalDate.of(2026, 6, 5));
        base.put("resolutor", "Marta");
        base.put("comentario", null);

        Map<String, Object> aprobada = new HashMap<>(base);
        aprobada.put("aprobada", true);
        String htmlAprobada = render("absence-resolved", aprobada);

        Map<String, Object> rechazada = new HashMap<>(base);
        rechazada.put("aprobada", false);
        String htmlRechazada = render("absence-resolved", rechazada);

        assertThat(htmlAprobada).contains("APROBADA").doesNotContain("RECHAZADA");
        assertThat(htmlRechazada).contains("RECHAZADA").doesNotContain(">APROBADA<");
    }

    @Test
    @DisplayName("El comentario del gestor aparece cuando lo hay")
    void absenceResolved_muestraElComentario() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombreEmpleado", "Ana");
        vars.put("tipo", "VACACIONES");
        vars.put("fechaInicio", LocalDate.of(2026, 6, 1));
        vars.put("fechaFin", LocalDate.of(2026, 6, 5));
        vars.put("resolutor", "Marta");
        vars.put("aprobada", false);
        vars.put("comentario", "Coincide con el cierre trimestral.");

        assertThat(render("absence-resolved", vars)).contains("Coincide con el cierre trimestral.");
    }

    @Test
    @DisplayName("Los comentarios de desarrollo no viajan dentro del correo")
    void plantillas_noFiltranComentariosDeDesarrollo() {
        // Detectado probando contra MailHog: los comentarios HTML
        // normales (<!-- ... -->) SÍ llegan a la salida de Thymeleaf, así
        // que las notas para quien mantiene la plantilla acababan dentro
        // de cada correo enviado. Se escriben como comentarios de parser
        // (<!--/* ... */-->), que Thymeleaf elimina al renderizar.
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombreEmpleado", "Ana");
        vars.put("nombreEmpresa", "TechCorp");
        vars.put("email", "ana@techcorp.demo");

        String html = render("employee-welcome", vars);

        assertThat(html).doesNotContain("<!--");
        assertThat(html).doesNotContain("Fase 10");
    }

    @Test
    @DisplayName("La bienvenida renderiza y NO contiene ninguna contraseña")
    void employeeWelcome_renderizaSinContrasena() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombreEmpleado", "Ana");
        vars.put("nombreEmpresa", "TechCorp");
        vars.put("email", "ana@techcorp.demo");

        String html = render("employee-welcome", vars);

        assertThat(html).contains("Ana").contains("TechCorp").contains("ana@techcorp.demo");
        // La plantilla dice explícitamente que la contraseña llega por
        // otro canal; que no se cuele nunca es la razón de este test.
        assertThat(html).doesNotContain("contrasena").doesNotContain("password");
    }
}
