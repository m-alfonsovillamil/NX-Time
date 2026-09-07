package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.OvertimeAlert;
import com.nxtime.nxtime.domain.OvertimeStatus;
import com.nxtime.nxtime.domain.OvertimeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OvertimeAlertRepository extends JpaRepository<OvertimeAlert, Long> {

    /**
     * El aviso que ya existe para ese periodo, si lo hay. Es la clave
     * del índice único {@code uq_horas_extra_usuario_fecha_tipo} (V12):
     * el proceso nocturno vuelve a mirar días que ya miró -- una
     * corrección de la Fase E puede cambiar una jornada del mes pasado
     * -- y sin esto crearía un duplicado en cada pasada.
     */
    Optional<OvertimeAlert> findByUsuario_IdAndFechaAndTipo(
            long usuarioId, LocalDate fecha, OvertimeType tipo);

    /**
     * Los avisos de una persona en un año, del más reciente al más
     * antiguo. El {@code JOIN FETCH} sobre el registro es opcional en la
     * relación pero no en la práctica: la pantalla enseña la hora del
     * fichaje que disparó cada aviso diario.
     */
    @Query("SELECT a FROM avisos_horas_extra a LEFT JOIN FETCH a.registro "
            + "WHERE a.usuario.id = :usuarioId "
            + "AND a.fecha >= :desde AND a.fecha <= :hasta "
            + "ORDER BY a.fecha DESC, a.tipo ASC")
    List<OvertimeAlert> findDeUsuarioEnRango(
            @Param("usuarioId") long usuarioId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Los avisos de toda la empresa en un rango: la bandeja de quien
     * revisa. {@code JOIN FETCH} sobre el usuario para pintar el nombre
     * de cada fila sin un SELECT por fila.
     *
     * Ordena por estado ASC a propósito: {@code OvertimeStatus} declara
     * ABIERTO primero, así que lo que está sin revisar sube arriba.
     */
    @Query("SELECT a FROM avisos_horas_extra a JOIN FETCH a.usuario LEFT JOIN FETCH a.registro "
            + "WHERE a.empresa.id = :empresaId "
            + "AND a.fecha >= :desde AND a.fecha <= :hasta "
            + "ORDER BY a.estado ASC, a.fecha DESC")
    List<OvertimeAlert> findDeEmpresaEnRango(
            @Param("empresaId") long empresaId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * <b>La bolsa anual del art. 35.2 ET, calculada al leer.</b>
     *
     * No hay tabla de contadores (ver la nota en V12): un contador
     * denormalizado se desincroniza en cuanto una corrección de la Fase
     * E cambia una jornada del mes pasado. Esto es la misma decisión que
     * ya toma el saldo de vacaciones, que también se deriva.
     *
     * Solo suman los ACEPTADO. Un aviso ABIERTO todavía no se ha
     * revisado y uno JUSTIFICADO se revisó y se descartó, así que
     * contarlos daría un tope agotado por excesos que nadie ha
     * reconocido como horas extra.
     *
     * Cuenta los SEMANAL además de los DIARIA a sabiendas de que pueden
     * solaparse -- un martes de once horas aparece en su aviso diario y
     * dentro del total de su semana. No es doble contabilidad accidental
     * sino el criterio de esta fase: son dos límites legales distintos
     * (art. 34.3 y jornada pactada) y pasarse de los dos es peor que
     * pasarse de uno. Quien revisa puede justificar el que sobre, y esa
     * decisión humana es justo lo que este contador respeta.
     */
    @Query("SELECT COALESCE(SUM(a.minutosExtra), 0) FROM avisos_horas_extra a "
            + "WHERE a.usuario.id = :usuarioId "
            + "AND a.estado = com.nxtime.nxtime.domain.OvertimeStatus.ACEPTADO "
            + "AND a.fecha >= :desde AND a.fecha <= :hasta")
    int sumarMinutosAceptados(
            @Param("usuarioId") long usuarioId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Avisos todavía sin revisar cuyo periodo cae en el rango.
     *
     * Lo usa el barrido nocturno para RETIRAR los que ya no proceden: si
     * una corrección de la Fase E recorta la jornada del martes, el aviso
     * de ese martes tiene que desaparecer, y el barrido por sí solo no se
     * enteraría — un día que se queda sin fichajes válidos deja de salir
     * en el agregado, así que nunca se volvería a mirar.
     *
     * Solo los ABIERTO: uno ya revisado lo decidió una persona, y el
     * proceso nocturno no deshace decisiones humanas.
     */
    List<OvertimeAlert> findByEstadoAndFechaBetween(
            OvertimeStatus estado, LocalDate desde, LocalDate hasta);

    long countByEmpresa_IdAndEstado(long empresaId, OvertimeStatus estado);

    long countByUsuario_IdAndEstado(long usuarioId, OvertimeStatus estado);
}
