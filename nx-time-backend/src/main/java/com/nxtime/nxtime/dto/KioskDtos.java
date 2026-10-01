package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.WorkStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Lo que entra y sale del kiosco de fichaje (ADR 033), en un solo sitio: son
 * piezas pequeñas de una misma conversación entre la tablet, el ADMIN que la
 * empareja y cada persona que ficha en ella.
 */
public final class KioskDtos {

    private KioskDtos() {
    }

    // ------------------------------------------------------------------
    // Emparejar la tablet
    // ------------------------------------------------------------------

    /**
     * Lo que recibe la tablet al pedir emparejarse.
     *
     * @param codigo lo que enseña en pantalla para que el ADMIN lo teclee
     * @param secreto con lo que pregunta por el estado; solo lo tiene ella
     */
    public record PairingStarted(String codigo, String secreto, Instant caducaEn) {
    }

    public record PairingStatusRequest(@NotBlank String secreto) {
    }

    public enum PairingState {
        /** Todavía no lo ha confirmado nadie. */
        PENDIENTE,
        /** Confirmado: la respuesta trae el token, esta única vez. */
        LISTO,
        /** Ya se entregó el token: no se repite. */
        ENTREGADO,
        /** Pasaron los diez minutos sin que nadie lo confirmara. */
        CADUCADO
    }

    /** @param token solo con estado LISTO, y solo esa vez */
    public record PairingStatus(PairingState estado, String token, KioskInfo kiosco) {
    }

    public record ConfirmKioskRequest(
            @NotBlank(message = "Teclea el código que enseña la tablet.")
            @Size(max = 20, message = "El código tiene 8 caracteres.")
            String codigo,

            @NotBlank(message = "Ponle un nombre al kiosco.")
            @Size(max = 80, message = "El nombre no puede pasar de 80 caracteres.")
            String nombre) {
    }

    /** Un kiosco, como lo ve el ADMIN en los ajustes de la empresa. */
    public record KioskResponse(long id, String nombre, Instant creadoEn, Instant ultimoUso, boolean activo) {
    }

    /** Quién es el kiosco, para su propia pantalla. */
    public record KioskInfo(String nombre, String empresa, String zonaHoraria) {
    }

    // ------------------------------------------------------------------
    // Fichar en la tablet
    // ------------------------------------------------------------------

    /** Una persona de la lista del kiosco: solo lo que hace falta para encontrarse. Sin correo. */
    public record KioskPerson(long id, String nombre, String apellidos) {
    }

    /**
     * Cómo se identifica alguien: con su tarjeta ({@code qr}) o con su nombre y
     * su PIN ({@code usuarioId} + {@code pin}). Una de las dos.
     */
    public record KioskCredential(
            @Size(max = 200) String qr,
            Long usuarioId,
            @Size(max = 6) String pin) {
    }

    /** Quién es, en qué está y qué puede elegir al empezar. */
    public record KioskIdentity(
            long usuarioId,
            String nombre,
            WorkStatus estado,
            List<ClockProjectsResponse.ProjectOption> proyectos,
            ClockProjectsResponse.ProjectOption proyectoEnCurso) {
    }

    public record KioskClockRequest(
            @Size(max = 200) String qr,
            Long usuarioId,
            @Size(max = 6) String pin,
            @NotNull(message = "Falta qué fichar.") TimeEntryAction tipo,
            Long proyectoId) {

        public KioskCredential credencial() {
            return new KioskCredential(qr, usuarioId, pin);
        }
    }

    /** Lo que la tablet enseña al fichar: «Hola, Lucía: entrada a las 8:02». */
    public record KioskClockResponse(String nombre, TimeEntryAction tipo, Instant instante) {
    }

    // ------------------------------------------------------------------
    // Lo de cada persona, desde su perfil
    // ------------------------------------------------------------------

    public record KioskPinRequest(
            @NotBlank(message = "Escribe un PIN.")
            @Pattern(regexp = "\\d{4,6}", message = "El PIN tiene que tener de 4 a 6 cifras.")
            String pin) {
    }

    /** Si puede fichar en un kiosco, y cómo. */
    public record MyKioskStatus(boolean tienePin, boolean tieneTarjeta, Instant pinBloqueadoHasta) {
    }

    /** La tarjeta: lo que va en el QR y el QR ya dibujado. */
    public record KioskCard(long usuarioId, String nombre, String codigo, String svg) {
    }
}
