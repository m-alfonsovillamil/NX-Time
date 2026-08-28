package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.User;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbsenceRequestRepository extends JpaRepository<AbsenceRequest, Long> {

    List<AbsenceRequest> findByUsuario(User usuario);

    // Filtra por AbsenceRequest.empresa directamente (denormalizado
    // desde la Fase 3) en vez de navegar usuario.empresa.id: más
    // simple y aprovecha el índice (empresa_id, estado) del esquema.
    List<AbsenceRequest> findByEmpresa_IdAndEstado(long empresaId, AbsenceStatus estado);

    List<AbsenceRequest> findByEmpresa_IdAndEstadoIsNot(long empresaId, AbsenceStatus estado);

    /**
     * Peticiones del usuario que se solapan con el rango dado y siguen
     * "vivas" (PENDIENTE o APROBADA); las RECHAZADAS no estorban.
     * Fase 9: antes no se comprobaba el solapamiento en absoluto (ver
     * auditoría del plan) -- se podían pedir las mismas vacaciones dos
     * veces, o vacaciones encima de una baja médica ya aprobada.
     *
     * Dos rangos [a1,a2] y [b1,b2] se solapan si a1 <= b2 y a2 >= b1:
     * más simple y más fiable que enumerar los cuatro casos a mano.
     */
    @Query("SELECT a FROM peticiones_ausencia a WHERE a.usuario = :usuario "
            + "AND a.estado <> com.nxtime.nxtime.domain.AbsenceStatus.RECHAZADA "
            + "AND a.fechaInicio <= :hasta AND a.fechaFin >= :desde")
    List<AbsenceRequest> findSolapadas(
            @Param("usuario") User usuario,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Vacaciones ya APROBADAS que caen (aunque sea parcialmente) dentro
     * del año indicado. Base del cálculo de días consumidos: el saldo no
     * guarda un contador, se deriva de aquí (ver VacationBalance).
     */
    @Query("SELECT a FROM peticiones_ausencia a WHERE a.usuario = :usuario "
            + "AND a.tipo = com.nxtime.nxtime.domain.AbsenceType.VACACIONES "
            + "AND a.estado = com.nxtime.nxtime.domain.AbsenceStatus.APROBADA "
            + "AND a.fechaInicio <= :finDeAnio AND a.fechaFin >= :inicioDeAnio")
    List<AbsenceRequest> findVacacionesAprobadasDelAnio(
            @Param("usuario") User usuario,
            @Param("inicioDeAnio") LocalDate inicioDeAnio,
            @Param("finDeAnio") LocalDate finDeAnio);

    // Agregados del dashboard (Fase 10): contar en la base de datos, no
    // traerse las filas para hacer size() sobre la lista.
    long countByUsuarioAndEstado(User usuario, AbsenceStatus estado);

    long countByEmpresa_IdAndEstado(long empresaId, AbsenceStatus estado);
}
