package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.ExternalIdentity;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.SsoProvider;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.LinkedIdentityDTO;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.ExternalIdentityRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.sso.SsoException;
import com.nxtime.nxtime.security.sso.SsoException.Motivo;
import com.nxtime.nxtime.security.sso.VerifiedIdentity;
import com.nxtime.nxtime.service.SsoService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quién entra con una cuenta de fuera, y quién no (ADR 036).
 *
 * <b>Solo entra quien ya tiene cuenta.</b> El SSO no crea usuarios ni empresas:
 * a la gente la da de alta su empresa, y eso no cambia. Lo que cambia es que
 * no tiene que acordarse de otra contraseña.
 *
 * <h2>Cómo se llega de la cuenta de fuera a la persona</h2>
 *
 * <ol>
 *   <li><b>Por el sujeto</b>, si esa cuenta ya se vinculó. Es lo normal desde la
 *       segunda vez, y no depende del correo: quien cambia de correo en Google
 *       sigue entrando, y quien hereda un correo viejo de otro no entra en la
 *       cuenta de ese otro.</li>
 *   <li><b>Por el correo</b>, la primera vez, y solo si el proveedor garantiza
 *       que es suyo ({@code OidcClient} decide cuándo). Entonces se vincula.</li>
 * </ol>
 *
 * <h2>Lo que se le dice a quien no entra</h2>
 *
 * Con el correo garantizado, se le dice la verdad: «no hay cuenta con ese
 * correo». No es enumerar cuentas: ha demostrado que el correo es suyo, y lo
 * que se le cuenta es de su propio correo. Sin esa garantía no se le dice nada
 * del correo, porque podría ser de otro.
 */
@Service
public class SsoServiceImpl implements SsoService {

    private static final Logger log = LoggerFactory.getLogger(SsoServiceImpl.class);

    private final ExternalIdentityRepository identidades;
    private final UserRepository usuarios;
    private final CompanyRepository empresas;
    private final PasswordEncoder passwordEncoder;

