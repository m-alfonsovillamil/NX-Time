package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * La lápida de una fila del tramo común de la auditoría cuya empresa ya se
 * borró (V46, ADR 050): su sitio y sus dos hashes, sin nada de su contenido.
 *
 * Con ella el tramo común se sigue pudiendo recorrer enlace a enlace aunque
 * falten las filas de una empresa que se fue. Solo lectura: la escribe la
 * función {@code borrar_empresa} de la base, y la tabla es de solo inserción.
 */
@Entity(name = "eslabones_retirados")
@Getter
@NoArgsConstructor
public class EslabonRetirado {

    /** El id que tenía la fila en {@code auditoria_fichaje}. */
    @Id
    private long id;

    @Column(name = "hash_anterior", length = 64)
    private String hashAnterior;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "empresa_id", nullable = false)
    private long empresaId;

    @Column(name = "retirado_en", nullable = false)
    private Instant retiradoEn;
}
