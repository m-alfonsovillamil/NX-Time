package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.MonthlySignatureResponse;
import com.nxtime.nxtime.dto.SignableMonthResponse;
import com.nxtime.nxtime.dto.SignatureVerificationResponse;
import com.nxtime.nxtime.dto.TeamSignatureResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * La firma mensual del registro horario (Fase B3, ADR 025).
 *
 * Una persona firma, con el mes ya terminado, que su registro es correcto. La
 * firma <b>no bloquea</b> nada: si después se corrige un fichaje del mes, la
 * firma queda invalidada en la misma transacción que la corrección, y el mes
 * vuelve a pedir firma. Es una firma de aceptación, no una firma electrónica
 * cualificada.
 */
public interface MonthlySignatureService {

    /** Los últimos meses terminados con algo que firmar, y cómo está cada uno. */
    List<SignableMonthResponse> misMeses(User actor);

    /**
     * Firmar un mes propio. 400 si no ha terminado; 409 si ya está firmado;
     * 422 si quedan jornadas abiertas, jornadas que cerró el sistema sin
     * corregir, o no hay ninguna.
     */
    MonthlySignatureResponse firmar(User actor, YearMonth mes, String ip);

    /** Lo firmado contra lo que hay hoy. La propia, o cualquiera de la empresa con firma:visar. */
    SignatureVerificationResponse verificar(long firmaId, User actor);

    /** Cómo está un mes para cada persona activa de la empresa (firma:visar). */
    List<TeamSignatureResponse> equipo(User actor, YearMonth mes);

    /** El visto bueno de la empresa a una firma vigente. Nunca a la propia. */
    MonthlySignatureResponse visar(long firmaId, User actor);

    /**
     * Tras cualquier cambio de un fichaje: si el mes en que empieza (o en el
     * que empezaba antes del cambio) está firmado y lo firmado ya no es lo que
     * hay, la firma se invalida. Corre dentro de la transacción del cambio.
     *
     * @param entradaAnterior la hora de entrada antes del cambio, si se sabe
     */
    void revisarTrasCambio(TimeEntry registro, Instant entradaAnterior, String motivo);

    /**
     * El recordatorio: a quien fichó el mes anterior a {@code hoy}, no lo ha
     * firmado y no ha recibido ya el recordatorio este mes.
     *
     * @return a cuántas personas se les ha recordado
     */
    int recordar(LocalDate hoy);
}
