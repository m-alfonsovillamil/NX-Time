package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.RefreshToken;
import com.nxtime.nxtime.domain.SsoProvider;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.LinkedIdentityDTO;
import com.nxtime.nxtime.dto.SsoExchangeRequest;
import com.nxtime.nxtime.dto.SsoProviderDTO;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.security.SesionWeb;
import com.nxtime.nxtime.security.sso.AppExchangeCodes;
import com.nxtime.nxtime.security.sso.OidcClient;
import com.nxtime.nxtime.security.sso.SsoConfig;
import com.nxtime.nxtime.security.sso.SsoException;
import com.nxtime.nxtime.security.sso.SsoException.Motivo;
import com.nxtime.nxtime.security.sso.SsoState;
import com.nxtime.nxtime.security.sso.SsoState.Estado;
import com.nxtime.nxtime.security.sso.SsoState.Modo;
import com.nxtime.nxtime.security.sso.VerifiedIdentity;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.service.SsoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrar con Google o con Microsoft (ADR 036).
 *
 * <h2>El recorrido</h2>
 *
 * <ol>
 *   <li><b>{@code /iniciar}</b>: el navegador llega aquí desde el botón. Se le
 *       deja una cookie firmada con lo que hay que recordar ({@link SsoState})
 *       y se le manda al proveedor.</li>
 *   <li><b>{@code /vuelta}</b>: el proveedor lo devuelve con un código. Se
 *       comprueba que la vuelta es de esa ida, se canjea el código, se valida el
 *       ID token ({@link OidcClient}) y se decide quién es ({@link SsoService}).</li>
 *   <li>Y según para qué era la ida:
 *     <ul>
 *       <li><b>la web</b>: se ponen las cookies de la sesión, las mismas que
 *           tras un login, y se vuelve a la web;</li>
 *       <li><b>la app</b>: se vuelve a la app con un código de un solo uso, que
 *           ella canjea en {@code /canjear} ({@link AppExchangeCodes});</li>
 *       <li><b>vincular</b>: se añade esa cuenta a la sesión que ya había.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <b>Los errores no devuelven un JSON</b>, salvo en {@code /canjear} y en lo
 * del perfil: las dos primeras rutas las pide el navegador navegando, y un
 * {@code ProblemDetail} sería una pantalla en blanco con texto. Se vuelve a la
 * web (o a la app) con el motivo en la URL, y es allí donde se explica.
 */
