package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ImportacionPlantillaResponse;

/**
 * Dar de alta a toda una plantilla desde un CSV o un Excel (ADR 052).
 */
public interface ImportacionDePlantillaService {

    /** Lee y comprueba el fichero, fila a fila, sin crear nada. */
    ImportacionPlantillaResponse simular(String nombreDelFichero, byte[] contenido, User actor);

    /**
     * Lo crea todo, o nada si alguna fila tiene errores. Las invitaciones
     * quedan en cola: las manda {@link InvitacionesService#enviarPendientes}.
     */
    ImportacionPlantillaResponse importar(String nombreDelFichero, byte[] contenido, User actor);
}
