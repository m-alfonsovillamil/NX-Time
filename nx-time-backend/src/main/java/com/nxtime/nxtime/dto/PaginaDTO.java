package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Una página de una lista (Fase A7, ADR 027).
 *
 * <b>No se expone el {@code Page} de Spring</b>, por tres motivos: su JSON
 * trae una docena de campos internos ({@code pageable}, {@code sort},
 * {@code first}...) que ningún cliente necesita; ese formato no es estable
 * entre versiones de Spring Data, que ya avisa de ello al serializarlo; y
 * springdoc lo documenta mal, así que el tipo que genera la web no serviría.
 * Seis campos con nombre propio son un contrato que controlamos.
 *
 * @param pagina empezando en 0.
 * @param hayMas si pedir la página siguiente devolvería algo. Es lo único que
 *   mira un scroll infinito; los totales son para quien pinte "página 3 de 8".
 */
public record PaginaDTO<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        @Schema(description = "Cuántos elementos hay en total, en todas las páginas")
        long totalElementos,
        int totalPaginas,
        boolean hayMas
) {

    public static <E, T> PaginaDTO<T> de(Page<E> pagina, Function<E, T> convertir) {
        return new PaginaDTO<>(
                pagina.getContent().stream().map(convertir).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.hasNext());
    }
}
