package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.config.CondicionesDelServicio;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CompanyStatus;
import com.nxtime.nxtime.domain.PlatformAction;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CompanyTermsResponse;
import com.nxtime.nxtime.dto.PlatformActionDTO;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.PlatformActionRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.OperadoresDePlataforma;
import com.nxtime.nxtime.service.CompanyLifecycleService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link CompanyLifecycleService} y el ADR 046.
 *
 * Cada correo sale después del commit (los eventos son AFTER_COMMIT): si la
 * acción no llega a guardarse, no se avisa de nada.
 */
@Service
@Transactional
public class CompanyLifecycleServiceImpl implements CompanyLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CompanyLifecycleServiceImpl.class);

    private final CompanyRepository empresas;
    private final UserRepository usuarios;
    private final RefreshTokenRepository sesiones;
    private final PlatformActionRepository acciones;
    private final UnconfirmedRegistrationCleaner borrado;
    private final OperadoresDePlataforma operadores;
    private final CondicionesDelServicio servicio;
    private final ApplicationEventPublisher eventos;

    public CompanyLifecycleServiceImpl(CompanyRepository empresas, UserRepository usuarios,
            RefreshTokenRepository sesiones, PlatformActionRepository acciones, UnconfirmedRegistrationCleaner borrado,
            OperadoresDePlataforma operadores, CondicionesDelServicio servicio, ApplicationEventPublisher eventos) {
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.acciones = acciones;
        this.borrado = borrado;
        this.operadores = operadores;
        this.servicio = servicio;
        this.eventos = eventos;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.SUPPORTS)
    public BusinessException noDisponible(Company empresa) {
        String texto = empresa.getEstado() == CompanyStatus.PENDIENTE
                ? "Tu empresa está pendiente de aprobación. Te avisaremos por correo en cuanto puedas entrar."
                : "El acceso de tu empresa a " + servicio.producto() + " está suspendido. Si crees que es un error, "
                        + "escribe a " + servicio.correoPrivacidad() + ".";
        return new BusinessException(texto, HttpStatus.LOCKED);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.SUPPORTS)
    public void exigirActiva(Company empresa) {
        if (empresa != null && !empresa.estaActiva()) {
            throw noDisponible(empresa);
        }
    }

    @Override
    public void avisarDeQueEspera(Company empresa, User admin) {
        if (operadores.correos().isEmpty()) {
            // Sin operadores no la aprobará nadie. No es un error de quien se
            // registra: es de configuración, y tiene que verse.
            log.error("La empresa {} espera aprobación y no hay operadores (PLATAFORMA_OPERADORES vacía).",
                    empresa.getId());
            return;
        }
        Map<String, Object> variables = variables(empresa, null);
        variables.put("adminNombre", (admin.getNombre() + " " + (admin.getApellidos() == null ? "" : admin.getApellidos())).strip());
        variables.put("adminEmail", admin.getEmail());
        variables.put("enlace", servicio.web() + "/plataforma/empresas/" + empresa.getId());
        for (String correo : operadores.correos()) {
            eventos.publishEvent(new NotificationEvents.CorreoDirecto(correo, "company-awaiting-approval",
                    "Empresa nueva esperando aprobación: " + empresa.getNombre(), variables));
        }
        log.info("Empresa {} esperando aprobación: avisados {} operador(es).", empresa.getId(), operadores.correos().size());
    }

    @Override
    public void aprobar(long empresaId, User operador) {
        Company empresa = buscar(empresaId);
        exigirEstado(empresa, CompanyStatus.PENDIENTE, "Solo se aprueba una empresa pendiente.");
        empresa.setEstado(CompanyStatus.ACTIVA);
        empresa.setAprobadaEn(Instant.now());
        empresas.save(empresa);
        anotar(empresa, operador, PlatformAction.Tipo.APROBAR, null);
        avisarALosAdmin(empresa, "company-approved", "Tu empresa ya está dada de alta en " + servicio.producto(), null);
    }

    @Override
    public void rechazar(long empresaId, User operador, String motivo) {
        Company empresa = buscar(empresaId);
        exigirEstado(empresa, CompanyStatus.PENDIENTE,
                "Solo se rechaza una empresa pendiente. A una que ya funcionaba se la suspende.");
        // Antes de borrarla: después no queda a quién escribir.
        avisarALosAdmin(empresa, "company-rejected", "No hemos podido dar de alta tu empresa en " + servicio.producto(),
                motivo);
        anotar(empresa, operador, PlatformAction.Tipo.RECHAZAR, motivo);
        try {
            borrado.borrarEmpresaRechazada(empresaId);
        } catch (DataIntegrityViolationException e) {
            // Tiene algo que una empresa que nadie ha usado no debería tener.
            throw new BusinessException("Esta empresa tiene datos y no se puede borrar. Suspéndela.", HttpStatus.CONFLICT);
        }
    }

    @Override
    public void suspender(long empresaId, User operador, String motivo) {
        Company empresa = buscar(empresaId);
        exigirEstado(empresa, CompanyStatus.ACTIVA, "Solo se suspende una empresa activa.");
        if (operador.getEmpresa() != null && operador.getEmpresa().getId() == empresaId) {
            // Se quedaría fuera del panel con el que se reactiva.
            throw new BusinessException("No puedes suspender tu propia empresa.", HttpStatus.CONFLICT);
        }
        empresa.setEstado(CompanyStatus.SUSPENDIDA);
        empresas.save(empresa);
        int cerradas = sesiones.revocarLasDeLaEmpresa(empresaId, Instant.now());
        anotar(empresa, operador, PlatformAction.Tipo.SUSPENDER, motivo);
        avisarALosAdmin(empresa, "company-suspended", "El acceso de tu empresa a " + servicio.producto()
                + " está suspendido", motivo);
        log.warn("Empresa {} suspendida por el operador {} ({} sesiones cerradas).", empresaId, operador.getId(), cerradas);
    }

    @Override
    public void reactivar(long empresaId, User operador) {
        Company empresa = buscar(empresaId);
        exigirEstado(empresa, CompanyStatus.SUSPENDIDA, "Solo se reactiva una empresa suspendida.");
        empresa.setEstado(CompanyStatus.ACTIVA);
        empresas.save(empresa);
        anotar(empresa, operador, PlatformAction.Tipo.REACTIVAR, null);
        avisarALosAdmin(empresa, "company-reactivated", "Tu empresa vuelve a tener acceso a " + servicio.producto(), null);
    }

    @Override
    public void registrarConsulta(Company empresa, User operador) {
        anotar(empresa, operador, PlatformAction.Tipo.CONSULTAR, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformActionDTO> historial(long empresaId) {
        return acciones.findTop50ByEmpresaIdOrderByEnDescIdDesc(empresaId).stream().map(PlatformActionDTO::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyTermsResponse condiciones(User admin) {
        return condicionesDe(buscar(admin.getEmpresa().getId()));
    }

    @Override
    public CompanyTermsResponse aceptarCondiciones(User admin) {
        Company empresa = buscar(admin.getEmpresa().getId());
        empresa.aceptarCondiciones(servicio.version(), admin, Instant.now());
        empresas.save(empresa);
        log.info("Empresa {}: condiciones {} aceptadas por el usuario {}.",
                empresa.getId(), servicio.version(), admin.getId());
        return condicionesDe(empresa);
    }

    private CompanyTermsResponse condicionesDe(Company empresa) {
        String quien = empresa.getCondicionesAceptadasPor() == null ? null
                : usuarios.findById(empresa.getCondicionesAceptadasPor())
                        .map(u -> (u.getNombre() + " " + (u.getApellidos() == null ? "" : u.getApellidos())).strip())
                        .orElse(null);
        return new CompanyTermsResponse(servicio.version(), empresa.getCondicionesVersion(),
                empresa.getCondicionesAceptadasEn(), quien, !servicio.version().equals(empresa.getCondicionesVersion()));
    }

    // ------------------------------------------------------------------

    private Company buscar(long empresaId) {
        return empresas.findById(empresaId).orElseThrow(() -> new ResourceNotFoundException("Esa empresa no existe."));
    }

    private static void exigirEstado(Company empresa, CompanyStatus esperado, String siNo) {
        if (empresa.getEstado() != esperado) {
            throw new BusinessException(siNo, HttpStatus.CONFLICT);
        }
    }

    private void anotar(Company empresa, User operador, PlatformAction.Tipo tipo, String motivo) {
        acciones.save(PlatformAction.builder()
                .operadorId(operador.getId())
                .operadorEmail(operador.getEmail())
                .empresaId(empresa.getId())
                .empresaNombre(empresa.getNombre())
                .accion(tipo)
                .motivo(motivo == null || motivo.isBlank() ? null : motivo.strip())
                .build());
        log.info("Panel de plataforma: {} sobre la empresa {} por el usuario {}.", tipo, empresa.getId(), operador.getId());
    }

    private void avisarALosAdmin(Company empresa, String plantilla, String asunto, String motivo) {
        for (User admin : usuarios.findByEmpresaAndRol(empresa, Role.ADMIN)) {
            if (!admin.isActivo()) {
                continue;
            }
            Map<String, Object> variables = variables(empresa, motivo);
            variables.put("nombre", admin.getNombre());
            eventos.publishEvent(new NotificationEvents.CorreoDirecto(admin.getEmail(), plantilla, asunto, variables));
        }
    }

    private Map<String, Object> variables(Company empresa, String motivo) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nombreEmpresa", empresa.getNombre());
        variables.put("producto", servicio.producto());
        variables.put("web", servicio.web());
        variables.put("correoContacto", servicio.correoPrivacidad());
        variables.put("motivo", motivo == null || motivo.isBlank() ? null : motivo.strip());
        return variables;
    }
}
