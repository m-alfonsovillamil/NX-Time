package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Empresa (tenant). Tabla "empresas".
 *
 * IDs con GenerationType.IDENTITY (BIGSERIAL de PostgreSQL) desde la
 * Fase 3. En SQLite esto no funcionaba (sqlite-jdbc no implementa
 * getGeneratedKeys()) y el workaround con GenerationType.TABLE
 * obligaba a quitar @Transactional de varios métodos de escritura
 * (ver AuthServiceImpl.registerManager antes de esta fase); con
 * PostgreSQL el problema desaparece de raíz.
 *
 * @Version añade bloqueo optimista: dos escrituras concurrentes sobre
 * la misma fila ya no pueden pisarse en silencio.
 *
 * La zona horaria es de cada empresa (ADR 032): decide a qué día pertenece
 * cada fichaje. Todo cálculo de "qué día es" pasa por {@link #zona()}.
 */
@Entity(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    /** La de todas las empresas antes del ADR 032, y la de las nuevas si no se dice otra. */
    public static final String ZONA_POR_DEFECTO = "Europe/Madrid";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String nombre;

    /** Nombre IANA. Se valida al guardarlo; ver {@link #zona()}. */
    @Column(name = "zona_horaria", nullable = false, length = 64)
    @Builder.Default
    private String zonaHoraria = ZONA_POR_DEFECTO;

    /**
     * Cuándo se dio de alta (V41, ADR 040). La pone {@link #alCrear()}. Null en
     * las anteriores a octubre de 2026 de las que no quedaba ningún rastro.
     */
    @Column(name = "creada_en")
    private Instant creadaEn;

    /** V43, ADR 046: solo una empresa ACTIVA deja entrar a su gente. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private CompanyStatus estado = CompanyStatus.ACTIVA;

    @Column(name = "aprobada_en")
    private Instant aprobadaEn;

    /** La versión de las condiciones y del contrato de encargo que aceptó, cuándo y quién (V43). */
    @Column(name = "condiciones_version", length = 32)
    private String condicionesVersion;

    @Column(name = "condiciones_aceptadas_en")
    private Instant condicionesAceptadasEn;

    @Column(name = "condiciones_aceptadas_por")
    private Long condicionesAceptadasPor;

    /**
     * A quién se dirige su gente para ejercer sus derechos del RGPD: un correo
     * o un enlace https (V44, ADR 047). La empresa es la responsable; NX Time,
     * el encargado. Null si el ADMIN no lo ha puesto.
     */
    @Column(name = "contacto_privacidad", length = 255)
    private String contactoPrivacidad;

    /**
     * El Responsable del Sistema Interno de Información (Ley 2/2023, art. 8;
     * V45, ADR 048): el único que instruye el canal de denuncias. Un id y no
     * una relación: se mira en cada petición para resolver los permisos
     * ({@link RoleAuthorities#de}). Null: instruyen los ADMIN.
     */
    @Column(name = "responsable_canal_id")
    private Long responsableCanalId;

    @Column(name = "responsable_canal_desde")
    private Instant responsableCanalDesde;

    /** Cómo comunicar algo de palabra o pedir una reunión (arts. 7.2 y 9.2). */
    @Column(name = "canal_via_verbal", length = 500)
    private String canalViaVerbal;

    /** Cuándo pidió el ADMIN la baja: a los 30 días se borra entera (V46, ADR 050). */
    @Column(name = "baja_solicitada_en")
    private Instant bajaSolicitadaEn;

    @Column(name = "baja_solicitada_por")
    private Long bajaSolicitadaPor;

    /**
     * Si sus ADMIN y RRHH tienen que entrar con segundo factor (V47, ADR 051).
     * Lo decide un ADMIN en los ajustes de la empresa.
     */
    @Builder.Default
    @Column(name = "exigir_segundo_factor", nullable = false)
    private boolean exigirSegundoFactor = false;

    /**
     * Si al entrar se pregunta presencial o a distancia (V51, ADR 055). Lo
     * decide un ADMIN en los ajustes de la empresa.
     */
    @Builder.Default
    @Column(name = "preguntar_modalidad", nullable = false)
    private boolean preguntarModalidad = false;

    /**
     * Si se pide la ubicación al entrar y al salir (V52, ADR 056). Lo decide
     * un ADMIN, después de informar a la plantilla (art. 90 LOPDGDD).
     */
    @Builder.Default
    @Column(name = "ubicacion_al_fichar", nullable = false)
    private boolean ubicacionAlFichar = false;

    public boolean estaActiva() {
        return estado == CompanyStatus.ACTIVA;
    }

    /** Lo que acepta quien la registra, o el ADMIN que acepta una versión nueva. */
    public void aceptarCondiciones(String version, User quien, Instant cuando) {
        this.condicionesVersion = version;
        this.condicionesAceptadasPor = quien.getId() == 0 ? null : quien.getId();
        this.condicionesAceptadasEn = cuando;
    }

    @Version
    private long version;

    /** La zona en la que se cuentan los días de esta empresa. */
    public ZoneId zona() {
        return ZoneId.of(zonaHoraria == null ? ZONA_POR_DEFECTO : zonaHoraria);
    }

    /** La zona de una empresa, o la de por defecto si no hay empresa (solo en tests unitarios). */
    public static ZoneId zonaDe(Company empresa) {
        return empresa != null ? empresa.zona() : ZoneId.of(ZONA_POR_DEFECTO);
    }

    /**
     * Quien crea la empresa sin decir zona (el builder o {@code new}) se queda
     * con la de por defecto, y la fecha de alta es la de este momento. Aquí y no
     * en cada sitio que crea una empresa (el registro, el del SSO y los datos
     * de demo): así ninguno se la puede dejar.
     */
    @PrePersist
    void alCrear() {
        if (zonaHoraria == null) {
            zonaHoraria = ZONA_POR_DEFECTO;
        }
        if (creadaEn == null) {
            creadaEn = Instant.now();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Company other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
