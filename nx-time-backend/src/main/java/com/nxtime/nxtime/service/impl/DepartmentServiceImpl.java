package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DepartmentRequest;
import com.nxtime.nxtime.dto.DepartmentResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.DepartmentService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentServiceImpl.class);

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository, UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<DepartmentResponse> listar(User actor) {
        return departmentRepository.findByEmpresaOrderByNombreAsc(actor.getEmpresa()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DepartmentResponse crear(DepartmentRequest request, User actor) {
        String nombre = request.nombre().trim();
        // Se comprueba aquí para dar un 409 con un mensaje que se
        // entienda; el UNIQUE de la base sigue estando y es quien lo
        // garantiza de verdad si dos altas llegan a la vez.
        if (departmentRepository.existsByEmpresaAndNombreIgnoreCase(actor.getEmpresa(), nombre)) {
            throw new BusinessException("Ya existe un departamento con ese nombre.");
        }

        Department departamento = departmentRepository.save(Department.builder()
                .empresa(actor.getEmpresa())
                .nombre(nombre)
                .build());

        log.info("{} ha creado el departamento '{}'", actor.getEmail(), nombre);
        return toResponse(departamento);
    }

    @Override
    @Transactional
    public DepartmentResponse renombrar(long id, DepartmentRequest request, User actor) {
        Department departamento = deLaMismaEmpresa(id, actor);
        String nombre = request.nombre().trim();

        // Renombrar a lo que ya se llama no es un conflicto, es no hacer
        // nada: sin este "no es él mismo", cambiar solo las mayúsculas
        // de un nombre daría 409.
        if (!departamento.getNombre().equalsIgnoreCase(nombre)
                && departmentRepository.existsByEmpresaAndNombreIgnoreCase(actor.getEmpresa(), nombre)) {
            throw new BusinessException("Ya existe un departamento con ese nombre.");
        }

        departamento.setNombre(nombre);
        departmentRepository.save(departamento);
        return toResponse(departamento);
    }

    @Override
    @Transactional
    public void borrar(long id, User actor) {
        Department departamento = deLaMismaEmpresa(id, actor);

        long empleados = userRepository.countByDepartamento_Id(id);
        if (empleados > 0) {
            // Se comprueba antes de borrar para poder decir CUÁNTOS hay.
            // Si no, lo que salta es la violación de fk_usuarios_departamento,
            // que llega al cliente como un 500 sin explicación.
            throw new BusinessException(
                    "No se puede borrar: todavía hay " + empleados + " empleado(s) en este departamento.");
        }

        departmentRepository.delete(departamento);
        log.info("{} ha borrado el departamento '{}'", actor.getEmail(), departamento.getNombre());
    }

    private Department deLaMismaEmpresa(long id, User actor) {
        Department departamento = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Departamento no encontrado."));
        if (departamento.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes gestionar departamentos de otra empresa.");
        }
        return departamento;
    }

    private DepartmentResponse toResponse(Department departamento) {
        return new DepartmentResponse(
                departamento.getId(),
                departamento.getNombre(),
                userRepository.countByDepartamento_Id(departamento.getId()));
    }
}
