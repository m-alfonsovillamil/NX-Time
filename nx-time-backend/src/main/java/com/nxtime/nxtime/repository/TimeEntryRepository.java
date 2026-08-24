package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    Optional<TimeEntry> findByUsuarioAndHoraSalidaIsNull(User usuario);

    List<TimeEntry> findByUsuarioOrderByHoraEntradaDesc(User usuario);

    List<TimeEntry> findByUsuarioInOrderByHoraEntradaDesc(List<User> usuarios);
}
