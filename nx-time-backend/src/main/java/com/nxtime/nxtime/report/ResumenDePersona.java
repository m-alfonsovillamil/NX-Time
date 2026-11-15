package com.nxtime.nxtime.report;

/**
 * Una persona en la hoja «Resumen por persona» del Excel de la empresa (plan
 * N, paso N6): lo que pide una gestoría para la nómina, sin tener que sumar a
 * mano cientos de filas de jornadas.
 *
 * Todo en las unidades exactas en que se cuenta (segundos, minutos, días
 * hábiles). El redondeo a «7h 59m» es cosa de quien lo escribe, y solo al
 * final: el bug que más se ha repetido en este proyecto es truncar antes de
 * sumar.
 *
 * @param diasTrabajados      días distintos con alguna jornada cerrada.
 * @param minutosHorasExtra   de los avisos ACEPTADOS del mes: los que cuentan
 *                            (ADR 011), no los detectados sin revisar.
 * @param diasVacaciones      días hábiles de vacaciones aprobadas que caen en el mes.
 * @param diasOtrasAusencias  lo mismo, de cualquier otro tipo de ausencia.
 * @param detalleAusencias    «Vacaciones 3 · Consulta médica 1», o vacío.
 */
public record ResumenDePersona(
        String nombre,
        String codigoEmpleado,
        long diasTrabajados,
        long segundosNetos,
        long segundosPausa,
        long cerradasPorElSistema,
        long pendientesDeRevisar,
        long sinConexion,
        long minutosHorasExtra,
        int diasVacaciones,
        int diasOtrasAusencias,
        String detalleAusencias
) {

    /** «7h 05m», a partir de los segundos exactos. */
    public static String duracion(long segundos) {
        long minutos = segundos / 60;
        return (minutos / 60) + "h " + String.format("%02dm", minutos % 60);
    }

    /** Las horas con decimales, para quien las pase a una nómina: 7h 30m son 7,5. */
    public double horasDecimales() {
        return segundosNetos / 3600.0;
    }
}
