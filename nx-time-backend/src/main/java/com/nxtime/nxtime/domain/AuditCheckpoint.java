package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hasta dónde se ha comprobado la cadena de auditoría, y con qué resultado
 * (Fase A4). Tabla {@code puntos_control_auditoria}, ver V28.
 *
 * Verificar la traza era una operación de todo o nada: se leían todas las filas
 * de {@link TimeEntryAudit}, de todas las empresas, con sus dos columnas JSONB.
 * El registro se conserva cuatro años por ley y crece con cada fichaje, así que
 * esa consulta solo podía ir a peor — y como costaba, se pedía poco; y como se
 * pedía poco, una manipulación podía tardar meses en verse.
 *
 * Un punto de control dice «hasta la fila N la cadena estaba intacta, y el hash
 * de la fila N era H». Con eso, la comprobación de cada noche solo mira lo
 * escrito desde entonces.
 *
 * <b>El {@code hash} no es decorativo</b>: es con lo que enlaza la fila
 * siguiente, así que sin él una verificación incremental no podría comprobar su
 * primer eslabón y tendría que empezar desde cero.
 *
 * Los totales se guardan acumulados desde el principio de la traza para que una
 * verificación incremental pueda seguir informando del total sin recorrerla
 * entera. La base comprueba que {@code comprobadas + soloEnlace = filas}: un
 * punto de control que cuenta mal es peor que no tenerlo.
 *
 * Como {@code auditoria_fichaje}, es de solo INSERT: V28 le revoca UPDATE y
 * DELETE al rol de la aplicación. Un punto de control que se puede reescribir
 * no demuestra nada.
 */
@Entity(name = "puntos_control_auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** La última fila de auditoría que entró en esta comprobación. */
    @Column(name = "hasta_id", nullable = false, unique = true)
    private long hastaId;

    /** El hash de esa fila, para poder seguir la cadena desde aquí. */
    @Column(nullable = false, length = 64)
    private String hash;

    /** Filas revisadas desde el principio de la traza. */
    @Column(nullable = false)
    private long filas;

    /** De esas, a cuántas se les ha podido recalcular el hash (versión 2 en adelante). */
    @Column(nullable = false)
    private long comprobadas;

    /** De esas, a cuántas solo se les ha podido comprobar el enlace (ver V26). */
    @Column(name = "solo_enlace", nullable = false)
    private long soloEnlace;

    @Column(name = "verificado_en", nullable = false)
    private Instant verificadoEn;
}
