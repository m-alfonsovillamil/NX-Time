package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.config.CacheConfig;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CompanySettingsResponse;
import com.nxtime.nxtime.dto.UpdateCompanySettingsRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.service.CompanySettingsService;
import com.nxtime.nxtime.service.MonthlySignatureService;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link CompanySettingsService}.
 *
 * La empresa se vuelve a leer dentro de la transacción: la que trae quien
 * pregunta viene del filtro de seguridad, fuera de ella, y guardar sobre esa
 * copia se saltaría el {@code @Version}.
 */
@Service
@Transactional(readOnly = true)
public class CompanySettingsServiceImpl implements CompanySettingsService {

    private static final Logger log = LoggerFactory.getLogger(CompanySettingsServiceImpl.class);

    private final CompanyRepository companyRepository;
    private final MonthlySignatureService signatureService;

    public CompanySettingsServiceImpl(CompanyRepository companyRepository, MonthlySignatureService signatureService) {
        this.companyRepository = companyRepository;
        this.signatureService = signatureService;
    }

    @Override
    public CompanySettingsResponse leer(User actor) {
        Company empresa = suEmpresa(actor);
        return new CompanySettingsResponse(empresa.getNombre(), empresa.getZonaHoraria(),
                empresa.getContactoPrivacidad(), empresa.isExigirSegundoFactor(), 0);
    }

    /**
     * Al cambiar la zona cambian los días de TODO el histórico, así que se
     * vacían las cachés que guardan cuentas por día: el panel dura un minuto,
     * pero la analítica, media hora.
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.ANALITICA, allEntries = true)
    })
    public CompanySettingsResponse guardar(UpdateCompanySettingsRequest request, User actor) {
        Company empresa = suEmpresa(actor);

        String nombre = request.nombre().trim();
        companyRepository.findByNombre(nombre)
                .filter(otra -> otra.getId() != empresa.getId())
                .ifPresent(otra -> {
                    throw new BusinessException("Ya hay otra empresa con ese nombre.");
                });

        String zona = zonaValida(request.zonaHoraria().trim());
        boolean cambiaLaZona = !zona.equals(empresa.getZonaHoraria());

        empresa.setNombre(nombre);
        empresa.setZonaHoraria(zona);
        empresa.setContactoPrivacidad(contactoValido(request.contactoPrivacidad()));
        if (request.exigirSegundoFactor() != null) {
            exigirSegundoFactor(empresa, request.exigirSegundoFactor(), actor);
        }
        companyRepository.saveAndFlush(empresa);

        int invalidadas = 0;
        if (cambiaLaZona) {
            // Con la zona nueva ya puesta: la huella de cada mes se recalcula
            // con los días contados en ella.
            invalidadas = signatureService.revisarTrasCambioDeZona(empresa.getId(),
                    "La empresa cambió su zona horaria a " + zona + ".");
            log.info("{} cambió la zona de la empresa {} a {}: {} firmas invalidadas.",
                    actor.getId(), empresa.getId(), zona, invalidadas);
        }
        return new CompanySettingsResponse(empresa.getNombre(), empresa.getZonaHoraria(),
                empresa.getContactoPrivacidad(), empresa.isExigirSegundoFactor(), invalidadas);
    }

    /**
     * Exigir el segundo factor a los ADMIN y RRHH (ADR 051). Para exigirlo,
     * quien lo hace tiene que tenerlo ya: si no, en la petición siguiente su
     * propia sesión quedaría limitada a activarlo, sin haber entendido por qué.
     */
    private void exigirSegundoFactor(Company empresa, boolean exigir, User actor) {
        if (exigir && !empresa.isExigirSegundoFactor() && !actor.tieneSegundoFactor()) {
            throw new BusinessException("Para exigir el segundo factor, actívalo antes tú en Ajustes → Segundo factor.",
                    HttpStatus.CONFLICT);
        }
        if (exigir != empresa.isExigirSegundoFactor()) {
            log.info("{} {} el segundo factor a los ADMIN y RRHH de la empresa {}.",
                    actor.getId(), exigir ? "exige" : "deja de exigir", empresa.getId());
        }
        empresa.setExigirSegundoFactor(exigir);
    }

    private Company suEmpresa(User actor) {
        return companyRepository.findById(actor.getEmpresa().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada."));
    }

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * Un correo o un enlace https, o nada (V44, ADR 047). Es lo que se le
     * enseña a cada persona de la plantilla en el aviso de privacidad, así
     * que tiene que ser algo a lo que se pueda escribir o que se pueda abrir:
     * un {@code javascript:} o un {@code http://} no pasan.
     */
    static String contactoValido(String contacto) {
        if (contacto == null || contacto.isBlank()) {
            return null;
        }
        String limpio = contacto.trim();
        if (CORREO.matcher(limpio).matches()) {
            return limpio;
        }
        try {
            URI enlace = new URI(limpio);
            if ("https".equalsIgnoreCase(enlace.getScheme()) && enlace.getHost() != null) {
                return limpio;
            }
        } catch (URISyntaxException e) {
            // Cae al error de abajo.
        }
        throw new BusinessException(
                "El contacto de privacidad tiene que ser un correo o un enlace que empiece por https://.",
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Una zona que Java conozca por su nombre de región. Los desfases fijos
     * ("+01:00", "UTC+1") se rechazan aunque {@code ZoneId.of} los acepte: no
     * saben del horario de verano, y una empresa de Madrid con "+01:00" vería
     * medio año desplazado una hora.
     */
    private static String zonaValida(String zona) {
        if (!ZoneId.getAvailableZoneIds().contains(zona)) {
            throw new BusinessException("Esa zona horaria no existe: elige una de la lista.", HttpStatus.BAD_REQUEST);
        }
        return zona;
    }
}
