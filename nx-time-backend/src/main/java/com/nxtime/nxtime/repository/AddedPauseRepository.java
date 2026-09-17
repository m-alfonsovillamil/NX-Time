package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.domain.TimeEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddedPauseRepository extends JpaRepository<AddedPause, Long> {

    List<AddedPause> findByRegistroAndAnuladaFalseOrderByInicioAsc(TimeEntry registro);

    /** Todos, sin paginar: para la exportación de datos personales (RGPD, arts. 15 y 20). */
    List<AddedPause> findByRegistro_UsuarioOrderByInicioAsc(com.nxtime.nxtime.domain.User usuario);
}
