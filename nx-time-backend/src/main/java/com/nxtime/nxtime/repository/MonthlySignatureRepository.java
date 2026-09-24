package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.MonthlySignature;
import com.nxtime.nxtime.domain.MonthlySignatureStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonthlySignatureRepository extends JpaRepository<MonthlySignature, Long> {

    Optional<MonthlySignature> findByUsuario_IdAndAnioAndMesAndEstado(
            long usuarioId, int anio, int mes, MonthlySignatureStatus estado);

    /** Todas las de una persona, la más reciente primero: las vigentes y el histórico. */
    List<MonthlySignature> findByUsuario_IdOrderByAnioDescMesDescFirmadaEnDesc(long usuarioId);

    /** Las de una empresa en un mes, para quien visa. */
    List<MonthlySignature> findByEmpresa_IdAndAnioAndMes(long empresaId, int anio, int mes);

    /**
     * Las vigentes de una persona en unos meses, como {@code anio * 12 + mes}.
     * Es la consulta que corre en cada cambio de un fichaje
     * (SignatureInvalidationListener): con el índice parcial, casi siempre
     * vuelve vacía y barata.
     */
    @Query("SELECT f FROM firmas_mensuales f WHERE f.usuario.id = :usuarioId "
            + "AND f.estado = com.nxtime.nxtime.domain.MonthlySignatureStatus.VIGENTE "
            + "AND (f.anio * 12 + f.mes) IN :meses")
    List<MonthlySignature> findVigentesDeUsuarioEnMeses(
            @Param("usuarioId") long usuarioId,
            @Param("meses") java.util.Collection<Integer> meses);

    /** Quién tiene ya firmado un mes, en todas las empresas: para el recordatorio. */
    @Query("SELECT f.usuario.id FROM firmas_mensuales f WHERE f.anio = :anio AND f.mes = :mes "
            + "AND f.estado = com.nxtime.nxtime.domain.MonthlySignatureStatus.VIGENTE")
    List<Long> findUsuariosConFirmaVigente(@Param("anio") int anio, @Param("mes") int mes);
}
