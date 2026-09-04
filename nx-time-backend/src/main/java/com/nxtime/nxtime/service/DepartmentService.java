package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DepartmentRequest;
import com.nxtime.nxtime.dto.DepartmentResponse;
import java.util.List;

/** Departamentos de una empresa (Fase B). */
public interface DepartmentService {

    /** Los de mi empresa, por nombre, con cuánta gente tiene cada uno. */
    List<DepartmentResponse> listar(User actor);

    DepartmentResponse crear(DepartmentRequest request, User actor);

    DepartmentResponse renombrar(long id, DepartmentRequest request, User actor);

    /**
     * @throws com.nxtime.nxtime.exception.BusinessException 409 si todavía tiene gente dentro
     */
    void borrar(long id, User actor);
}
