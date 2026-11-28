package com.nxtime.app.ui.horasextra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.BolsaHorasExtraDTO
import com.nxtime.app.data.dto.HorasExtraDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.BotonDeRevision
import com.nxtime.app.ui.components.Insignia
import com.nxtime.app.ui.components.NotaDeRevision
import com.nxtime.app.ui.components.PesoDeBoton
import com.nxtime.app.ui.components.TarjetaDeRevision
import com.nxtime.app.ui.components.TonoDeInsignia
import com.nxtime.app.ui.components.SeccionVacia
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import java.time.LocalDate

/**
 * Horas extra (Fase F).
 *
 * Arriba la bolsa anual; debajo, lo detectado en tus jornadas y —si te
 * toca revisar— lo de la empresa.
 *
 * Lo que la pantalla tiene que dejar claro por encima de todo es que un
 * aviso **todavía no son horas extra**. El reloj sabe que el martes duró
 * once horas; no sabe si fue una intensiva pactada o un fichaje mal
 * cerrado. Por eso cada tarjeta enseña el listón contra el que se
 * comparó y no solo el exceso: en un aviso semanal con un puente por
 * medio, "3 h de más" sin el "de 30 h" al lado no significa nada.
 */
@Composable
fun HorasExtraScreen(
    onVolver: () -> Unit,
    viewModel: HorasExtraViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    var aJustificar by remember { mutableStateOf<HorasExtraDTO?>(null) }
    var aConfirmar by remember { mutableStateOf<Pair<HorasExtraDTO, Decision>?>(null) }

    PantallaConBarra(
        titulo = stringResource(R.string.horas_extra_titulo),
        onVolver = onVolver
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.bolsa != null,
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                estado.error?.let { mensaje ->
                    item {
                        BannerError(
                            mensaje = mensaje.resolver(),
                            onReintentar = viewModel::descartarError
                        )
                    }
                }

                estado.bolsa?.let { bolsa ->
                    item { TarjetaBolsa(bolsa) }
                }

                item {
                    Text(
                        text = stringResource(R.string.horas_extra_mios),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (estado.mios.isEmpty() && !estado.cargando) {
                    // SeccionVacia y no EstadoVacio: el segundo ocupa la
                    // pantalla entera y hace scroll propio, y dentro de
                    // un item{} de LazyColumn revienta la app con
                    // "measured with an infinity maximum height".
                    item { SeccionVacia(stringResource(R.string.horas_extra_vacio_texto)) }
                }
                items(estado.mios, key = { "mio-" + it.id }) { aviso ->
                    // Sin botones: nadie revisa sus propias horas extra,
                    // ni siquiera quien tiene el permiso. El servidor lo
                    // rechaza con 403, así que ofrecerlos sería ofrecer
                    // algo que va a fallar.
                    TarjetaHorasExtra(aviso = aviso, conNombre = false)
                }

                if (estado.puedeRevisar) {
                    item { HorizontalDivider() }
                    item {
                        Text(
                            text = stringResource(R.string.horas_extra_del_equipo),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // La lista viene YA sin los avisos propios: los
                    // excluye el servidor, porque sobre los tuyos no
                    // decides nunca. Filtrar aquí sería duplicar esa
                    // regla fuera de donde vive.
                    if (estado.delEquipo.isEmpty() && !estado.cargando) {
                        item { SeccionVacia(stringResource(R.string.horas_extra_equipo_vacio_texto)) }
                    }
                    items(estado.delEquipo, key = { "equipo-" + it.id }) { aviso ->
                        TarjetaHorasExtra(
                            aviso = aviso,
                            conNombre = true,
                            onAceptar = { aConfirmar = aviso to Decision.PAGAR },
                            onCompensar = { aConfirmar = aviso to Decision.DESCANSO },
                            onJustificar = { aJustificar = aviso }
                        )
                    }
                }
            }
        }
    }

    aJustificar?.let { aviso ->
        DialogoJustificar(
            onConfirma = { justificacion ->
                viewModel.justificar(aviso.id, justificacion)
                aJustificar = null
            },
            onCancela = { aJustificar = null }
        )
    }

    aConfirmar?.let { (aviso, decision) ->
        DialogoConfirmarDecision(
            aviso = aviso,
            decision = decision,
            onConfirma = {
                when (decision) {
                    Decision.PAGAR -> viewModel.aceptar(aviso.id)
                    Decision.DESCANSO -> viewModel.compensarConDescanso(aviso.id)
                }
                aConfirmar = null
            },
            onCancela = { aConfirmar = null }
        )
    }

    LaunchedEffect(estado.aviso) {
        if (estado.aviso != null) viewModel.avisoMostrado()
    }
}

/**
 * La bolsa anual, con barra.
 *
 * La barra no es decoración: 48 h de 80 se entiende de un vistazo y
 * "2880 minutos" no. Y la cifra de avisos abiertos va al lado a
 * propósito, porque una bolsa al 60 % con quince avisos sin revisar no
 * significa lo mismo que una al 60 % sin ninguno.
 */
@Composable
private fun TarjetaBolsa(bolsa: BolsaHorasExtraDTO) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.horas_extra_bolsa, bolsa.anio),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = DateFormats.minutos(bolsa.minutosConsumidos.toLong()) + " / " +
                            DateFormats.minutos(bolsa.minutosTope.toLong()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (bolsa.alLimite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            val proporcion = if (bolsa.minutosTope == 0) {
                0f
            } else {
                bolsa.minutosConsumidos.toFloat() / bolsa.minutosTope
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        // Se acota a 1 porque pasarse del tope legal es
                        // posible en la vida real, y una barra que se
                        // sale de su carril solo se ve como un fallo.
                        .fillMaxWidth(proporcion.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (bolsa.alLimite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.horas_extra_bolsa_detalle,
                    DateFormats.minutos(bolsa.minutosDisponibles.toLong()),
                    bolsa.avisosAbiertos
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TarjetaHorasExtra(
    aviso: HorasExtraDTO,
    conNombre: Boolean,
    onAceptar: (() -> Unit)? = null,
    onCompensar: (() -> Unit)? = null,
    onJustificar: (() -> Unit)? = null
) {
    val tipo = TipoHorasExtra.de(aviso.tipo)
    val estado = EstadoHorasExtra.de(aviso.estado)
    val cuando = periodo(aviso, tipo)
    val decide = estado?.esperaDecision == true && onAceptar != null && onCompensar != null &&
            onJustificar != null

    TarjetaDeRevision(
        // La de otra persona empieza por su nombre: con veinte pendientes es
        // lo que se busca primero. La propia, por el día o la semana.
        titulo = if (conNombre) aviso.usuario else cuando,
        subtitulo = if (conNombre) cuando else null,
        estado = estado?.let {
            {
                Insignia(
                    texto = stringResource(it.etiqueta),
                    tono = when (it) {
                        EstadoHorasExtra.ACEPTADO -> TonoDeInsignia.EXITO
                        EstadoHorasExtra.ABIERTO -> TonoDeInsignia.AVISO
                        else -> TonoDeInsignia.NEUTRO
                    }
                )
            }
        },
        // El exceso Y el listón, siempre juntos. "3 h de más" a secas
        // no se puede juzgar: en una semana con un puente el listón
        // son 30 h y no 37,5, y sin verlo la cifra parece arbitraria.
        cifra = "+" + DateFormats.minutos(aviso.minutosExtra.toLong()),
        cifraDetalle = stringResource(
            R.string.horas_extra_sobre,
            DateFormats.minutos(aviso.minutosEsperados.toLong())
        ),
        contenido = {
            aviso.justificacion?.let {
                NotaDeRevision(texto = it, etiqueta = stringResource(R.string.horas_extra_nota_justificacion))
            }
            textoDeCompensacion(aviso, LocalDate.now(DateFormats.zona))?.let {
                Text(
                    text = if (it.fecha == null) {
                        stringResource(it.texto)
                    } else {
                        stringResource(it.texto, DateFormats.fechaCorta(it.fecha))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            aviso.revisadoPor?.let {
                Text(
                    text = stringResource(R.string.horas_extra_revisado_por, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        acciones = if (decide) {
            {
                BotonDeRevision(
                    texto = stringResource(R.string.horas_extra_justificar),
                    onClick = onJustificar!!,
                    peso = PesoDeBoton.CONTORNO
                )
                BotonDeRevision(
                    texto = stringResource(R.string.horas_extra_aceptar),
                    onClick = onAceptar!!,
                    peso = PesoDeBoton.TONAL
                )
                BotonDeRevision(
                    texto = stringResource(R.string.horas_extra_boton_descanso),
                    onClick = onCompensar!!,
                    peso = PesoDeBoton.TONAL
                )
            }
        } else {
            null
        }
    )
}

/** Lo que se confirma antes de aplicarlo: las dos decisiones que no se pueden deshacer. */
private enum class Decision { PAGAR, DESCANSO }

/**
 * Pagar o compensar con descanso no tiene vuelta atrás (el servidor da 409
 * a la segunda decisión), y los tres botones van juntos: un toque en el de
 * al lado decidiría otra cosa. Se confirma diciendo qué pasa con la bolsa.
 */
@Composable
private fun DialogoConfirmarDecision(
    aviso: HorasExtraDTO,
    decision: Decision,
    onConfirma: () -> Unit,
    onCancela: () -> Unit
) {
    val horas = DateFormats.minutos(aviso.minutosExtra.toLong())
    val cuando = periodo(aviso, TipoHorasExtra.de(aviso.tipo)).replaceFirstChar { it.lowercase() }
    AlertDialog(
        onDismissRequest = onCancela,
        title = {
            Text(
                stringResource(
                    when (decision) {
                        Decision.PAGAR -> R.string.horas_extra_confirmar_pagan_titulo
                        Decision.DESCANSO -> R.string.horas_extra_confirmar_descanso_titulo
                    }
                )
            )
        },
        text = {
            Text(
                stringResource(
                    when (decision) {
                        Decision.PAGAR -> R.string.horas_extra_confirmar_pagan_texto
                        Decision.DESCANSO -> R.string.horas_extra_confirmar_descanso_texto
                    },
                    horas,
                    aviso.usuario,
                    cuando
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirma) {
                // Las etiquetas cortas, las mismas de la tarjeta: con la larga
                // ("Se compensan con descanso") el diálogo apila los botones.
                Text(
                    stringResource(
                        when (decision) {
                            Decision.PAGAR -> R.string.horas_extra_aceptar
                            Decision.DESCANSO -> R.string.horas_extra_boton_descanso
                        }
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}

/**
 * "El 03/03/2026" o "Semana del 02/03/2026 al 08/03/2026".
 *
 * Un aviso semanal lleva el LUNES en `fecha`, así que enseñarla sola se
 * leería como si el exceso fuera de ese día.
 */
@Composable
private fun periodo(aviso: HorasExtraDTO, tipo: TipoHorasExtra?): String =
    if (tipo == TipoHorasExtra.SEMANAL) {
        stringResource(
            R.string.horas_extra_periodo_semana,
            DateFormats.fechaCorta(aviso.fecha),
            DateFormats.fechaCorta(aviso.fechaFin)
        )
    } else {
        stringResource(R.string.horas_extra_periodo_dia, DateFormats.fechaCorta(aviso.fecha))
    }

/**
 * Justificar exige explicación; aceptar no.
 *
 * La asimetría es deliberada y viene del servidor, que rechaza con 400
 * una justificación vacía: aceptar unas horas que el reloj ya ha medido
 * no necesita motivo, pero decidir que once horas trabajadas no cuentan
 * es la decisión que hay que poder enseñar motivada.
 */
@Composable
private fun DialogoJustificar(onConfirma: (String) -> Unit, onCancela: () -> Unit) {
    var texto by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(stringResource(R.string.horas_extra_justificar)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.horas_extra_justificar_ayuda),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text(stringResource(R.string.horas_extra_justificacion_etiqueta)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirma(texto) },
                // El servidor volvería a rechazarlo, pero deshabilitar el
                // botón lo dice antes y sin gastar una petición.
                enabled = texto.isNotBlank()
            ) {
                Text(stringResource(R.string.horas_extra_justificar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}
