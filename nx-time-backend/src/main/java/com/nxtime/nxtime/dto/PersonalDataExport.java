package com.nxtime.nxtime.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Todos los datos personales de una persona, tal como se le entregan
 * (RGPD, art. 15 acceso y art. 20 portabilidad).
 *
 * <b>Es un contrato con la persona, no con la app</b>: por eso tiene su propio
 * modelo y no reutiliza los DTO de las pantallas. Si mañana cambia lo que
 * enseña una pantalla, lo que se le entrega a alguien que ejerce un derecho
 * no debe cambiar por accidente.
 *
 * Los enums van por su nombre ({@code "VACACIONES"}) y no por su etiqueta:
 * el art. 20 pide un formato "estructurado y de lectura mecánica", y para
 * leer ya está el PDF.
 *
 * Lo que <b>no</b> va, y por qué, queda dicho en {@link #notas()} dentro del
 * propio fichero: la contraseña (ni cifrada), el contenido de los ficheros
 * adjuntos (van aparte, por su propio endpoint), y los mensajes del canal de
 * denuncias (se consultan en el canal, con el código).
 */
public record PersonalDataExport(
        Instant generadoEn,
        Persona persona,
        List<Fichaje> fichajes,
        List<PausaAnadida> pausasAnadidas,
        List<Ausencia> ausencias,
        List<SaldoVacaciones> saldosVacaciones,
        List<Correccion> correccionesPedidas,
        List<HorasExtra> horasExtra,
        List<Proyecto> proyectos,
        List<Cuadrante> cuadrantes,
        List<ExcepcionDeCuadrante> excepcionesDeCuadrante,
        List<Aviso> avisos,
        List<Adjunto> adjuntos,
        List<Candidatura> candidaturas,
        List<Denuncia> denunciasIdentificadas,
        List<String> notas
) {

    public record Persona(
            long id, String nombre, String apellidos, String email, String rol, String empresa,
            String departamento, String puesto, LocalDate fechaNacimiento, BigDecimal horasSemanales,
            boolean activo, Instant fechaBaja) {
    }

    public record Fichaje(
            long id, Instant horaEntrada, Instant horaSalida, long segundosPausa,
            boolean anulado, boolean jornadaIncompleta, Long corrigeAlFichaje) {
    }

    public record PausaAnadida(
            long fichaje, Instant inicio, Instant fin, String motivo, Instant creadaEn,
            boolean porAprobacion, boolean anulada) {
    }

    public record Ausencia(
            LocalDate fechaInicio, LocalDate fechaFin, String tipo, String estado, String motivo,
            Instant fechaResolucion, String comentarioResolucion) {
    }

    public record SaldoVacaciones(int anio, int diasTotales) {
    }

    public record Correccion(
            long fichaje, Instant horaEntradaPropuesta, Instant horaSalidaPropuesta,
            Instant pausaInicioPropuesta, Instant pausaFinPropuesta, String motivo, String estado,
            Instant creadoEn, Instant fechaResolucion, String comentarioResolucion) {
    }

    public record HorasExtra(
            LocalDate fecha, String tipo, int minutosExtra, int minutosEsperados, String estado,
            String justificacion, Instant fechaRevision) {
    }

    public record Proyecto(String codigo, String nombre, LocalDate desde, LocalDate hasta) {
    }

    /** Qué plantilla de horario se tuvo y cuándo (Fase B1). Hasta null = sigue vigente. */
    public record Cuadrante(String plantilla, LocalDate desde, LocalDate hasta) {
    }

    /** Un día que se salió del cuadrante. Horas null en un día libre. */
    public record ExcepcionDeCuadrante(
            LocalDate fecha, String tipo, String horaInicio, String horaFin, String motivo) {
    }

    public record Aviso(String tipo, String titulo, String cuerpo, boolean leido, Instant creadoEn) {
    }

    public record Adjunto(String tipo, String nombre, String mime, long tamanoBytes, Instant subidoEn, boolean vigente) {
    }

    public record Candidatura(String oferta, String carta, String estado, Instant creadoEn, Instant fechaResolucion) {
    }

    public record Denuncia(
            String categoria, String descripcion, String estado, Instant creadoEn,
            Instant acuseReciboEn, Instant resueltaEn) {
    }
}
