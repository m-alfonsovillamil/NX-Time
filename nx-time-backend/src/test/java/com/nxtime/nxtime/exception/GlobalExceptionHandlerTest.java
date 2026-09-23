package com.nxtime.nxtime.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Unitarios de la traducción de violaciones de integridad (Fase A2).
 *
 * Lo que se protege aquí es que ganar el índice en vez de la comprobación
 * previa del servicio no cambie lo que ve quien está delante. Antes de esto,
 * una carrera al fichar devolvía 500 "Ha ocurrido un error inesperado" en
 * lugar de "Ya hay una jornada activa", y registrar dos veces la misma empresa
 * --por un endpoint público-- también.
 *
 * Los mensajes se comparan con los literales que lanzan los servicios: si
 * alguien cambia uno de los dos lados sin el otro, esto se pone rojo. Ese es
 * el punto del test, más que el código de estado.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * Una violación como la que llega de un UNIQUE o de una clave ajena: Spring
     * envolviendo a Hibernate envolviendo a JDBC, con el nombre de la
     * restricción ya extraído por Hibernate.
     */
    private DataIntegrityViolationException violacion(String sqlState, String constraint) {
        SQLException jdbc = new SQLException("detalle de PostgreSQL", sqlState);
        ConstraintViolationException hibernate =
                new ConstraintViolationException("no se pudo ejecutar", jdbc, constraint);
        return new DataIntegrityViolationException("no se pudo ejecutar", hibernate);
    }

    /**
     * Una violación como la que llega DE VERDAD de un EXCLUDE: Hibernate no sabe
     * extraer el nombre de la restricción del SQLSTATE 23P01 y lo deja a null.
     * El nombre solo viaja en el campo estructurado del error de PostgreSQL
     * (el campo 'n' del protocolo).
     *
     * Hasta la Fase B1 este caso se probaba con {@link #violacion}, que rellena
     * el nombre como si Hibernate lo supiera sacar: el test pasaba y en
     * producción los 409 salían con el mensaje genérico. Se vio levantando el
     * backend y haciendo peticiones.
     */
    private DataIntegrityViolationException violacionDePostgres(String sqlState, String constraint) {
        ServerErrorMessage servidor = new ServerErrorMessage(
                "SERROR\0C" + sqlState + "\0Mconflicting key value violates exclusion constraint \""
                        + constraint + "\"\0n" + constraint + "\0");
        PSQLException postgres = new PSQLException(servidor);
        ConstraintViolationException hibernate =
                new ConstraintViolationException("no se pudo ejecutar", postgres, null);
        return new DataIntegrityViolationException("no se pudo ejecutar", hibernate);
    }

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @DisplayName("Cada índice único contesta con el mismo mensaje que la comprobación previa del servicio")
    @CsvSource({
            // El literal de TimeEntryServiceImpl al ver una jornada abierta.
            "uq_registros_jornada_abierta, Ya hay una jornada activa.",
            // El de AuthServiceImpl.registerManager, que es endpoint PÚBLICO.
            "uq_empresas_nombre, La empresa ya existe. Solicita acceso al administrador.",
            // El de AuthServiceImpl.createEmployee, por los dos índices del correo.
            "uq_usuarios_email, El email ya está registrado.",
            "ux_usuarios_email_lower, El email ya está registrado.",
            "uq_festivos_empresa_fecha, Esa fecha ya es festivo para la empresa.",
            "uq_proyectos_empresa_codigo, Ya existe un proyecto con ese código.",
            "uq_candidaturas_oferta_usuario, Ya te has presentado a esa oferta.",
    })
    void unique_contestaConflictoYElMensajeDelServicio(String constraint, String mensajeEsperado) {
        ProblemDetail problema = handler.handleDataIntegrity(violacion("23505", constraint));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problema.getDetail()).isEqualTo(mensajeEsperado);
    }

    /**
     * Un EXCLUDE es un conflicto igual que un UNIQUE, pero con otro SQLSTATE
     * (23P01), y hasta la Fase B1 caía en la rama del 500. No se notaba porque
     * el único EXCLUDE que había lo capturaba su propio servicio; los de los
     * cuadrantes dependen de este manejador.
     */
    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @DisplayName("Un EXCLUDE es 409, no 500, y con su mensaje")
    @CsvSource(delimiter = '|', value = {
            "ex_asignaciones_horario_sin_solape | Esa persona ya tiene un cuadrante en alguna de esas fechas. "
                    + "Cierra el anterior antes de asignar el nuevo.",
            "ex_excepciones_horario_sin_solape | Ese día ya tiene una excepción que choca con esta. Bórrala antes.",
            "ex_tramos_sin_solape | Hay dos tramos del mismo día que se pisan.",
            "ex_asignaciones_sin_solape | Esa persona ya está en ese proyecto en alguna de esas fechas.",
    })
    void exclude_contestaConflicto(String constraint, String mensajeEsperado) {
        ProblemDetail problema = handler.handleDataIntegrity(violacionDePostgres("23P01", constraint));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problema.getDetail()).isEqualTo(mensajeEsperado);
    }

    @Test
    @DisplayName("Un índice sin mensaje propio sigue siendo 409, con una frase genérica")
    void unique_desconocido_conflictoGenerico() {
        ProblemDetail problema = handler.handleDataIntegrity(violacion("23505", "uq_algo_que_no_esta_en_la_tabla"));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problema.getDetail()).isEqualTo(
                "Eso choca con algo que ya existe. Vuelve a cargar los datos e inténtalo de nuevo.");
    }

    @Test
    @DisplayName("El nombre del constraint nunca sale al cliente")
    void unique_noFiltraElNombreDelConstraintNiElDetalleDePostgres() {
        ProblemDetail problema = handler.handleDataIntegrity(violacion("23505", "uq_registros_jornada_abierta"));

        // Describe el esquema y no le sirve a nadie al otro lado. Va al log.
        assertThat(problema.getDetail())
                .doesNotContain("uq_", "constraint", "detalle de PostgreSQL");
    }

    @Test
    @DisplayName("Una clave ajena es 409: hay datos que dependen de esto")
    void claveAjena_esConflicto() {
        ProblemDetail problema = handler.handleDataIntegrity(violacion("23503", "fk_usuarios_departamento"));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problema.getDetail()).isEqualTo("No se puede hacer eso porque hay datos que dependen de ello.");
    }

    @ParameterizedTest(name = "SQLSTATE {0}")
    @DisplayName("Un NOT NULL o un CHECK violados siguen siendo 500: son un bug nuestro, no un conflicto")
    @CsvSource({"23502", "23514"})
    void notNullYCheck_siguenSiendo500(String sqlState) {
        // Decirle "conflicto" a quien envía esto le invitaría a reintentar algo
        // que no va a funcionar nunca: lo que falta es validación nuestra.
        ProblemDetail problema = handler.handleDataIntegrity(violacion(sqlState, "ck_lo_que_sea"));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problema.getDetail()).isEqualTo("Ha ocurrido un error inesperado.");
    }

    @Test
    @DisplayName("Sin SQLException dentro tampoco revienta: se queda en 500")
    void sinCausaJdbc_noRevienta() {
        ProblemDetail problema = handler.handleDataIntegrity(
                new DataIntegrityViolationException("algo raro sin causa JDBC"));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
