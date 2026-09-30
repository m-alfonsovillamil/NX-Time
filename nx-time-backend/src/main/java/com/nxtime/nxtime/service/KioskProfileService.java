package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.KioskDtos.KioskCard;
import com.nxtime.nxtime.dto.KioskDtos.MyKioskStatus;
import java.util.List;

/**
 * Lo de cada persona para fichar en un kiosco (ADR 033): su PIN y su tarjeta.
 * El PIN lo elige ella y nadie más lo ve (ADR 014).
 */
public interface KioskProfileService {

    MyKioskStatus estado(User actor);

    /** @throws com.nxtime.nxtime.exception.BusinessException 400 si el PIN es demasiado fácil */
    MyKioskStatus fijarPin(User actor, String pin);

    MyKioskStatus quitarPin(User actor);

    /** Su tarjeta. Si no tenía, se le crea. */
    KioskCard tarjeta(User actor);

    /** Una tarjeta nueva: la anterior, impresa o en el móvil, deja de valer. */
    KioskCard regenerarTarjeta(User actor);

    /** Las de toda la plantilla de alta, para imprimirlas (RRHH). A quien no tenía, se le crea. */
    List<KioskCard> tarjetasDeLaEmpresa(User actor);
}
