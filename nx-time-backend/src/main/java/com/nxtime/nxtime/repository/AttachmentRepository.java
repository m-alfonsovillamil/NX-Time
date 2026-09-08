package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Attachment;
import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /**
     * El adjunto VIGENTE de una persona para un tipo.
     *
     * Desde la fase H puede haber varios del mismo tipo: los que alguna
     * candidatura congeló siguen en la tabla, marcados como no vigentes.
     * Sin el filtro, esto devolvería uno cualquiera de ellos.
     */
    Optional<Attachment> findByUsuarioAndTipoAndVigenteTrue(User usuario, AttachmentType tipo);

    /** Los vigentes de una persona: lo que se enseña en su perfil. */
    List<Attachment> findByUsuarioAndVigenteTrue(User usuario);
}
