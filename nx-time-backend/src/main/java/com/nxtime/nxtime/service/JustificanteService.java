package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.JustificanteResponse;
import com.nxtime.nxtime.dto.JustificantesResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * El justificante de una ausencia (ADR 062): un parte de baja, un justificante
 * médico. Es un dato de salud, así que lo ven solo la persona y quien tenga
 * {@code ausencia:justificante:leer} (RRHH y ADMIN), y la empresa tiene que
 * haberlo activado.
 */
public interface JustificanteService {

    /** Los bytes para descargar, con el nombre y el tipo con que se guardaron. */
    record Contenido(byte[] contenido, String nombre, String mime) {
    }

    /**
     * Si la empresa los tiene activados y, de las ausencias que se piden, los
     * justificantes que quien pregunta puede ver.
     */
    JustificantesResponse de(List<Long> peticiones, User actor);

    /** Subir o reemplazar el de una ausencia propia. */
    JustificanteResponse subir(long peticionId, MultipartFile fichero, User actor);

    Contenido descargar(long peticionId, User actor);

    /** Quitar el de una ausencia propia. */
    void borrar(long peticionId, User actor);

    /** Activarlos o desactivarlos en la empresa (ADMIN). Desactivar no borra los que haya. */
    JustificantesResponse activar(boolean activo, User actor);
}
