package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Complaint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    /**
     * La denuncia a la que corresponde un código de seguimiento.
     *
     * Busca por el HASH, que es lo único que se guarda. Nótese que no
     * hay ningún método que vaya en la otra dirección — de la denuncia
     * al código — y no se puede escribir aunque se quisiera: el hash no
     * se invierte. Esa imposibilidad es la garantía del anonimato, y por
     * eso vive en el esquema y no en una regla que alguien pueda
     * saltarse desde un servicio.
     */
    Optional<Complaint> findByCodigoHash(String codigoHash);

    /**
     * La bandeja de quien instruye: las de su empresa, las abiertas
     * primero y dentro de cada grupo las más antiguas arriba.
     *
     * El orden no es estético. Los plazos del art. 9.2 corren desde que
     * se presentó la denuncia, así que la más vieja sin acuse es la que
     * está más cerca de incumplirse; una bandeja ordenada por fecha
     * descendente enseña justo las que menos urgen.
     *
     * El {@code LEFT JOIN FETCH} del denunciante evita una consulta por
     * fila. Es LEFT y no un JOIN a secas porque las anónimas no tienen
     * denunciante: con un INNER desaparecerían de la bandeja, que es el
     * peor fallo posible en esta pantalla.
     */
    @Query("SELECT d FROM denuncias d LEFT JOIN FETCH d.denunciante "
            + "WHERE d.empresa.id = :empresaId "
            + "ORDER BY CASE WHEN d.estado IN "
            + "  (com.nxtime.nxtime.domain.ComplaintStatus.RECIBIDA, "
            + "   com.nxtime.nxtime.domain.ComplaintStatus.EN_INVESTIGACION) "
            + "  THEN 0 ELSE 1 END ASC, d.creadoEn ASC")
    List<Complaint> findDeEmpresa(@Param("empresaId") long empresaId);

    /**
     * Las que presentó una persona identificándose.
     *
     * Las anónimas de esa misma persona <b>no salen y no pueden salir</b>:
     * no hay columna que las relacione con ella. Es la contrapartida del
     * anonimato, y la pantalla lo dice antes de que alguien lo descubra
     * echándolas en falta.
     */
    @Query("SELECT d FROM denuncias d WHERE d.denunciante.id = :usuarioId "
            + "ORDER BY d.creadoEn DESC")
    List<Complaint> findMias(@Param("usuarioId") long usuarioId);

    /** Para el contador del panel de empresa: cuántas esperan. */
    @Query("SELECT COUNT(d) FROM denuncias d WHERE d.empresa.id = :empresaId "
            + "AND d.estado IN (com.nxtime.nxtime.domain.ComplaintStatus.RECIBIDA, "
            + "                 com.nxtime.nxtime.domain.ComplaintStatus.EN_INVESTIGACION)")
    long contarAbiertas(@Param("empresaId") long empresaId);
}
