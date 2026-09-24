package com.nxtime.nxtime.exception;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejo de errores centralizado (RFC 7807 / ProblemDetail).
 *
 * Antes de esto, NINGÚN error de la API llegaba al cliente con su
 * código real: tanto las excepciones no controladas como las
 * ResponseStatusException correctamente lanzadas acababan en 403,
 * porque usaban response.sendError(), que dispara un dispatch interno a
 * "/error" y ese dispatch vuelve a pasar por el filtro de seguridad y
 * cae en el anyRequest().denyAll() (ver el commit de la Fase 0 y los
 * tests de contrato marcados "bugActual").
 *
 * Los métodos @ExceptionHandler de aquí NO usan sendError(): construyen
 * la respuesta directamente a través de los HttpMessageConverter de
 * Spring MVC, dentro del propio ciclo de DispatcherServlet -- por eso
 * evitan ese bug de raíz, sin tocar la configuración de seguridad.
 *
 * Las excepciones "estándar" de Spring MVC (validación con @Valid,
 * JSON malformado, método HTTP no soportado...) las resuelve Spring
 * Boot automáticamente en ProblemDetail gracias a
 * spring.mvc.problemdetails.enabled=true (ver application.yml); no
 * hace falta declararlas aquí.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** SQLSTATE de PostgreSQL: dos filas que no pueden convivir. */
    private static final String UNIQUE_VIOLATION = "23505";
    /** SQLSTATE de PostgreSQL: se apunta a algo que no existe, o se borra algo al que se apunta. */
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    /**
     * SQLSTATE de PostgreSQL: un EXCLUDE. Es un conflicto igual que un UNIQUE
     * --dos filas que no pueden convivir--, pero con otro código, y hasta la
     * Fase B1 caía en la rama del 500. No se notaba porque el único EXCLUDE que
     * existía (el de asignaciones_proyecto) lo captura su propio servicio antes
     * de llegar aquí; los de los cuadrantes dependen de este manejador.
     */
    private static final String EXCLUSION_VIOLATION = "23P01";

    /**
     * Qué decirle a quien está delante cuando gana el índice en vez de la
     * comprobación previa del servicio.
     *
     * Son literalmente los mismos mensajes que lanza esa comprobación, y eso
     * es lo importante: el resultado no puede depender de cuál de las dos
     * barreras llegó primero. Si alguien cambia el texto en el servicio, tiene
     * que cambiarlo aquí -- {@code ConflictosDeIntegridadTest} lo comprueba.
     *
     * Los constraints que no están aquí caen en el mensaje genérico. Está bien
     * que así sea: la lista cubre lo que un cliente puede provocar por una
     * carrera, y no hace falta inventar una frase para cada índice del
     * esquema.
     */
    private static final Map<String, String> MENSAJES_POR_CONSTRAINT = Map.ofEntries(
            // Fichar dos veces a la vez (V1, índice parcial sobre jornada abierta).
            Map.entry("uq_registros_jornada_abierta", "Ya hay una jornada activa."),
            // Registrar la misma empresa dos veces. /auth/register-manager es público.
            Map.entry("uq_empresas_nombre", "La empresa ya existe. Solicita acceso al administrador."),
            // El correo, por sus dos índices: el original de V1 y el de V17, que
            // lo hace insensible a mayúsculas.
            Map.entry("uq_usuarios_email", "El email ya está registrado."),
            Map.entry("ux_usuarios_email_lower", "El email ya está registrado."),
            // Festivos (V4). Aquí el mensaje pierde el "...: Nochebuena" que sí
            // da la comprobación previa: desde el constraint no se puede saber
            // con qué festivo se ha chocado sin ir a buscarlo.
            Map.entry("uq_festivos_empresa_fecha", "Esa fecha ya es festivo para la empresa."),
            Map.entry("uq_festivos_nacional_fecha", "Esa fecha ya es festivo."),
            Map.entry("uq_departamentos_empresa_nombre", "Ya existe un departamento con ese nombre."),
            Map.entry("uq_proyectos_empresa_codigo", "Ya existe un proyecto con ese código."),
            Map.entry("uq_correcciones_una_viva_por_registro",
                    "Ese fichaje ya tiene una solicitud de corrección pendiente."),
            Map.entry("uq_borrados_una_pendiente_por_usuario", "Ya hay una solicitud de borrado pendiente."),
            Map.entry("uq_candidaturas_oferta_usuario", "Ya te has presentado a esa oferta."),
            // Proyectos (V10) y cuadrantes (V31): los EXCLUDE de las vigencias.
            Map.entry("ex_asignaciones_sin_solape",
                    "Esa persona ya está en ese proyecto en alguna de esas fechas."),
            Map.entry("uq_plantillas_horario_empresa_nombre", "Ya existe una plantilla de horario con ese nombre."),
            Map.entry("ex_tramos_sin_solape", "Hay dos tramos del mismo día que se pisan."),
            Map.entry("ex_asignaciones_horario_sin_solape",
                    "Esa persona ya tiene un cuadrante en alguna de esas fechas. "
                            + "Cierra el anterior antes de asignar el nuevo."),
            Map.entry("ex_excepciones_horario_sin_solape",
                    "Ese día ya tiene una excepción que choca con esta. Bórrala antes."),
            // B3: dos firmas del mismo mes a la vez; la segunda llega aquí.
            Map.entry("uq_firmas_vigente", "Ese mes ya está firmado."),
            // La cadena de auditoría (V27). Que esto salte significa que el
            // advisory lock de TimeEntryAuditListener no se pidió: el dato está
            // a salvo, pero es un fallo nuestro, así que además se registra
            // como error.
            Map.entry("uq_auditoria_hash_anterior",
                    "No se ha podido registrar la operación. Vuelve a intentarlo."));

    /**
     * Choques que no son una carrera entre dos usuarios sino una barrera
     * interna que ha fallado. Se contestan igual --un 409 reintentable, que es
     * lo que le sirve a quien está delante-- pero se registran con
     * {@code log.error} para que lleguen a Sentry en vez de perderse entre los
     * avisos normales.
     */
    private static final Set<String> CONSTRAINTS_QUE_SON_FALLO_NUESTRO = Set.of("uq_auditoria_hash_anterior");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        log.warn("Regla de negocio incumplida ({}): {}", ex.getStatus(), ex.getMessage());
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(TenantAccessException.class)
    public ProblemDetail handleTenantAccess(TenantAccessException ex) {
        log.warn("Acceso entre empresas bloqueado: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta acción.");
    }

    /**
     * Validación de parámetros sueltos (@RequestParam, @PathVariable) en
     * clases anotadas con @Validated -- por ejemplo el mes y el año de
     * los informes (Fase 10).
     *
     * Hace falta declararla a mano: spring.mvc.problemdetails.enabled
     * cubre MethodArgumentNotValidException (la de @Valid @RequestBody)
     * pero NO ConstraintViolationException, que es la que se lanza aquí.
     * Sin este manejador, pedir el mes 13 devolvía un 500 genérico en
     * vez de decir que el parámetro está mal.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detalle = ex.getConstraintViolations().stream()
                .map(violacion -> {
                    // El propertyPath incluye el nombre del método
                    // ("exportarHorasEnExcel.mes"); al cliente solo le
                    // interesa el parámetro.
                    String ruta = violacion.getPropertyPath().toString();
                    String parametro = ruta.contains(".") ? ruta.substring(ruta.lastIndexOf('.') + 1) : ruta;
                    return parametro + ": " + violacion.getMessage();
                })
                .collect(Collectors.joining("; "));

        log.warn("Parámetros inválidos: {}", detalle);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
    }

    /**
     * Dos personas han editado la misma fila a la vez y la segunda ha
     * llegado con una @Version vieja (Fase A).
     *
     * Sin este manejador caía en el 500 genérico de abajo, que es
     * mentira: no ha fallado nada del servidor, ha fallado una carrera
     * entre dos usuarios y volver a intentarlo la resuelve. Se hace
     * visible ahora porque la ficha de empleado es la primera pantalla
     * donde dos personas de RRHH editan de verdad al mismo empleado,
     * pero el manejador vale para cualquier entidad con @Version.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        log.warn("Edición concurrente sobre {}: {}", ex.getPersistentClassName(), ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Otra persona ha modificado estos datos mientras los editabas. Vuelve a cargarlos e inténtalo de nuevo.");
    }

    /**
     * Un índice o una clave ajena de la base ha parado la operación (Fase A2).
     *
     * Varias reglas están protegidas dos veces a propósito: el servicio
     * comprueba antes ("¿ya hay una jornada activa?") y la base lo garantiza
     * con un índice único parcial. La comprobación previa no es inútil --da el
     * mensaje bueno-- pero tampoco basta: entre el SELECT y el INSERT cabe
     * otra petición, así que en una carrera gana el índice.
     *
     * Sin este manejador, ganar el índice caía en el 500 genérico de abajo, y
     * eso es mentira dos veces: no ha fallado nada del servidor, y el cliente
     * no se entera de que lo que pedía choca con algo que ya existe. En
     * TimeEntryServiceImpl significaba que fichar dos veces a la vez devolvía
     * 500 en lugar de "ya hay una jornada activa"; en /auth/register-manager,
     * que es PÚBLICO, significaba un 500 provocable desde internet con solo
     * registrar dos veces la misma empresa.
     *
     * Los mensajes son deliberadamente los MISMOS que los de la comprobación
     * previa: a quien está delante le da igual cuál de las dos barreras saltó.
     *
     * No todo lo que llega aquí es un conflicto del cliente. Un NOT NULL o un
     * CHECK violados son un fallo de validación nuestro --un bug-- y decirle
     * al cliente "conflicto" le invitaría a reintentar algo que nunca va a
     * funcionar. Esos se quedan en 500, que es lo honesto, y con log.error
     * para que se vean.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String constraint = nombreDelConstraint(ex);
        String sqlState = estadoSql(ex);

        if (UNIQUE_VIOLATION.equals(sqlState) || EXCLUSION_VIOLATION.equals(sqlState)) {
            String mensaje = MENSAJES_POR_CONSTRAINT.getOrDefault(
                    constraint, "Eso choca con algo que ya existe. Vuelve a cargar los datos e inténtalo de nuevo.");
            // El nombre del constraint no sale al cliente: no le dice nada y
            // describe el esquema. Al log sí, que es donde hace falta.
            if (CONSTRAINTS_QUE_SON_FALLO_NUESTRO.contains(constraint)) {
                // Al cliente se le da un 409 reintentable, pero esto no es una
                // carrera normal entre dos usuarios: es que una barrera interna
                // no hizo su trabajo. Tiene que verse en Sentry.
                log.error("Choque que no debería poder ocurrir ({}): {}",
                        constraint, ex.getMostSpecificCause().getMessage());
            } else {
                log.warn("Conflicto de unicidad ({}): {}", constraint, ex.getMostSpecificCause().getMessage());
            }
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, mensaje);
        }

        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            log.warn("Conflicto de referencia ({}): {}", constraint, ex.getMostSpecificCause().getMessage());
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "No se puede hacer eso porque hay datos que dependen de ello.");
        }

        log.error("Violación de integridad no esperada ({}, SQLSTATE {})", constraint, sqlState, ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado.");
    }

    /**
     * El nombre del constraint que ha saltado, o {@code "desconocido"}.
     *
     * Hibernate lo expone en su propia {@code ConstraintViolationException},
     * que viaja envuelta en la de Spring. Si algún día cambia el envoltorio,
     * esto devuelve "desconocido" y el manejador sigue funcionando con el
     * mensaje genérico: el SQLSTATE, que es lo que decide el código de estado,
     * no depende de esto.
     */
    /**
     * El nombre de la restricción que ha saltado, o {@code "desconocido"}.
     *
     * Dos fuentes, por este orden. La de Hibernate sirve para UNIQUE, claves
     * ajenas y CHECK, pero <b>no sabe sacarlo de un EXCLUDE</b> (SQLSTATE
     * 23P01): lo deja a null. Se descubrió ejecutando la Fase B1 contra el
     * backend levantado, no en los tests, porque los tests construían la
     * excepción con el nombre ya puesto: los 409 de los cuadrantes salían con
     * el mensaje genérico en vez del suyo.
     *
     * La segunda fuente es el campo estructurado que manda PostgreSQL en el
     * propio error ({@link ServerErrorMessage#getConstraint()}), que no depende
     * de cómo esté redactado el mensaje ni del idioma del servidor.
     */
    private String nombreDelConstraint(DataIntegrityViolationException ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException hibernate
                    && hibernate.getConstraintName() != null) {
                return hibernate.getConstraintName();
            }
        }
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof PSQLException postgres) {
                ServerErrorMessage detalle = postgres.getServerErrorMessage();
                if (detalle != null && detalle.getConstraint() != null) {
                    return detalle.getConstraint();
                }
            }
        }
        return "desconocido";
    }

    /** El SQLSTATE de PostgreSQL, que es lo que distingue un choque de un bug. */
    private String estadoSql(DataIntegrityViolationException ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.warn("Fallo de autenticación: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error inesperado no controlado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado.");
    }
}
