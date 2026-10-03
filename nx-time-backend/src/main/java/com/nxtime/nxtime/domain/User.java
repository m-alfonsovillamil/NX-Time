package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuario (empleado o gestor). Tabla "usuarios".
 *
 * Ya NO implementa UserDetails (ver auditoría, defectos de diseño):
 * ese acoplamiento a Spring Security se traslada al adaptador
 * {@link com.nxtime.nxtime.security.SecurityUser}, que envuelve esta
 * entidad. Esto es, entre otras cosas, lo que permite que ningún
 * controlador pueda ya devolver por accidente la contraseña cifrada al
 * serializar un User -- la propia clase ya no expone getPassword().
 *
 * IDs con GenerationType.IDENTITY desde la Fase 3 (ver Company.java).
 *
 * "activo"/"fechaBaja" desde la Fase 4: antes los flags de cuenta de
 * UserDetails estaban cableados a true sin excepción (ver auditoría,
 * defectos de diseño) -- no se podía dar de baja a nadie. Un usuario
 * dado de baja no puede autenticarse ({@link
 * com.nxtime.nxtime.security.SecurityUser#isEnabled()}), pero sus
 * fichajes y ausencias pasadas se conservan tal cual (requisito legal
 * de trazabilidad, no se borran ni se anonimizan).
 */
@Entity(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true)
    private String email;

    private String nombre;

    private String contrasena;

    @Enumerated(EnumType.STRING)
    private Role rol;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @Builder.Default
    private boolean activo = true;

    private Instant fechaBaja;

    /**
     * Jornada esperada, en horas por semana (Fase 9). 40 h es la
     * jornada máxima ordinaria en España (art. 34 ET); por eso es el
     * valor por defecto. Base para detectar incidencias.
     */
    @Builder.Default
    private BigDecimal horasSemanales = new BigDecimal("40.0");

    /*
     * Datos personales (Fase B). Todos opcionales: los rellena la propia
     * persona desde su perfil, y una plantilla ya dada de alta no tiene
     * ninguno. "apellidos" va aparte de "nombre" porque un listado de
     * RRHH se ordena por apellido, y eso no se puede hacer con un campo
     * único sin partir cadenas a ojo.
     */
    private String apellidos;

    private LocalDate fechaNacimiento;

    private String puesto;

    @ManyToOne
    @JoinColumn(name = "departamento_id")
    private Department departamento;

    /*
     * Fichar en un kiosco (ADR 033). El PIN lo elige la persona y va con
     * BCrypt; los fallos seguidos lo bloquean un rato. La tarjeta QR no guarda
     * secreto: su contenido es una firma del servidor sobre (id, versión), y
     * regenerarla sube la versión. Null = sin PIN / sin tarjeta.
     */
    private String kioscoPinHash;

    @Builder.Default
    private int kioscoPinFallos = 0;

    private Instant kioscoPinBloqueadoHasta;

    private Integer kioscoTarjetaVersion;

    /**
     * Quien registró la empresa y aún no ha confirmado su correo (V37, ADR
     * 034): no puede entrar hasta canjear el código que le llegó. Null para
     * todos los demás.
     */
    private Instant correoSinConfirmarDesde;

    /** Ha registrado una empresa y todavía no ha demostrado que el correo es suyo. */
    public boolean correoPendienteDeConfirmar() {
        return correoSinConfirmarDesde != null;
    }

    @Version
    private long version;

    /**
     * Última red antes de la base: el correo se guarda siempre en minúsculas.
     *
     * Los DTO de entrada ya normalizan lo que teclea la gente, pero esto cubre
     * lo que no pasa por ahí -- el sembrador de datos de demo, los tests, y
     * cualquier alta futura que llegue por otro camino. Que el índice único de
     * V17 sea sobre lower(email) no sirve de nada si la fila entra con
     * mayúsculas: se guardaría bien, pero el correo que le mandamos a esa
     * persona llevaría una dirección que luego ella no podría teclear igual.
     */
    @PrePersist
    @PreUpdate
    private void normalizarEmail() {
        this.email = Emails.normalizar(this.email);
    }

    /**
     * La zona en la que se cuentan los días de esta persona: la de su empresa
     * (ADR 032). Sin empresa -- solo pasa en tests unitarios -- la de por defecto.
     */
    public ZoneId zona() {
        return Company.zonaDe(empresa);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
