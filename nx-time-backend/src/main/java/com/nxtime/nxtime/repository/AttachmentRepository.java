package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Attachment;
import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByUsuarioAndTipo(User usuario, AttachmentType tipo);

    List<Attachment> findByUsuario(User usuario);
}
