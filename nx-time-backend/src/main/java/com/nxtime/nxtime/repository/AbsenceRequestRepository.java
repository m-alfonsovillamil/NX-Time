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
     * Lo mismo que {@link #findSolapadas}, pero para toda la empresa:
     * es lo que pinta el calendario del equipo (Fase C).
     *
     * Deja fuera las RECHAZADAS por la misma razón que allí -- una
     * ausencia denegada no ocupa el calendario de nadie -- y mantiene
     * las PENDIENTES, que son justo las que un gestor necesita ver
     * antes de aprobar otra que se solape.
     *
     * El {@code JOIN FETCH} no es un adorno: el nombre de cada persona
     * se pinta en su banda, y {@code @ManyToOne} es EAGER por defecto,
     * así que sin él cada fila dispararía su propio SELECT sobre
     * usuarios -- el mismo N+1 que la Fase 10 tuvo que arreglar en los
     * festivos.
     */
    @Query("SELECT a FROM peticiones_ausencia a JOIN FETCH a.usuario "
            + "WHERE a.empresa.id = :empresaId "
            + "AND a.estado <> com.nxtime.nxtime.domain.AbsenceStatus.RECHAZADA "
            + "AND a.fechaInicio <= :hasta AND a.fechaFin >= :desde "
            + "ORDER BY a.fechaInicio")
    List<AbsenceRequest> findSolapadasDeEmpresa(
            @Param("empresaId") long empresaId,
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

    /**
     * Todas las ausencias APROBADAS que tocan el rango, de cualquier
     * persona (Fase F).
     *
     * El detector de horas extra las necesita para prorratear la jornada
     * semanal: una semana con dos días de vacaciones tiene tres días
     * hábiles, no cinco, y medirla contra 37,5 h haría saltar un aviso a
     * cualquiera que recupere un poco de trabajo.
     *
     * Va sin filtro de empresa a propósito: el proceso nocturno recorre
     * todas de una pasada, y filtrar por cada una convertiría una
     * consulta en tantas como empresas haya. Es el único sitio del
     * proyecto donde eso es correcto -- aquí no hay usuario autenticado
     * de quien deducir un tenant.
     */
    @Query("SELECT a FROM peticiones_ausencia a "
            + "WHERE a.estado = com.nxtime.nxtime.domain.AbsenceStatus.APROBADA "
            + "AND a.fechaInicio <= :hasta AND a.fechaFin >= :desde")
    List<AbsenceRequest> findAprobadasEnRango(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    // Agregados del dashboard (Fase 10): contar en la base de datos, no
    // traerse las filas para hacer size() sobre la lista.
    long countByUsuarioAndEstado(User usuario, AbsenceStatus estado);

    long countByEmpresa_IdAndEstado(long empresaId, AbsenceStatus estado);
}
