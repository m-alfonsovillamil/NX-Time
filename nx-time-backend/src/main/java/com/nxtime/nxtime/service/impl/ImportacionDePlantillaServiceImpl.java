package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.CodigosDeEmpleado;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Emails;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import com.nxtime.nxtime.dto.FilaDeImportacion;
import com.nxtime.nxtime.dto.ImportacionPlantillaResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import com.nxtime.nxtime.service.ImportacionDePlantillaService;
import com.nxtime.nxtime.service.InvitacionesService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link ImportacionDePlantillaService} y el ADR 052.
 *
 * <b>Las mismas reglas que dar de alta a mano</b>, fila a fila, y con los
 * permisos de quien sube el fichero:
 * <ul>
 *   <li>un EMPLEADO lo da de alta quien tiene {@code empleado:crear}; un
 *       GESTOR, RRHH o ADMIN, solo quien tiene {@code gestor:crear} (ADMIN);</li>
 *   <li>el departamento, solo con {@code departamento:gestionar}, y tiene que
 *       existir ya en la empresa;</li>
 *   <li>la jornada, las vacaciones y el código de empleado, solo con
 *       {@code empleado:configurar}, y con los mismos límites que la ficha
 *       (0,1-60 h, 0-365 días; el código, {@link CodigosDeEmpleado}, sin
 *       repetirse en la empresa ni en el fichero);</li>
 *   <li>el correo no puede tener cuenta ya, en ninguna empresa, ni repetirse en
 *       el fichero.</li>
 * </ul>
 *
 * <b>O todas o ninguna:</b> con una fila mala no se crea nada, para no dejar
 * media plantilla dentro y tener que adivinar cuál falta.
 */
@Service
@Transactional(readOnly = true)
public class ImportacionDePlantillaServiceImpl implements ImportacionDePlantillaService {

    private static final Logger log = LoggerFactory.getLogger(ImportacionDePlantillaServiceImpl.class);

    /** Lo mismo que pide {@code @Email} en el alta a mano, sin depender de un validador. */
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Map<String, Role> ROLES = Map.ofEntries(
            Map.entry("empleado", Role.EMPLEADO), Map.entry("empleada", Role.EMPLEADO),
            Map.entry("gestor", Role.GESTOR), Map.entry("gestora", Role.GESTOR),
            Map.entry("rrhh", Role.RRHH), Map.entry("recursos humanos", Role.RRHH),
            Map.entry("admin", Role.ADMIN), Map.entry("administrador", Role.ADMIN),
            Map.entry("administradora", Role.ADMIN));

    private final UserRepository usuarios;
    private final DepartmentRepository departamentos;
    private final VacationBalanceRepository saldos;
    private final PasswordEncoder passwordEncoder;
    private final InvitacionesService invitaciones;
    private final ApplicationEventPublisher eventos;

    public ImportacionDePlantillaServiceImpl(
            UserRepository usuarios,
            DepartmentRepository departamentos,
            VacationBalanceRepository saldos,
            PasswordEncoder passwordEncoder,
            InvitacionesService invitaciones,
            ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.departamentos = departamentos;
        this.saldos = saldos;
        this.passwordEncoder = passwordEncoder;
        this.invitaciones = invitaciones;
        this.eventos = eventos;
    }

    /** Una fila ya comprobada: lo que se enseña y, si vale, con qué crearla. */
    private record Comprobada(FilaDeImportacion fila, Department departamento) {
    }

    @Override
    public ImportacionPlantillaResponse simular(String nombreDelFichero, byte[] contenido, User actor) {
        LectorDePlantilla.Leido leido = LectorDePlantilla.leer(nombreDelFichero, contenido);
        List<Comprobada> filas = comprobar(leido, actor);
        return respuesta(true, leido, filas, 0);
    }

