package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.TimeEntry;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Qué se firma cuando alguien firma su registro de un mes, y su SHA-256
 * (Fase B3, ADR 025).
 *
 * Una sola definición para firmar y para comprobar después, por lo mismo que
 * {@link HuellaDeAuditoria}: dos sitios que decidieran qué se firma serían dos
 * verdades, y la que fallaría sería la de verificar.
 *
 * <h2>Qué entra y qué no</h2>
 *
 * Entra lo que dice el registro horario: cada jornada viva del mes (sin las
 * anuladas), con su entrada, su salida, su pausa y si la cerró el sistema. Es
 * lo mismo que enseña el PDF mensual.
 *
 * <b>No entra el id del fichaje</b>, a propósito: una corrección anula el
 * original y crea otro, y si se deja la misma hora, lo firmado sigue siendo
 * verdad. Tampoco entra el reparto por proyectos, que no es registro horario.
 *
 * <b>Las horas van truncadas a segundos.</b> Un fichaje recién escrito lleva
 * nanosegundos en memoria y microsegundos al releerlo de la base; firmar eso
 * haría que el mismo mes diera dos huellas según de dónde se leyera, y cada
 * fichaje invalidaría firmas que nadie había tocado.
 */
public final class HuellaDelMes {

    /** La definición de hoy. Si cambia, las firmas viejas se comprueban con la suya. */
    public static final short VERSION = 1;

    private HuellaDelMes() {
    }

    /** Lo que se firma, ya ordenado. */
    public record Resumen(
            short version,
            long usuarioId,
            long empresaId,
            String mes,
            int jornadas,
            long segundosNetos,
            List<Jornada> detalle) {
    }

    public record Jornada(
            Instant entrada,
            Instant salida,
            long segundosPausa,
            boolean cerradaPorElSistema) {
    }

    /**
     * El resumen del mes a partir de sus fichajes. Quien llama pasa los
     * fichajes vivos (sin anulados) que EMPIEZAN en el mes, en hora de España:
     * el mismo criterio que el informe mensual.
     */
    public static Resumen resumen(long usuarioId, long empresaId, YearMonth mes, List<TimeEntry> fichajes) {
        List<Jornada> detalle = fichajes.stream()
                .filter(fichaje -> !fichaje.isAnulado())
                .sorted(Comparator.comparing(TimeEntry::getHoraEntrada))
                .map(fichaje -> new Jornada(
                        segundos(fichaje.getHoraEntrada()),
                        segundos(fichaje.getHoraSalida()),
                        fichaje.getSegundosPausaAcumulados(),
                        fichaje.isJornadaIncompleta()))
                .toList();
        long netos = detalle.stream()
                .filter(jornada -> jornada.salida() != null)
                .mapToLong(jornada -> jornada.entrada().until(jornada.salida(), ChronoUnit.SECONDS)
                        - jornada.segundosPausa())
                .sum();
        return new Resumen(VERSION, usuarioId, empresaId, mes.toString(), detalle.size(), netos, detalle);
    }

    /** El SHA-256 de la forma canónica del resumen. */
    public static String hash(Resumen resumen) {
        return Canonico.sha256(Canonico.deObjeto(resumen));
    }

    private static Instant segundos(Instant instante) {
        return instante == null ? null : instante.truncatedTo(ChronoUnit.SECONDS);
    }
}
