package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recorre la traza de auditoría y dice si sigue intacta.
 *
 * El RD-ley 8/2019 exige conservar el registro de jornada cuatro años y que sea
 * fiable, y el Real Decreto de registro horario digital que se tramita en 2026
 * concreta esa fiabilidad en "integridad demostrable". **Demostrable** es la
 * palabra: una cadena de hashes que nadie comprueba nunca no demuestra nada, y
 * hasta ahora nadie podía comprobarla (ver {@link HuellaDeAuditoria}).
 *
 * Comprueba dos cosas distintas, y las cuenta por separado porque significan
 * cosas distintas:
 *
 * <ol>
 *   <li><b>El enlace.</b> Cada fila guarda el hash de la anterior. Si no
 *       coincide, es que falta una fila, sobra una o se han reordenado. Esto
 *       se puede comprobar en TODAS las filas, también en las viejas.</li>
 *   <li><b>El contenido.</b> Se recalcula el hash de la fila y se compara con
 *       el guardado. Si no coincide, a esa fila le han cambiado algo por
 *       debajo. Solo se puede en las filas de la versión 2 en adelante.</li>
 * </ol>
 *
 * Las filas viejas no se dan por buenas ni por rotas: se cuentan aparte, como
 * "no comprobables". Decir que algo está verificado cuando no se ha podido
 * verificar es peor que no tener verificador.
 *
 * Se para en el primer problema. Un fallo en la fila 40 hace que todas las
 * siguientes fallen también --así funciona una cadena--, y una lista de mil
 * errores esconde el único dato que importa: dónde empezó.
 */
@Service
@Transactional(readOnly = true)
public class VerificadorDeAuditoria {

    private final TimeEntryAuditRepository auditRepository;
    private final HuellaDeAuditoria huella;

    public VerificadorDeAuditoria(TimeEntryAuditRepository auditRepository, HuellaDeAuditoria huella) {
        this.auditRepository = auditRepository;
        this.huella = huella;
    }

    /**
     * Verifica la traza entera, en orden de escritura.
     *
     * La cadena es global (cada fila enlaza con la anterior de la tabla, sea
     * de la empresa que sea), así que verificar "solo mi empresa" no tendría
     * sentido: el enlace se rompería en cada salto. Es una consecuencia de
     * cómo se encadena, y por eso esto lo puede pedir RRHH, no cualquiera.
     */
    public AuditIntegrityResponse verificar() {
        List<TimeEntryAudit> filas = auditRepository.findAllByOrderByIdAsc();

        long comprobadas = 0;
        long soloEnlace = 0;
        String hashDeLaAnterior = null;

        for (TimeEntryAudit fila : filas) {
            String problema = revisar(fila, hashDeLaAnterior);
            if (problema != null) {
                return AuditIntegrityResponse.rota(filas.size(), comprobadas, soloEnlace, fila.getId(), problema);
            }
            if (fila.getVersionHash() >= HuellaDeAuditoria.VERSION_VERIFICABLE) {
                comprobadas++;
            } else {
                soloEnlace++;
            }
            hashDeLaAnterior = fila.getHash();
        }
        return AuditIntegrityResponse.intacta(filas.size(), comprobadas, soloEnlace);
    }

    /** Qué le pasa a esta fila, o null si está bien. */
    private String revisar(TimeEntryAudit fila, String hashDeLaAnterior) {
        // El enlace: lo que la fila dice que había antes contra lo que había
        // antes de verdad. La primera de todas no enlaza con nada.
        if (!java.util.Objects.equals(fila.getHashAnterior(), hashDeLaAnterior)) {
            return "el enlace con la fila anterior no cuadra: falta una fila, sobra una o se han reordenado";
        }
        if (fila.getVersionHash() < HuellaDeAuditoria.VERSION_VERIFICABLE) {
            return null;
        }
        if (!huella.calcular(fila, hashDeLaAnterior).equals(fila.getHash())) {
            return "el contenido de la fila no coincide con su hash: le han cambiado algo";
        }
        return null;
    }
}
