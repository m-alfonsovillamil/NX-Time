package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PreferenciasDeAvisos.CambioDePreferencia;
import com.nxtime.nxtime.dto.PreferenciasDeAvisos.PreferenciaDeAviso;
import java.util.List;

/**
 * Cómo le llegan los avisos a cada persona (ADR 059; plan N, paso N9).
 *
 * Lo consultan los dos sitios por los que sale un aviso hacia fuera: el envío
 * de correo y el push. La campana de la aplicación no pregunta: siempre recibe.
 */
public interface PreferenciasDeAvisosService {

    /** Todas las categorías, con lo que ha elegido esa persona (o lo de por defecto). */
    List<PreferenciaDeAviso> mias(User usuario);

    /** Guarda las categorías que vienen. Una obligatoria da 400. */
    List<PreferenciaDeAviso> guardar(User usuario, List<CambioDePreferencia> cambios);

    /** Si a quien tiene ese correo le llega por correo un aviso de ese tipo. */
    boolean quiereCorreo(String email, NoticeType tipo);

    /** Si a esa persona le llega al móvil un aviso de ese tipo. */
    boolean quierePush(long usuarioId, NoticeType tipo);
}