    @Override
    @Transactional
    public ImportacionPlantillaResponse importar(String nombreDelFichero, byte[] contenido, User actor) {
        LectorDePlantilla.Leido leido = LectorDePlantilla.leer(nombreDelFichero, contenido);
        List<Comprobada> filas = comprobar(leido, actor);
        long conErrores = filas.stream().filter(f -> !f.fila().errores().isEmpty()).count();
        if (conErrores > 0) {
            throw new BusinessException("Hay " + conErrores + (conErrores == 1 ? " fila" : " filas")
                    + " con errores y no se ha creado a nadie. Corrígelas y vuelve a probar.", HttpStatus.BAD_REQUEST);
        }

        Company empresa = actor.getEmpresa();
        int anio = LocalDate.now(actor.zona()).getYear();
        // Una sola contraseña inutilizable para todas: el hash de 122 bits de
        // azar que no se guardan. Nadie la conoce, igual que la de un alta a
        // mano, y así no son cien BCrypt seguidos con la CPU del plan gratuito.
        String inutilizable = passwordEncoder.encode(UUID.randomUUID().toString());

        List<Long> creados = new ArrayList<>();
        for (Comprobada comprobada : filas) {
            FilaDeImportacion fila = comprobada.fila();
            User.UserBuilder nuevo = User.builder()
                    .nombre(fila.nombre())
                    .apellidos(fila.apellidos())
                    .email(fila.email())
                    .contrasena(inutilizable)
                    .rol(fila.rol())
                    .empresa(empresa)
                    .departamento(comprobada.departamento())
                    .codigoEmpleado(fila.codigoEmpleado());
            if (fila.horasSemanales() != null) {
                nuevo.horasSemanales(fila.horasSemanales());
            }
            User usuario = usuarios.save(nuevo.build());
            if (fila.diasVacaciones() != null) {
                saldos.save(VacationBalance.builder().usuario(usuario).anio(anio).diasTotales(fila.diasVacaciones())
                        .build());
            }
            // El aviso de bienvenida dentro de la aplicación, como un alta a mano.
            eventos.publishEvent(new NotificationEvents.EmployeeCreated(usuario, empresa.getNombre()));
            creados.add(usuario.getId());
        }
        invitaciones.encolar(empresa.getId(), creados);
        log.info("{} ha importado {} personas en la empresa {}.", actor.getId(), creados.size(), empresa.getId());
        return respuesta(false, leido, filas, creados.size());
    }

    private ImportacionPlantillaResponse respuesta(boolean simulada, LectorDePlantilla.Leido leido,
            List<Comprobada> filas, int creadas) {
        List<FilaDeImportacion> detalle = filas.stream().map(Comprobada::fila).toList();
        int conErrores = (int) detalle.stream().filter(f -> !f.errores().isEmpty()).count();
        return new ImportacionPlantillaResponse(simulada, detalle.size(), conErrores, creadas,
                leido.columnasIgnoradas(), detalle, null);
    }

    private List<Comprobada> comprobar(LectorDePlantilla.Leido leido, User actor) {
        boolean creaEmpleados = RoleAuthorities.tiene(actor, "empleado:crear");
        boolean creaOtrosRoles = RoleAuthorities.tiene(actor, "gestor:crear");
        boolean poneDepartamento = RoleAuthorities.tiene(actor, "departamento:gestionar");
        boolean configuraFicha = RoleAuthorities.tiene(actor, "empleado:configurar");

        Map<String, Department> porNombre = departamentos.findByEmpresaOrderByNombreAsc(actor.getEmpresa()).stream()
                .collect(Collectors.toMap(d -> LectorDePlantilla.normalizar(d.getNombre()), d -> d, (a, b) -> a));
        Set<String> correos = leido.filas().stream()
                .map(f -> Emails.normalizar(f.valor(LectorDePlantilla.EMAIL)))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toSet());
        Set<String> yaExisten = correos.isEmpty() ? Set.of() : usuarios.correosQueYaExisten(correos);
        Map<String, Integer> primeraVez = new HashMap<>();
        boolean hayCodigos = leido.filas().stream().anyMatch(f -> !f.valor(LectorDePlantilla.CODIGO).isEmpty());
        Set<String> codigosEnLaEmpresa = hayCodigos ? usuarios.codigosDeEmpleado(actor.getEmpresa().getId()) : Set.of();
        Map<String, Integer> primerCodigo = new HashMap<>();

