package com.nxtime.nxtime.security;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * A quién se le exige entrar con segundo factor (ADR 051).
 *
 * <ul>
 *   <li><b>Al operador de plataforma</b>: ve y gestiona todas las empresas.</li>
 *   <li><b>Al Responsable del canal de denuncias</b> de su empresa: lo
 *       recomienda la Autoridad Independiente de Protección del Informante
 *       (Recomendación 1/2026), y lo que instruye es lo más delicado que hay
 *       en la aplicación.</li>
 *   <li><b>A los ADMIN y RRHH</b> de una empresa que lo exija (lo decide un
 *       ADMIN en los ajustes).</li>
 * </ul>
 *
 * A los demás, nunca: es opcional para todos.
 *
 * Entrar con Google o Microsoft cuenta como segundo factor (lo pone el
 * proveedor), así que a quien entra así no se le pide nada más.
 */
@Component
public class SegundoFactorObligatorio {

    /** Por qué se le exige, para decírselo en la pantalla de activarlo. */
    public enum Motivo {
        OPERADOR_DE_PLATAFORMA("Eres operador de la plataforma"),
        RESPONSABLE_DEL_CANAL("Eres el Responsable del canal de denuncias de tu empresa"),
        EXIGIDO_POR_LA_EMPRESA("Tu empresa lo exige a los administradores y a RRHH");

        private final String texto;

        Motivo(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }
    }

    private final OperadoresDePlataforma operadores;

    public SegundoFactorObligatorio(OperadoresDePlataforma operadores) {
        this.operadores = operadores;
    }

    /** Por qué se le exige a esta persona, o vacío si no se le exige. */
    public Optional<Motivo> motivo(User usuario) {
        if (operadores.es(usuario)) {
            return Optional.of(Motivo.OPERADOR_DE_PLATAFORMA);
        }
        var empresa = usuario.getEmpresa();
        if (empresa == null) {
            return Optional.empty();
        }
        Long responsable = empresa.getResponsableCanalId();
        if (responsable != null && responsable == usuario.getId()) {
            return Optional.of(Motivo.RESPONSABLE_DEL_CANAL);
        }
        if (empresa.isExigirSegundoFactor() && (usuario.getRol() == Role.ADMIN || usuario.getRol() == Role.RRHH)) {
            return Optional.of(Motivo.EXIGIDO_POR_LA_EMPRESA);
        }
        return Optional.empty();
    }

    public boolean exigido(User usuario) {
        return motivo(usuario).isPresent();
    }
}
