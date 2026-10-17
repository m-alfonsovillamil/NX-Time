package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PlatformActionDTO;
import com.nxtime.nxtime.exception.BusinessException;
import java.util.List;

/**
 * El ciclo de vida de una empresa en el servicio (ADR 046): pendiente de
 * aprobar, activa, suspendida, o rechazada y borrada. Lo que hace el operador
 * queda en {@code acciones_de_plataforma}.
 */
public interface CompanyLifecycleService {

    /**
     * El 423 que recibe quien intenta entrar en una empresa que no está
     * activa, con lo que tiene que saber: que espere o a quién escribir.
     */
    BusinessException noDisponible(Company empresa);

    /** Si la empresa no está activa, lanza {@link #noDisponible}. */
    void exigirActiva(Company empresa);

    /** Una empresa acaba de quedar esperando aprobación: se avisa a los operadores. */
    void avisarDeQueEspera(Company empresa, User admin);

    void aprobar(long empresaId, User operador);

    /** Solo una pendiente. Se avisa a quien la registró y se borra entera. */
    void rechazar(long empresaId, User operador, String motivo);

    /** Solo una activa. Se cierran todas sus sesiones y se avisa a sus ADMIN. */
    void suspender(long empresaId, User operador, String motivo);

    void reactivar(long empresaId, User operador);

    /** El operador ha abierto el detalle de una empresa: queda escrito. */
    void registrarConsulta(Company empresa, User operador);

    List<PlatformActionDTO> historial(long empresaId);

    /** Las condiciones de la empresa de quien pregunta. */
    com.nxtime.nxtime.dto.CompanyTermsResponse condiciones(User admin);

    /** Quien pregunta acepta, en nombre de su empresa, la versión en vigor. */
    com.nxtime.nxtime.dto.CompanyTermsResponse aceptarCondiciones(User admin);
}
