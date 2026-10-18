package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PrivacyNoticeResponse;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * El aviso de privacidad de la primera entrada (plan J, fase J5; ADR 047).
 *
 * Informar a la plantilla es cosa de la empresa, que es la responsable del
 * tratamiento (RGPD, art. 13). NX Time no puede hacerlo por ella, pero sí
 * decirle a cada persona, una vez, quién es quién y a quién dirigirse: si no,
 * lo natural es escribir al que hace la aplicación, que no puede atender sus
 * derechos sin la empresa.
 */
@Component
public class AvisoDePrivacidad {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    public AvisoDePrivacidad(UserRepository userRepository, CompanyRepository companyRepository) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public PrivacyNoticeResponse leer(User actor) {
        Company empresa = companyRepository.findById(actor.getEmpresa().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada."));
        boolean visto = userRepository.avisoDePrivacidadVistoEn(actor.getId()).isPresent();
        return new PrivacyNoticeResponse(empresa.getNombre(), empresa.getContactoPrivacidad(), visto);
    }

    @Transactional
    public void marcarVisto(User actor) {
        userRepository.marcarAvisoDePrivacidadVisto(actor.getId(), Instant.now());
    }
}
