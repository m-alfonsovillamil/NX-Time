package com.nxtime.nxtime.support;

import com.nxtime.nxtime.dto.PaginaDTO;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Páginas de un solo trozo para los tests que no van de paginación (Fase A7). */
public final class Paginas {

    private Paginas() {
    }

    public static <T> PaginaDTO<T> una(List<T> contenido) {
        return new PaginaDTO<>(contenido, 0, 50, contenido.size(), contenido.isEmpty() ? 0 : 1, false);
    }

    public static <T> Page<T> page(List<T> contenido) {
        return new PageImpl<>(contenido, PageRequest.of(0, 50), contenido.size());
    }
}
