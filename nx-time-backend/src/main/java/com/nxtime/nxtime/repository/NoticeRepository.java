package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Notice;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * Los avisos de una persona, del más reciente al más antiguo.
     *
     * Acotado a 50 porque en la Fase A no hay paginación y un empleado
     * acumula avisos indefinidamente: sin el tope, la lista crecería
     * para siempre y la pantalla acabaría descargando años de historia
     * para enseñar los cinco de arriba. Cuando duela, se pagina.
     */
    List<Notice> findTop50ByDestinatarioOrderByCreadoEnDesc(User destinatario);

    List<Notice> findByDestinatarioAndLeidoFalse(User destinatario);

    long countByDestinatarioAndLeidoFalse(User destinatario);
}
