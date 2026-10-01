package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.Kiosk;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.KioskDtos.ConfirmKioskRequest;
import com.nxtime.nxtime.dto.KioskDtos.KioskClockRequest;
import com.nxtime.nxtime.dto.KioskDtos.KioskClockResponse;
import com.nxtime.nxtime.dto.KioskDtos.KioskCredential;
import com.nxtime.nxtime.dto.KioskDtos.KioskIdentity;
import com.nxtime.nxtime.dto.KioskDtos.KioskInfo;
import com.nxtime.nxtime.dto.KioskDtos.KioskPerson;
import com.nxtime.nxtime.dto.KioskDtos.KioskResponse;
import com.nxtime.nxtime.dto.KioskDtos.PairingStarted;
import com.nxtime.nxtime.dto.KioskDtos.PairingStatus;
import java.util.List;

/** El kiosco de fichaje (ADR 033): emparejarlo, gestionarlo y fichar en él. */
public interface KioskService {

    // Emparejar: lo pide la tablet, sin token todavía.

    PairingStarted iniciarEmparejamiento();

    /** @throws com.nxtime.nxtime.exception.ResourceNotFoundException si el secreto no es de ningún emparejamiento */
    PairingStatus estadoDelEmparejamiento(String secreto);

    // Gestionar: ADMIN, desde los ajustes de la empresa.

    /** @throws com.nxtime.nxtime.exception.ResourceNotFoundException si el código no existe o caducó */
    KioskResponse confirmar(ConfirmKioskRequest request, User actor);

    List<KioskResponse> listar(User actor);

    void revocar(long kioscoId, User actor);

    // Fichar: lo pide la tablet, con su token.

    KioskInfo info(Kiosk kiosco);

    List<KioskPerson> plantilla(Kiosk kiosco);

    KioskIdentity identificar(Kiosk kiosco, KioskCredential credencial);

    KioskClockResponse fichar(Kiosk kiosco, KioskClockRequest request);
}