@RestController
@Tag(name = "SSO", description = "Entrar con una cuenta de Google o de Microsoft. Solo para quien ya tiene cuenta.")
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);

    /** Adónde vuelve la app: lo declara en su manifiesto. */
    static final String VUELTA_A_LA_APP = "nxtime://sso";

    /** Un reto de PKCE (S256): 32 bytes en base64url, sin relleno. */
    private static final Pattern RETO = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SsoConfig config;
    private final SsoState estados;
    private final OidcClient oidc;
    private final SsoService sso;
    private final AuthService authService;
    private final SesionWeb sesionWeb;
    private final AppExchangeCodes codigos;
    private final SecureRandom azar = new SecureRandom();

    public SsoController(SsoConfig config, SsoState estados, OidcClient oidc, SsoService sso,
            AuthService authService, SesionWeb sesionWeb, AppExchangeCodes codigos) {
        this.config = config;
        this.estados = estados;
        this.oidc = oidc;
        this.sso = sso;
        this.authService = authService;
        this.sesionWeb = sesionWeb;
        this.codigos = codigos;
    }

    @Operation(summary = "Con qué cuentas de fuera se puede entrar aquí",
            description = "Los proveedores configurados en este servidor. Vacío si no hay ninguno: "
                    + "la web y la app no pintan entonces ningún botón.")
    @ApiResponse(responseCode = "200", description = "Proveedores",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SsoProviderDTO.class))))
    @GetMapping("/auth/sso/proveedores")
    public ResponseEntity<List<SsoProviderDTO>> proveedores() {
        return ResponseEntity.ok(config.activos().stream()
                .map(proveedor -> new SsoProviderDTO(proveedor.id(), proveedor.nombre(), config.urlDeInicio(proveedor)))
                .toList());
    }

    @Operation(summary = "Empezar: manda el navegador al proveedor",
            description = "No es para llamarla con fetch: se navega a ella. Responde siempre con una redirección.")
    @ApiResponse(responseCode = "302", description = "Al proveedor, o de vuelta con el motivo si no se puede empezar")
    @GetMapping("/auth/sso/{proveedor}/iniciar")
    public ResponseEntity<Void> iniciar(
            @PathVariable String proveedor,
            @Parameter(description = "'app' si empieza la app Android; cualquier otra cosa, la web")
            @RequestParam(required = false) String cliente,
            @Parameter(description = "Solo la app: el SHA-256 en base64url de su verificador")
            @RequestParam(required = false) String reto,
            @Parameter(description = "'1' para añadir la cuenta a la sesión que ya hay abierta en la web")
            @RequestParam(required = false) String vincular,
            HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        Modo modo = "app".equals(cliente) ? Modo.APP : "1".equals(vincular) ? Modo.VINCULAR : Modo.WEB;
        try {
            SsoProvider elegido = SsoProvider.deId(proveedor)
                    .filter(config::activo)
                    .orElseThrow(() -> new SsoException(Motivo.NO_DISPONIBLE));

            Long usuarioId = null;
            if (modo == Modo.APP && (reto == null || !RETO.matcher(reto).matches())) {
                throw new SsoException(Motivo.FALLO, "La app ha empezado un SSO sin un reto válido");
            }
            if (modo == Modo.VINCULAR) {
                // Quién vincula lo dice la cookie de la sesión, que es Strict:
                // solo viaja si la navegación sale de la propia web. Otra web
                // que mande aquí a alguien no consigue que vincule nada.
                usuarioId = sesionWeb.refreshDeLaCookie(peticion)
                        .flatMap(authService::usuarioDelRefresh)
                        .orElseThrow(() -> new SsoException(Motivo.SIN_SESION));
            }

            String verificador = aleatorio();
            Estado estado = new Estado(elegido, modo, aleatorio(), aleatorio(), verificador,
                    modo == Modo.APP ? reto : null, usuarioId,
                    Instant.now().plus(SsoState.VIDA).getEpochSecond());
            estados.guardar(respuesta, estado);
            return redirigir(oidc.urlDeAutorizacion(
                    elegido, estado.state(), estado.nonce(), AppExchangeCodes.retoDe(verificador)));
        } catch (SsoException e) {
            return volverConError(modo, e);
        }
    }

    @Operation(summary = "La vuelta del proveedor",
            description = "Adonde el proveedor devuelve el navegador. Es la URL que hay que registrar en su consola.")
    @ApiResponse(responseCode = "302", description = "A la web o a la app, con la sesión hecha o con el motivo")
    @GetMapping("/auth/sso/{proveedor}/vuelta")
    public ResponseEntity<Void> vuelta(
            @PathVariable String proveedor,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        Optional<Estado> guardado = estados.leer(peticion);
        // Se usa una vez: pase lo que pase a partir de aquí, la cookie se va.
        estados.borrar(respuesta);
        Modo modo = guardado.map(Estado::modo).orElse(Modo.WEB);
        try {
            Estado estado = guardado
                    .filter(e -> e.proveedor().id().equals(proveedor))
                    .filter(e -> state != null && MessageDigest.isEqual(
                            e.state().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8)))
                    .orElseThrow(() -> new SsoException(Motivo.FALLO, "La vuelta no corresponde a ninguna ida"));
            if (!config.activo(estado.proveedor())) {
                throw new SsoException(Motivo.NO_DISPONIBLE);
            }
            if (error != null) {
                // «access_denied» es que ha dado a Cancelar. Lo demás, un fallo.
                throw new SsoException("access_denied".equals(error) ? Motivo.CANCELADO : Motivo.FALLO,
                        "El proveedor ha vuelto con un error");
            }
            if (code == null || code.isBlank()) {
                throw new SsoException(Motivo.FALLO, "El proveedor ha vuelto sin código");
            }

            VerifiedIdentity identidad = oidc.canjear(estado.proveedor(), code, estado.verificador(), estado.nonce());

            return switch (estado.modo()) {
                case WEB -> {
                    User usuario = sso.identificar(identidad);
                    AuthenticationResponse sesion = authService.abrirSesion(usuario.getId(), RefreshToken.Origen.WEB);
                    // Las mismas cookies que tras un login con contraseña. La web
                    // arranca, ve que hay sesión que recuperar y pide su access token.
                    sesionWeb.emitir(respuesta, sesion.refreshToken());
                    yield redirigir(config.urlWeb() + "/");
                }
                case APP -> {
                    User usuario = sso.identificar(identidad);
                    yield redirigir(VUELTA_A_LA_APP + "?codigo=" + codigos.emitir(usuario.getId(), estado.retoDeLaApp()));
                }
                case VINCULAR -> {
                    sso.vincular(estado.usuarioId(), identidad);
                    yield redirigir(config.urlWeb() + "/ajustes?sso=vinculada");
                }
            };
        } catch (SsoException e) {
            return volverConError(modo, e);
        }
    }

    @Operation(summary = "La app recoge su sesión",
            description = "Canjea el código con el que el navegador volvió a la app. Vale una vez y un minuto, "
                    + "y solo a quien presente el verificador con el que se empezó.")
    @ApiResponse(responseCode = "200", description = "La sesión, como la de un login",
            content = @Content(schema = @Schema(implementation = AuthenticationResponse.class)))
    @ApiResponse(responseCode = "400", description = "El código no vale, ha caducado o ya se usó",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/auth/sso/canjear")
    public ResponseEntity<AuthenticationResponse> canjear(@Valid @RequestBody SsoExchangeRequest request) {
        long usuarioId = codigos.canjear(request.codigo(), request.verificador())
                .orElseThrow(() -> new BusinessException(
                        "No se ha podido completar el acceso. Vuelve a intentarlo.", HttpStatus.BAD_REQUEST));
        return ResponseEntity.ok(authService.abrirSesion(usuarioId, RefreshToken.Origen.ANDROID));
    }

    @Operation(summary = "Mis cuentas vinculadas", description = "Las cuentas de fuera con las que entro.")
    @ApiResponse(responseCode = "200", description = "Cuentas",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LinkedIdentityDTO.class))))
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/v1/perfil/identidades")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LinkedIdentityDTO>> misIdentidades(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(sso.identidadesDe(usuario.getUser()));
    }

    @Operation(summary = "Desvincular una cuenta",
            description = "Dejo de poder entrar con ella. La contraseña sigue valiendo.")
    @ApiResponse(responseCode = "204", description = "Desvinculada")
    @ApiResponse(responseCode = "404", description = "No tenía ninguna de ese proveedor",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/v1/perfil/identidades/{proveedor}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> desvincular(
            @PathVariable String proveedor, @AuthenticationPrincipal SecurityUser usuario) {
        SsoProvider elegido = SsoProvider.deId(proveedor)
                .orElseThrow(() -> new BusinessException("Ese proveedor no existe.", HttpStatus.NOT_FOUND));
        sso.desvincular(usuario.getUser(), elegido);
        return ResponseEntity.noContent().build();
    }

    /**
     * De vuelta a quien empezó, con el motivo. Lo que no es culpa de la persona
     * se apunta aquí con su detalle; a ella solo le llega el motivo.
     */
    private ResponseEntity<Void> volverConError(Modo modo, SsoException e) {
        if (e.motivo() == Motivo.FALLO) {
            log.warn("SSO fallido: {}", e.getMessage());
        }
        String motivo = e.motivo().id();
        // Sin URL de la web no hay adónde volver: es que el SSO no está montado.
        String web = config.urlWeb().isEmpty() ? "/" : config.urlWeb() + "/";
        return redirigir(switch (modo) {
            case APP -> VUELTA_A_LA_APP + "?error=" + motivo;
            case VINCULAR -> web + "ajustes?sso=" + motivo;
            case WEB -> web + "?sso=" + motivo;
        });
    }

    private static ResponseEntity<Void> redirigir(String destino) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(destino))
                // Lleva un estado o un código de un solo uso: que nadie lo guarde.
                .cacheControl(CacheControl.noStore())
                .build();
    }

    /** 256 bits en base64url: para el state, el nonce y el verificador de PKCE. */
    private String aleatorio() {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
