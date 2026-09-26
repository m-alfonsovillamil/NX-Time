package com.nxtime.nxtime.service;

import com.nxtime.nxtime.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

/**
 * Qué página se puede pedir (Fase A7, ADR 027). Un solo sitio para los
 * límites, que valen igual para todas las listas paginadas.
 *
 * El tope de 200 se queda en el servidor aunque el cliente pida más: es lo
 * que ya servían las dos listas acotadas de antes, y lo que impide que
 * {@code ?tamano=1000000} devuelva la tabla entera, que es justo lo que la
 * paginación existe para evitar. Se responde 400 en vez de recortar en
 * silencio: un cliente que pide 500 y recibe 200 sin saberlo creería que no
 * hay más.
 */
public final class Paginacion {

    public static final int TAMANO_POR_DEFECTO = 50;
    public static final int TAMANO_MAXIMO = 200;

    private Paginacion() {
    }

    public static Pageable pedir(int pagina, int tamano) {
        if (pagina < 0) {
            throw new BusinessException("La página empieza en 0.", HttpStatus.BAD_REQUEST);
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new BusinessException(
                    "El tamaño de página va de 1 a " + TAMANO_MAXIMO + ".", HttpStatus.BAD_REQUEST);
        }
        // Sin orden aquí: cada consulta lleva el suyo en el JPQL, con un
        // desempate por id para que ninguna fila salte de una página a otra.
        return PageRequest.of(pagina, tamano);
    }
}
