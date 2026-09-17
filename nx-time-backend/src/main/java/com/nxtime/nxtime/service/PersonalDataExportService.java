package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PersonalDataExport;

/**
 * Exportar los datos personales de uno mismo (RGPD, arts. 15 y 20).
 *
 * Solo existe la versión "los míos": el derecho de acceso lo ejerce la
 * persona sobre sus datos, y no necesita que nadie lo apruebe. No hay un
 * "exportar los datos de otra persona", y no debe haberlo.
 */
public interface PersonalDataExportService {

    PersonalDataExport exportar(User persona);
}
