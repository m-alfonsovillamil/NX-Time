package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ComplaintChannelInfoResponse;
import com.nxtime.nxtime.dto.ComplaintChannelSettingsResponse;
import com.nxtime.nxtime.dto.UpdateComplaintChannelRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cómo está montado el canal de denuncias de una empresa (plan J, fase J6; ADR
 * 048): quién es el Responsable del Sistema Interno de Información y cuál es la
 * vía verbal.
 *
 * <b>Designar al Responsable cambia quién instruye</b>: desde ese momento solo
 * él tiene {@code denuncia:instruir}, y los ADMIN dejan de ver el canal (ver
 * {@code RoleAuthorities.de}). Lo decide el ADMIN porque el nombramiento lo
 * hace el órgano de administración (art. 8.1), y la empresa tiene diez días
 * hábiles para comunicarlo a la Autoridad Independiente de Protección del
 * Informante (art. 8.3). Eso último no lo puede hacer la aplicación: se le
 * recuerda en la pantalla.
 */
@Component
public class CanalDeDenuncias {

    private static final Logger log = LoggerFactory.getLogger(CanalDeDenuncias.class);

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CanalDeDenuncias(CompanyRepository companyRepository, UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ComplaintChannelSettingsResponse ajustes(User actor) {
        Company empresa = suEmpresa(actor);
        return new ComplaintChannelSettingsResponse(
                empresa.getResponsableCanalId(),
                nombreDe(empresa.getResponsableCanalId()),
                empresa.getResponsableCanalDesde(),
                empresa.getCanalViaVerbal());
    }

    @Transactional
    public ComplaintChannelSettingsResponse guardar(UpdateComplaintChannelRequest request, User actor) {
        Company empresa = suEmpresa(actor);
        Long nuevo = request.responsableId();
        if (nuevo != null) {
            // Activa, de la empresa y con el correo confirmado: es quien va a
            // leer las denuncias, y una cuenta a medio crear no lee nada.
            userRepository.findById(nuevo)
                    .filter(u -> u.getEmpresa().getId() == empresa.getId())
                    .filter(u -> u.isActivo() && !u.correoPendienteDeConfirmar())
                    .orElseThrow(() -> new BusinessException(
                            "El Responsable tiene que ser una persona activa de tu empresa.", HttpStatus.BAD_REQUEST));
        }
        if (!Objects.equals(nuevo, empresa.getResponsableCanalId())) {
            empresa.setResponsableCanalId(nuevo);
            empresa.setResponsableCanalDesde(nuevo == null ? null : Instant.now());
            log.info("{} ha cambiado el Responsable del canal de denuncias de la empresa {}: ahora {}",
                    actor.getId(), empresa.getId(), nuevo == null ? "los ADMIN" : nuevo);
        }
        String via = request.viaVerbal() == null || request.viaVerbal().isBlank() ? null : request.viaVerbal().trim();
        empresa.setCanalViaVerbal(via);
        companyRepository.saveAndFlush(empresa);
        return ajustes(actor);
    }

    @Transactional(readOnly = true)
    public ComplaintChannelInfoResponse paraQuienDenuncia(User actor) {
        Company empresa = suEmpresa(actor);
        return new ComplaintChannelInfoResponse(nombreDe(empresa.getResponsableCanalId()), empresa.getCanalViaVerbal());
    }

    private String nombreDe(Long usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        return userRepository.findById(usuarioId)
                .map(u -> u.getApellidos() == null || u.getApellidos().isBlank()
                        ? u.getNombre() : u.getNombre() + " " + u.getApellidos())
                .orElse(null);
    }

    private Company suEmpresa(User actor) {
        return companyRepository.findById(actor.getEmpresa().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada."));
    }
}
