package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.config.CondicionesDelServicio;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CompanyClosureResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La baja de una empresa (condiciones, punto 9; V46, ADR 050).
 *
 * El ADMIN la pide escribiendo el nombre de la empresa. Durante treinta días
 * todo sigue funcionando, para que se lleven sus datos (la exportación para la
 * Inspección y los datos de cada persona); se puede cancelar. Pasados, la tarea
 * de las 03:45 la borra entera con la función {@code borrar_empresa} de la
 * base, que es la única que puede tocar la auditoría.
 *
 * Que sean treinta días lo comprueba también la función, no solo esto: la
 * condición que protege el borrado vive donde está el borrado.
 */
@Component
public class BajaDeEmpresa {

    private static final Logger log = LoggerFactory.getLogger(BajaDeEmpresa.class);

    /** El plazo de las condiciones (punto 9). El mismo que exige {@code borrar_empresa}. */
    static final Duration PLAZO = Duration.ofDays(30);

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final CompanyRepository empresas;
    private final UserRepository usuarios;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final ApplicationEventPublisher eventos;
    private final CondicionesDelServicio servicio;

    public BajaDeEmpresa(CompanyRepository empresas, UserRepository usuarios, JdbcTemplate jdbc,
            TransactionTemplate transaccion, ApplicationEventPublisher eventos, CondicionesDelServicio servicio) {
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.eventos = eventos;
        this.servicio = servicio;
    }

    @Transactional(readOnly = true)
    public CompanyClosureResponse estado(User actor) {
        return respuesta(suEmpresa(actor));
    }

    /**
     * @param confirmacion el nombre de la empresa, escrito a mano: es
     *     irreversible a los treinta días, y un botón solo no basta
     */
    @Transactional
    public CompanyClosureResponse solicitar(User actor, String confirmacion) {
        Company empresa = suEmpresa(actor);
        if (empresa.getBajaSolicitadaEn() != null) {
            throw new BusinessException("La baja de tu empresa ya está pedida.");
        }
        if (confirmacion == null || !confirmacion.strip().equalsIgnoreCase(empresa.getNombre().strip())) {
            throw new BusinessException("Escribe el nombre de la empresa tal cual para confirmar la baja.",
                    HttpStatus.BAD_REQUEST);
        }
        empresa.setBajaSolicitadaEn(Instant.now());
        empresa.setBajaSolicitadaPor(actor.getId());
        empresas.saveAndFlush(empresa);
        avisar(empresa, "company-closing", "La baja de tu empresa en " + servicio.producto() + " está pedida");
        log.warn("{} ha pedido la baja de la empresa {}: se borrará a partir del {}.",
                actor.getId(), empresa.getId(), empresa.getBajaSolicitadaEn().plus(PLAZO));
        return respuesta(empresa);
    }

    @Transactional
    public CompanyClosureResponse cancelar(User actor) {
        Company empresa = suEmpresa(actor);
        if (empresa.getBajaSolicitadaEn() == null) {
            throw new BusinessException("Tu empresa no tiene ninguna baja pedida.");
        }
        empresa.setBajaSolicitadaEn(null);
        empresa.setBajaSolicitadaPor(null);
        empresas.saveAndFlush(empresa);
        avisar(empresa, "company-closing-cancelled", "Se ha cancelado la baja de tu empresa en " + servicio.producto());
        log.info("{} ha cancelado la baja de la empresa {}.", actor.getId(), empresa.getId());
        return respuesta(empresa);
    }

    /**
     * Para la tarea de las 03:45: borra las empresas cuya baja tiene más de
     * treinta días. Cada una en su transacción: si una falla (una tabla nueva
     * que la función no conoce, por ejemplo), no se lleva a las demás, y se
     * reintenta la noche siguiente.
     *
     * @return cuántas se han borrado
     */
    public int borrarLasVencidas(Instant ahora) {
        List<Long> vencidas = jdbc.queryForList(
                "SELECT id FROM empresas WHERE baja_solicitada_en < ? ORDER BY id", Long.class,
                Timestamp.from(ahora.minus(PLAZO)));
        int borradas = 0;
        for (Long empresa : vencidas) {
            try {
                Long movimientos = transaccion.execute(estado ->
                        jdbc.queryForObject("SELECT borrar_empresa(?)", Long.class, empresa));
                borradas++;
                log.warn("Empresa {} borrada por su baja: {} movimientos de auditoría con ella.", empresa, movimientos);
            } catch (DataAccessException e) {
                log.error("No se ha podido borrar la empresa {}, de baja: {}", empresa,
                        e.getMostSpecificCause().getMessage());
            }
        }
        return borradas;
    }

    private CompanyClosureResponse respuesta(Company empresa) {
        Instant pedida = empresa.getBajaSolicitadaEn();
        return new CompanyClosureResponse(pedida, pedida == null ? null : pedida.plus(PLAZO));
    }

    private void avisar(Company empresa, String plantilla, String asunto) {
        ZoneId zona = empresa.zona();
        Instant pedida = empresa.getBajaSolicitadaEn();
        for (User admin : usuarios.findByEmpresaAndRol(empresa, Role.ADMIN)) {
            if (!admin.isActivo()) {
                continue;
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("nombre", admin.getNombre());
            variables.put("nombreEmpresa", empresa.getNombre());
            variables.put("producto", servicio.producto());
            variables.put("web", servicio.web());
            variables.put("correoContacto", servicio.correoPrivacidad());
            variables.put("borradoEl", pedida == null ? null : FECHA.format(pedida.plus(PLAZO).atZone(zona)));
            eventos.publishEvent(new NotificationEvents.CorreoDirecto(admin.getEmail(), plantilla, asunto, variables));
        }
    }

    private Company suEmpresa(User actor) {
        return empresas.findById(actor.getEmpresa().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada."));
    }
}