        List<Comprobada> resultado = new ArrayList<>();
        for (LectorDePlantilla.Fila fila : leido.filas()) {
            List<String> errores = new ArrayList<>();

            String nombre = fila.valor(LectorDePlantilla.NOMBRE);
            String apellidos = fila.valor(LectorDePlantilla.APELLIDOS);
            if (nombre.isEmpty()) {
                errores.add("Falta el nombre.");
            } else if (nombre.length() > 100) {
                errores.add("El nombre no puede pasar de 100 caracteres.");
            }
            if (apellidos.isEmpty()) {
                errores.add("Faltan los apellidos.");
            } else if (apellidos.length() > 150) {
                errores.add("Los apellidos no pueden pasar de 150 caracteres.");
            }

            String email = Emails.normalizar(fila.valor(LectorDePlantilla.EMAIL));
            if (email.isEmpty()) {
                errores.add("Falta el correo.");
            } else if (email.length() > 255 || !CORREO.matcher(email).matches()) {
                errores.add("El correo no tiene un formato válido.");
            } else if (yaExisten.contains(email)) {
                errores.add("Ese correo ya tiene cuenta en NX Time.");
            } else if (primeraVez.containsKey(email)) {
                errores.add("El correo está repetido: ya sale en la fila " + primeraVez.get(email) + ".");
            } else {
                primeraVez.put(email, fila.numero());
            }

            Role rol = Role.EMPLEADO;
            String rolEscrito = fila.valor(LectorDePlantilla.ROL);
            if (!rolEscrito.isEmpty()) {
                rol = ROLES.get(LectorDePlantilla.normalizar(rolEscrito));
                if (rol == null) {
                    errores.add("Rol desconocido: «" + rolEscrito + "». Vale empleado, gestor, rrhh o admin.");
                    rol = Role.EMPLEADO;
                }
            }
            if (rol == Role.EMPLEADO ? !creaEmpleados : !creaOtrosRoles) {
                errores.add("No puedes dar de alta a alguien con el rol " + rol.name()
                        + ": solo un administrador puede dar roles por encima de empleado.");
            }

            Department departamento = null;
            String departamentoEscrito = fila.valor(LectorDePlantilla.DEPARTAMENTO);
            if (!departamentoEscrito.isEmpty()) {
                departamento = porNombre.get(LectorDePlantilla.normalizar(departamentoEscrito));
                if (!poneDepartamento) {
                    errores.add("No puedes asignar departamentos: deja la columna vacía.");
                } else if (departamento == null) {
                    errores.add("No hay ningún departamento «" + departamentoEscrito
                            + "» en la empresa. Créalo antes en Plantilla → Departamentos.");
                }
            }

            BigDecimal horas = null;
            String horasEscritas = fila.valor(LectorDePlantilla.HORAS);
            if (!horasEscritas.isEmpty()) {
                horas = numero(horasEscritas);
                if (!configuraFicha) {
                    errores.add("No puedes poner la jornada: deja la columna vacía.");
                } else if (horas == null || horas.scale() > 1 || horas.compareTo(new BigDecimal("0.1")) < 0
                        || horas.compareTo(new BigDecimal("60")) > 0) {
                    errores.add("La jornada tiene que ser un número de horas entre 0,1 y 60, con un decimal como mucho.");
                    horas = null;
                }
            }

            String codigo = CodigosDeEmpleado.normalizar(fila.valor(LectorDePlantilla.CODIGO));
            if (codigo != null) {
                String problema = CodigosDeEmpleado.problema(codigo);
                String clave = CodigosDeEmpleado.clave(codigo);
                if (!configuraFicha) {
                    errores.add("No puedes poner el código de empleado: deja la columna vacía.");
                } else if (problema != null) {
                    errores.add(problema);
                } else if (codigosEnLaEmpresa.contains(clave)) {
                    errores.add("El código de empleado «" + codigo + "» ya lo tiene otra persona de la empresa.");
                } else if (primerCodigo.containsKey(clave)) {
                    errores.add("El código de empleado está repetido: ya sale en la fila " + primerCodigo.get(clave) + ".");
                } else {
                    primerCodigo.put(clave, fila.numero());
                }
            }

            Integer vacaciones = null;
            String vacacionesEscritas = fila.valor(LectorDePlantilla.VACACIONES);
            if (!vacacionesEscritas.isEmpty()) {
                BigDecimal dias = numero(vacacionesEscritas);
                if (!configuraFicha) {
                    errores.add("No puedes poner las vacaciones: deja la columna vacía.");
                } else if (dias == null || dias.stripTrailingZeros().scale() > 0 || dias.signum() < 0
                        || dias.compareTo(new BigDecimal("365")) > 0) {
                    errores.add("Las vacaciones tienen que ser un número entero de días, entre 0 y 365.");
                } else {
                    vacaciones = dias.intValue();
                }
            }

            resultado.add(new Comprobada(new FilaDeImportacion(fila.numero(), nombre, apellidos, email, rol,
                    departamento == null ? null : departamento.getNombre(), horas, vacaciones, List.copyOf(errores),
                    configuraFicha ? codigo : null),
                    departamento));
        }
        return resultado;
    }

    /** «37,5» o «37.5»; null si no es un número. */
    private static BigDecimal numero(String texto) {
        try {
            return new BigDecimal(texto.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
