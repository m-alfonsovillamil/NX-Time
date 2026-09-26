package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Notice;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * Los avisos de alguien, del más reciente al más antiguo, por páginas
     * (Fase A7).
     *
     * Hasta entonces era un "Top 50": un empleado acumula avisos para
     * siempre, así que se acotó, pero los anteriores al quincuagésimo
     * quedaban sin forma de verse desde ningún sitio. El id desempata dos
     * avisos del mismo instante, que los hay: un barrido nocturno los crea
     * en ráfaga, y sin desempate uno podía saltar de una página a otra.
     */
    Page<Notice> findByDestinatarioOrderByCreadoEnDescIdDesc(User destinatario, Pageable pagina);

    List<Notice> findByDestinatarioAndLeidoFalse(User destinatario);

    long countByDestinatarioAndLeidoFalse(User destinatario);

    /** Todos, sin paginar: para la exportación de datos personales (RGPD, arts. 15 y 20). */
    List<Notice> findByDestinatarioOrderByCreadoEnDesc(User destinatario);

    /**
     * A quién se le ha publicado ya un aviso de un tipo desde un instante
     * (Fase B3): el recordatorio de firma sale una vez al mes aunque la tarea
     * corra a diario.
     */
    @Query("SELECT n.destinatario.id FROM avisos n WHERE n.tipo = :tipo AND n.creadoEn >= :desde")
    List<Long> findDestinatariosDeTipoDesde(
            @Param("tipo") NoticeType tipo,
            @Param("desde") Instant desde);
}
