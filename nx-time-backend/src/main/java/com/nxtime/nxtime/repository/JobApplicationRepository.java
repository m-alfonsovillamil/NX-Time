package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.JobApplication;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /**
     * Si algún CV congelado apunta a este adjunto (Fase H).
     *
     * Lo consulta {@code AttachmentServiceImpl} antes de borrar: un CV
     * referenciado por una candidatura deja de estar vigente pero no se
     * destruye. Quien lo garantiza de verdad es el {@code RESTRICT} de
     * la clave ajena; esto es para no llegar hasta ahí.
     */
    boolean existsByCv_Id(long adjuntoId);

    /** La candidatura de una persona a una oferta, si la presentó. */
    Optional<JobApplication> findByOferta_IdAndUsuario_Id(long ofertaId, long usuarioId);

    /**
     * Las candidaturas de una oferta, para quien las valora.
     *
     * Trae el usuario y el CV de una vez: la pantalla enseña el nombre y
     * ofrece la descarga en cada fila, así que sin esto listar quince
     * candidatos serían treinta consultas más.
     */
    @Query("SELECT c FROM candidaturas c JOIN FETCH c.usuario JOIN FETCH c.cv "
            + "LEFT JOIN FETCH c.resueltaPor "
            + "WHERE c.oferta.id = :ofertaId ORDER BY c.creadoEn ASC")
    List<JobApplication> findDeOferta(@Param("ofertaId") long ofertaId);

    /** Las que ha presentado una persona, para que vea en qué han quedado. */
    @Query("SELECT c FROM candidaturas c JOIN FETCH c.oferta o "
            + "LEFT JOIN FETCH o.departamento "
            + "WHERE c.usuario.id = :usuarioId ORDER BY c.creadoEn DESC")
    List<JobApplication> findMias(@Param("usuarioId") long usuarioId);

    /**
     * Cuántas candidaturas tiene cada oferta de las que se le pasen.
     *
     * Una sola consulta para toda la lista en vez de un {@code count}
     * por fila. <b>Con la lista vacía no se puede llamar</b>: un
     * {@code IN} sin elementos no es SQL válido. Lo corta quien llama.
     */
    @Query("SELECT c.oferta.id, COUNT(c) FROM candidaturas c "
            + "WHERE c.oferta.id IN :ofertaIds GROUP BY c.oferta.id")
    List<Object[]> contarPorOferta(@Param("ofertaIds") List<Long> ofertaIds);
}
