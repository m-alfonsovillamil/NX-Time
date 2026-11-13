package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.FichajeRecibido;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.SinConexionDtos.Fichaje;
import com.nxtime.nxtime.dto.SinConexionDtos.Resultado;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.repository.FichajeRecibidoRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que pasa cuando un fichaje del lote falla sin ser una regla del fichaje.
 * Antes, cualquier excepción así daba un 500 a todo el lote: la app no quitaba
 * nada de la cola, y si el fallo se repetía, lo de detrás no llegaba nunca.
 */
class RecepcionDeFichajesSinConexionTest {

    private static final Instant AHORA = Instant.parse("2026-11-13T12:00:00Z");

    private final TimeEntryService fichajes = mock(TimeEntryService.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private final FichajeRecibidoRepository recibidos = mock(FichajeRecibidoRepository.class);
    private final TransactionTemplate transaccion = mock(TransactionTemplate.class);
    private final AtomicLong ids = new AtomicLong();
    private RecepcionDeFichajesSinConexion recepcion;
    private User persona;

    @BeforeEach
    void preparar() {
        persona = User.builder().id(7L).email("tecnica@nxtime.test").nombre("Técnica").rol(Role.EMPLEADO)
                .empresa(Company.builder().id(1L).nombre("Empresa").build()).activo(true).build();
        when(usuarios.findByEmail(persona.getEmail())).thenReturn(Optional.of(persona));
        when(usuarios.findById(7L)).thenReturn(Optional.of(persona));
        when(recibidos.findByUsuarioIdAndIdCliente(anyLong(), any())).thenReturn(Optional.empty());
        when(recibidos.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transaccion.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
        recepcion = new RecepcionDeFichajesSinConexion(fichajes, usuarios, recibidos, transaccion,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Fichaje hecho(TimeEntryAction tipo, Duration hace) {
        return new Fichaje(UUID.randomUUID(), tipo, null, AHORA.minus(hace), hace.toMillis());
    }

    /** Lo que devuelve el servicio cuando el fichaje se aplica. */
    private TimeEntry aplicado() {
        return TimeEntry.builder().id(ids.incrementAndGet()).build();
    }

    private void alFichar(TimeEntryAction tipo, Object respuesta) {
        var llamada = when(fichajes.registrarSinConexion(any(), argThat((TimeEntryRequest r) -> r != null && r.tipo() == tipo),
                any(), anyString(), anyBoolean()));
        if (respuesta instanceof RuntimeException e) {
            llamada.thenThrow(e);
        } else {
            llamada.thenAnswer(i -> aplicado());
        }
    }

    @Test
    @DisplayName("Si la base falla a mitad, se contesta lo procesado y lo de detrás se deja para el reenvío")
    void laBaseCaidaParaElLote() {
        alFichar(TimeEntryAction.INICIO, "ok");
        alFichar(TimeEntryAction.PAUSA_INICIO, new QueryTimeoutException("statement timeout"));
        alFichar(TimeEntryAction.FIN, "ok");

        List<Resultado> resultados = recepcion.recibir(persona.getEmail(), List.of(
                hecho(TimeEntryAction.INICIO, Duration.ofHours(5)),
                hecho(TimeEntryAction.PAUSA_INICIO, Duration.ofHours(3)),
                hecho(TimeEntryAction.FIN, Duration.ofHours(1))));

        // Solo la entrada: la pausa y la salida no se nombran, así que la app
        // las deja en la cola y las reenvía, en orden.
        assertThat(resultados).extracting(Resultado::resultado).containsExactly(FichajeRecibido.Resultado.APLICADO);
        verify(fichajes, never()).registrarSinConexion(any(),
                argThat((TimeEntryRequest r) -> r != null && r.tipo() == TimeEntryAction.FIN), any(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("Sin poder abrir la transacción del primero, la respuesta va vacía y la app reintenta")
    void sinConexionesLibresNoSeNombraNada() {
        doThrow(new CannotCreateTransactionException("sin conexiones libres")).when(transaccion).execute(any());

        List<Resultado> resultados = recepcion.recibir(persona.getEmail(), List.of(
                hecho(TimeEntryAction.INICIO, Duration.ofHours(2))));

        assertThat(resultados).isEmpty();
    }

    @Test
    @DisplayName("Si la consulta del reenvío falla, también se para sin rechazar nada")
    void fallaLaConsultaDelPrevio() {
        when(recibidos.findByUsuarioIdAndIdCliente(anyLong(), any()))
                .thenThrow(new DataAccessResourceFailureException("conexión perdida"));

        List<Resultado> resultados = recepcion.recibir(persona.getEmail(), List.of(
                hecho(TimeEntryAction.INICIO, Duration.ofHours(2))));

        assertThat(resultados).isEmpty();
        verify(recibidos, never()).save(any());
    }

    @Test
    @DisplayName("Un fallo que no es de la base se rechaza, para no atascar la cola, y lo de detrás sigue")
    void unFalloInesperadoSeRechazaYSeSigue() {
        alFichar(TimeEntryAction.INICIO, "ok");
        alFichar(TimeEntryAction.PAUSA_INICIO, new IllegalStateException("un bug"));
        alFichar(TimeEntryAction.FIN, "ok");

        List<Resultado> resultados = recepcion.recibir(persona.getEmail(), List.of(
                hecho(TimeEntryAction.INICIO, Duration.ofHours(5)),
                hecho(TimeEntryAction.PAUSA_INICIO, Duration.ofHours(3)),
                hecho(TimeEntryAction.FIN, Duration.ofHours(1))));

        assertThat(resultados).extracting(Resultado::resultado).containsExactly(
                FichajeRecibido.Resultado.APLICADO, FichajeRecibido.Resultado.RECHAZADO, FichajeRecibido.Resultado.APLICADO);
        assertThat(resultados.get(1).motivo()).isEqualTo(RecepcionDeFichajesSinConexion.FALLO_DEL_SERVIDOR);
        // El rechazo queda guardado: si la app lo reenvía, se contesta lo mismo
        // en vez de volver a fallar.
        verify(recibidos).save(argThat(f -> f.getResultado() == FichajeRecibido.Resultado.RECHAZADO
                && RecepcionDeFichajesSinConexion.FALLO_DEL_SERVIDOR.equals(f.getMotivo())));
    }
}
