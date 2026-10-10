package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.VerificadorDeAuditoria;
import com.nxtime.nxtime.domain.AuditCheckpoint;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.PlatformOrder;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PaginaDTO;
import com.nxtime.nxtime.dto.PlatformCompanyDetailResponse;
import com.nxtime.nxtime.dto.PlatformCompanyDetailResponse.Administrador;
import com.nxtime.nxtime.dto.PlatformCompanyDetailResponse.Fichajes;
import com.nxtime.nxtime.dto.PlatformCompanyDetailResponse.Plantilla;
import com.nxtime.nxtime.dto.PlatformCompanyDetailResponse.Sesiones;
import com.nxtime.nxtime.dto.PlatformCompanyResponse;
import com.nxtime.nxtime.dto.PlatformSummaryResponse;
import com.nxtime.nxtime.dto.PlatformSummaryResponse.Altas;
import com.nxtime.nxtime.dto.PlatformSummaryResponse.Cadena;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.PlatformRepository;
import com.nxtime.nxtime.repository.PlatformRepository.ActividadProjection;
import com.nxtime.nxtime.repository.PlatformRepository.AltasProjection;
import com.nxtime.nxtime.repository.PlatformRepository.CuentasProjection;
import com.nxtime.nxtime.repository.PlatformRepository.EmpresaProjection;
import com.nxtime.nxtime.repository.PlatformRepository.FichajesDeEmpresaProjection;
import com.nxtime.nxtime.repository.PlatformRepository.FichajesProjection;
import com.nxtime.nxtime.repository.PlatformRepository.PlantillaProjection;
import com.nxtime.nxtime.repository.PlatformRepository.RecuentosProjection;
import com.nxtime.nxtime.repository.PlatformRepository.SesionesProjection;
import com.nxtime.nxtime.repository.PlatformRepository.UltimaSesionProjection;
import com.nxtime.nxtime.service.PlatformService;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlatformServiceImpl implements PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformServiceImpl.class);

    /**
     * «Hoy» y «esta semana» son los de Madrid: es desde donde se mira la
     * instalación, igual que las tareas nocturnas corren con ese reloj. Lo de
     * cada empresa sigue contándose en su zona; esto solo afecta a los totales.
     */
    private static final ZoneId ZONA = ZoneId.of(ScheduledTask.ZONA);

    /** Cuántas semanas de altas se enseñan, contando la que corre. */
    static final int SEMANAS_DE_ALTAS = 12;

    private final PlatformRepository platformRepository;
    private final CompanyRepository companyRepository;
    private final TaskMonitorService taskMonitorService;
    private final VerificadorDeAuditoria verificador;

    public PlatformServiceImpl(
            PlatformRepository platformRepository,
            CompanyRepository companyRepository,
            TaskMonitorService taskMonitorService,
            VerificadorDeAuditoria verificador) {
        this.platformRepository = platformRepository;
        this.companyRepository = companyRepository;
        this.taskMonitorService = taskMonitorService;
        this.verificador = verificador;
    }

    @Override
    public PlatformSummaryResponse resumen() {
        Instant ahora = Instant.now();
        LocalDate hoy = LocalDate.ofInstant(ahora, ZONA);

        ActividadProjection actividad = platformRepository.actividad(
                hoy.atStartOfDay(ZONA).toInstant(), ahora.minus(Duration.ofDays(30)));
        CuentasProjection cuentas = platformRepository.cuentas();

        return new PlatformSummaryResponse(
                actividad.getEmpresas(),
                actividad.getEmpresasConActividad(),
                cuentas.getActivas(),
                cuentas.getSinConfirmar(),
                actividad.getFichajesHoy(),
                altasPorSemana(hoy),
                taskMonitorService.estado(),
                cadena());
    }

    /** Las doce últimas semanas, con sus ceros: una semana sin altas también es un dato. */
    private List<Altas> altasPorSemana(LocalDate hoy) {
        LocalDate estaSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate primera = estaSemana.minusWeeks(SEMANAS_DE_ALTAS - 1L);
        Map<LocalDate, Long> altas = platformRepository.altasPorSemana(primera.atStartOfDay(ZONA).toInstant())
                .stream()
                .collect(Collectors.toMap(AltasProjection::getSemana, AltasProjection::getAltas));
        return IntStream.range(0, SEMANAS_DE_ALTAS)
                .mapToObj(primera::plusWeeks)
                .map(semana -> new Altas(semana, altas.getOrDefault(semana, 0L)))
                .toList();
    }

    /**
     * Lo que dejó dicho la comprobación de cada noche. Barato: no recorre nada.
     * El recorrido completo se pide aparte, y es quien da la fila rota.
     */
    private Cadena cadena() {
        Optional<AuditCheckpoint> punto = verificador.ultimoPuntoDeControl();
        return punto
                .map(p -> new Cadena(p.getVerificadoEn(), p.getFilas(), verificador.movimientosSinRevisar(p)))
                .orElseGet(() -> new Cadena(null, 0, verificador.movimientosEnTotal()));
    }

    @Override
    public PaginaDTO<PlatformCompanyResponse> empresas(String busqueda, PlatformOrder orden, Pageable pagina) {
        Page<EmpresaProjection> empresas = platformRepository.empresas(
                busqueda == null ? "" : busqueda.trim(), orden.name(), pagina);
        if (empresas.isEmpty()) {
            return PaginaDTO.de(empresas, empresa -> fila(empresa, null, null));
        }

        // Una consulta por métrica para toda la página, no una por empresa.
        List<Long> ids = empresas.getContent().stream().map(EmpresaProjection::getId).toList();
        Instant ahora = Instant.now();
        Map<Long, FichajesProjection> fichajes = platformRepository
                .fichajesRecientes(ids, ahora.minus(Duration.ofDays(7)), ahora.minus(Duration.ofDays(30)))
                .stream()
                .collect(Collectors.toMap(FichajesProjection::getEmpresaId, Function.identity()));
        Map<Long, Instant> sesiones = platformRepository.ultimasSesiones(ids).stream()
                .collect(Collectors.toMap(UltimaSesionProjection::getEmpresaId, UltimaSesionProjection::getUltima));

        return PaginaDTO.de(empresas,
                empresa -> fila(empresa, fichajes.get(empresa.getId()), sesiones.get(empresa.getId())));
    }

    /** Sin fichajes recientes no hay fila en la consulta: son ceros, no un dato que falte. */
    private static PlatformCompanyResponse fila(
            EmpresaProjection empresa, FichajesProjection fichajes, Instant ultimaSesion) {
        return new PlatformCompanyResponse(
                empresa.getId(),
                empresa.getNombre(),
                empresa.getZonaHoraria(),
                empresa.getCreadaEn(),
                empresa.getEmpleadosActivos(),
                empresa.getEmpleadosDeBaja(),
                empresa.getSinConfirmar() > 0,
                empresa.getUltimoFichaje(),
                fichajes == null ? 0 : fichajes.getEn7Dias(),
                fichajes == null ? 0 : fichajes.getEn30Dias(),
                fichajes == null ? 0 : fichajes.getPersonas(),
                ultimaSesion);
    }

    @Override
    public PlatformCompanyDetailResponse empresa(long empresaId, User operador) {
        Company empresa = companyRepository.findById(empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Esa empresa no existe."));

        // Quién ha mirado a quién. Aquí se ven los correos de los ADMIN de
        // otra empresa: que quede constancia es parte del trato (ADR 040).
        log.info("Panel de plataforma: el usuario {} ha consultado la empresa {}.", operador.getId(), empresaId);

        Instant ahora = Instant.now();
        Instant hace7 = ahora.minus(Duration.ofDays(7));
        Instant hace30 = ahora.minus(Duration.ofDays(30));

        Map<Role, PlantillaProjection> porRol = platformRepository.plantilla(empresaId).stream()
                .collect(Collectors.toMap(fila -> Role.valueOf(fila.getRol()), Function.identity()));
        List<Plantilla> plantilla = Arrays.stream(Role.values())
                .map(rol -> Optional.ofNullable(porRol.get(rol))
                        .map(fila -> new Plantilla(rol, fila.getActivos(), fila.getDeBaja()))
                        .orElseGet(() -> new Plantilla(rol, 0, 0)))
                .toList();

        List<Administrador> administradores = platformRepository.administradores(empresaId).stream()
                .map(admin -> new Administrador(admin.getNombre(), admin.getEmail(), admin.getSinConfirmar()))
                .toList();

        FichajesDeEmpresaProjection fichajes = platformRepository.fichajesDe(empresaId, hace7, hace30);
        SesionesProjection sesiones = platformRepository.sesionesDe(empresaId, hace30);
        RecuentosProjection recuentos = platformRepository.recuentosDe(empresaId);

        return new PlatformCompanyDetailResponse(
                empresa.getId(),
                empresa.getNombre(),
                empresa.getZonaHoraria(),
                empresa.getCreadaEn(),
                plantilla.stream().mapToLong(Plantilla::activos).sum(),
                plantilla.stream().mapToLong(Plantilla::deBaja).sum(),
                plantilla,
                administradores,
                new Fichajes(fichajes.getUltimo(), fichajes.getTotal(), fichajes.getEn7Dias(),
                        fichajes.getEn30Dias(), fichajes.getPersonas()),
                new Sesiones(sesiones.getUltima(), sesiones.getWeb(), sesiones.getApp()),
                recuentos.getConGoogle(),
                recuentos.getConMicrosoft(),
                recuentos.getKioscos(),
                recuentos.getDispositivosPush(),
                recuentos.getDepartamentos(),
                recuentos.getProyectos(),
                recuentos.getBytesDeAdjuntos(),
                recuentos.getBorradosPendientes(),
                recuentos.getMovimientosDeAuditoria());
    }
}
