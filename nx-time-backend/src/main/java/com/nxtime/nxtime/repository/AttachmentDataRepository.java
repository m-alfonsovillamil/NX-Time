package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.AttachmentData;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Los bytes de un adjunto, aparte de sus metadatos (ver ADR 007).
 *
 * Tiene repositorio propio precisamente para que leerlos sea siempre una
 * decisión explícita: nadie se los trae sin querer al cargar un
 * {@link com.nxtime.nxtime.domain.Attachment}.
 */
public interface AttachmentDataRepository extends JpaRepository<AttachmentData, Long> {
}