    public SsoServiceImpl(ExternalIdentityRepository identidades, UserRepository usuarios,
            CompanyRepository empresas, PasswordEncoder passwordEncoder) {
        this.identidades = identidades;
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User registrarEmpresa(
            SsoProvider proveedor, String sujeto, String correo, String nombreEmpresa, String nombre, String apellidos) {
        // Entre volver del proveedor y mandar el formulario ha podido pasar un
        // cuarto de hora: se vuelve a mirar todo.
        if (usuarios.findByEmail(correo).isPresent()
                || identidades.findByProveedorAndSujeto(proveedor, sujeto).isPresent()) {
            throw new BusinessException(
                    "Ya hay una cuenta de NX Time con esa cuenta de " + proveedor.nombre() + ". Entra con ella.",
                    HttpStatus.CONFLICT);
        }
        if (empresas.findByNombre(nombreEmpresa.strip()).isPresent()) {
            // El mismo texto que el registro con contraseña (AuthServiceImpl).
            throw new BusinessException("La empresa ya existe. Solicita acceso al administrador.");
        }

        Company empresa = empresas.save(Company.builder().nombre(nombreEmpresa.strip()).build());
        User admin = usuarios.save(User.builder()
                .nombre(nombre.strip())
                .apellidos(apellidos.strip())
                .email(correo)
                // Inutilizable, como en las altas (ADR 014): el hash de un
                // valor al azar que no se guarda. Entra con su cuenta del
                // proveedor, y si quiere contraseña la elige con un código.
                .contrasena(passwordEncoder.encode(UUID.randomUUID().toString()))
                .rol(Role.ADMIN)
                .empresa(empresa)
                // Sin correo pendiente de confirmar (V37): que es suyo lo ha
                // garantizado el proveedor, que es lo que el código demostraba.
                .build());
        identidades.save(ExternalIdentity.builder()
                .usuario(admin)
                .proveedor(proveedor)
                .sujeto(sujeto)
                .correo(correo)
                .ultimoAcceso(Instant.now())
                .build());
        log.info("Empresa '{}' registrada con {}; su ADMIN es el usuario {}.",
                empresa.getNombre(), proveedor.id(), admin.getId());
        return admin;
    }

    /*
     * Con el rollback por defecto: si al final no entra (la cuenta está de
     * baja, el correo sigue sin confirmar), la vinculación que se hubiera
     * creado por el camino tampoco se queda.
     */
    @Override
    @Transactional
    public User identificar(VerifiedIdentity identidad) {
        Optional<ExternalIdentity> vinculada =
                identidades.findByProveedorAndSujeto(identidad.proveedor(), identidad.sujeto());

        User usuario;
        if (vinculada.isPresent()) {
            usuario = vinculada.get().getUsuario();
            exigirActiva(usuario, identidad);
            vinculada.get().setUltimoAcceso(Instant.now());
        } else {
            if (!identidad.correoVerificado()) {
                log.info("SSO {}: el proveedor no garantiza el correo; no se busca cuenta por él.",
                        identidad.proveedor().id());
                throw new SsoException(Motivo.CORREO_SIN_VERIFICAR);
            }
            usuario = usuarios.findByEmail(identidad.correo())
                    .orElseThrow(() -> {
                        log.info("SSO {}: correo verificado sin cuenta en NX Time.", identidad.proveedor().id());
                        return new SsoException(Motivo.SIN_CUENTA);
                    });
            exigirActiva(usuario, identidad);
            if (identidades.findByUsuarioAndProveedor(usuario, identidad.proveedor()).isPresent()) {
                // Ya entra con OTRA cuenta de este proveedor. Cambiársela sin
                // preguntar por coincidir el correo sería hacerlo a sus espaldas.
                log.info("SSO {}: el usuario {} ya tiene otra cuenta de este proveedor vinculada.",
                        identidad.proveedor().id(), usuario.getId());
                throw new SsoException(Motivo.YA_TIENE_OTRA);
            }
            identidades.save(ExternalIdentity.builder()
                    .usuario(usuario)
                    .proveedor(identidad.proveedor())
                    .sujeto(identidad.sujeto())
                    .correo(identidad.correo())
                    .ultimoAcceso(Instant.now())
                    .build());
            log.info("SSO {}: vinculada por el correo al usuario {}.", identidad.proveedor().id(), usuario.getId());
        }

        // Quien registró su empresa y aún no había canjeado el código del
        // correo (V37): entrar con una cuenta de ese correo, garantizado por el
        // proveedor, demuestra lo mismo que el código.
        if (usuario.correoPendienteDeConfirmar() && identidad.correoVerificado()
                && usuario.getEmail().equals(identidad.correo())) {
            usuario.setCorreoSinConfirmarDesde(null);
            usuarios.save(usuario);
        }
        if (usuario.correoPendienteDeConfirmar()) {
            throw new SsoException(Motivo.CORREO_SIN_VERIFICAR);
        }

        log.info("SSO {} correcto: {}", identidad.proveedor().id(), usuario.getId());
        return usuario;
    }

    private static void exigirActiva(User usuario, VerifiedIdentity identidad) {
        if (!usuario.isActivo()) {
            log.info("SSO {}: la cuenta {} está dada de baja.", identidad.proveedor().id(), usuario.getId());
            throw new SsoException(Motivo.CUENTA_INACTIVA);
        }
    }

    @Override
    @Transactional
    public void vincular(long usuarioId, VerifiedIdentity identidad) {
        User usuario = usuarios.findById(usuarioId)
                .filter(User::isActivo)
                .orElseThrow(() -> new SsoException(Motivo.SIN_SESION));

        Optional<ExternalIdentity> deQuien =
                identidades.findByProveedorAndSujeto(identidad.proveedor(), identidad.sujeto());
        if (deQuien.isPresent()) {
            if (deQuien.get().getUsuario().getId() == usuario.getId()) {
                return; // Ya era suya: no hay nada que hacer.
            }
            throw new SsoException(Motivo.YA_VINCULADA);
        }
        if (identidades.findByUsuarioAndProveedor(usuario, identidad.proveedor()).isPresent()) {
            throw new SsoException(Motivo.YA_TIENE_OTRA);
        }
        // Aquí no hace falta que el correo esté garantizado, ni que coincida:
        // la persona tiene abierta su sesión de NX Time y acaba de entrar en
        // esa cuenta del proveedor. Controla las dos, que es lo que se pide.
        identidades.save(ExternalIdentity.builder()
                .usuario(usuario)
                .proveedor(identidad.proveedor())
                .sujeto(identidad.sujeto())
                .correo(identidad.correo() == null ? "" : identidad.correo())
                .build());
        log.info("SSO {}: vinculada a mano por el usuario {}.", identidad.proveedor().id(), usuario.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkedIdentityDTO> identidadesDe(User usuario) {
        return identidades.findByUsuarioOrderByProveedorAsc(usuario).stream()
                .map(identidad -> new LinkedIdentityDTO(
                        identidad.getProveedor().id(),
                        identidad.getProveedor().nombre(),
                        identidad.getCorreo(),
                        identidad.getVinculadaEn(),
                        identidad.getUltimoAcceso()))
                .toList();
    }

    @Override
    @Transactional
    public void desvincular(User usuario, SsoProvider proveedor) {
        ExternalIdentity identidad = identidades.findByUsuarioAndProveedor(usuario, proveedor)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No tienes ninguna cuenta de " + proveedor.nombre() + " vinculada."));
        // Siempre se puede: la contraseña sigue ahí, y quien no la recuerde
        // elige otra con un código al correo.
        identidades.delete(identidad);
        log.info("SSO {}: desvinculada por el usuario {}.", proveedor.id(), usuario.getId());
    }
}
