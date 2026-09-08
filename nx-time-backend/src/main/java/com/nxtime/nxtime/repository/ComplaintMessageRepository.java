package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ComplaintMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintMessageRepository extends JpaRepository<ComplaintMessage, Long> {

    /**
     * La conversación de un expediente, en orden de lectura.
     *
     * {@code LEFT JOIN FETCH} del autor: los mensajes del denunciante
     * anónimo no lo tienen.
     */
    @Query("SELECT m FROM denuncia_mensajes m LEFT JOIN FETCH m.autor "
            + "WHERE m.denuncia.id = :denunciaId ORDER BY m.creadoEn ASC, m.id ASC")
    List<ComplaintMessage> findDeDenuncia(@Param("denunciaId") long denunciaId);

    /**
     * Cuántos mensajes tiene cada denuncia de las que se le pasen.
     *
     * Una sola consulta para toda la bandeja, en vez de un
     * {@code count} por fila: la lista del instructor puede traer
     * decenas de expedientes y el contador es un dato de adorno — no
     * merece un viaje a la base cada uno.
     *
     * <b>Con la lista vacía no se puede llamar</b>: Hibernate traduce un
     * {@code IN} sin elementos a {@code in ()}, que PostgreSQL rechaza
     * como error de sintaxis. Lo corta quien llama.
     */
    @Query("SELECT m.denuncia.id, COUNT(m) FROM denuncia_mensajes m "
            + "WHERE m.denuncia.id IN :denunciaIds GROUP BY m.denuncia.id")
    List<Object[]> contarPorDenuncia(@Param("denunciaIds") List<Long> denunciaIds);
}
