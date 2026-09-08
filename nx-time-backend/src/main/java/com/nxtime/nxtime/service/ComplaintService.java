package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ComplaintCreatedResponse;
import com.nxtime.nxtime.dto.ComplaintMessageRequest;
import com.nxtime.nxtime.dto.ComplaintResponse;
import com.nxtime.nxtime.dto.ComplaintSummaryResponse;
import com.nxtime.nxtime.dto.CreateComplaintRequest;
import com.nxtime.nxtime.dto.UpdateComplaintStatusRequest;
import java.util.List;

/**
 * El canal interno de información de la Ley 2/2023 (Fase G).
 *
 * Tiene <b>dos puertas y no una</b>, y esa es la forma del servicio:
 *
 * <ul>
 *   <li>La del <b>denunciante</b>, con dos llaves para la misma puerta.
 *       El <b>código</b> ({@link #seguimiento}, {@link #responder}), que
 *       es la única posible en una denuncia anónima y donde no se
 *       comprueba quién lo trae — comprobarlo sería negar el anonimato.
 *       Y la <b>titularidad</b> ({@link #miDetalle},
 *       {@link #responderComoDenunciante}), que solo existe para las
 *       identificadas: quien se identificó al denunciar no tiene por qué
 *       perder su expediente por perder un papel, porque de él sí
 *       sabemos que es suyo.</li>
 *   <li>La de <b>quien instruye</b>, que entra por id y con la authority
 *       {@code denuncia:instruir} ({@link #bandeja}, {@link #detalle},
 *       {@link #responderComoInstructor}, {@link #cambiarEstado}).</li>
 * </ul>
 *
 * Las dos puertas no se mezclan a propósito: un solo {@code obtener(id)}
 * que decidiera por dentro quién llama es como se cuela un día un caso
 * que deja leer una denuncia ajena a quien solo tenía un número.
 */
public interface ComplaintService {

    /** Presentar una denuncia. Devuelve el código UNA sola vez. */
    ComplaintCreatedResponse presentar(CreateComplaintRequest request, User actor);

    /** Consultar un expediente con el código de seguimiento. */
    ComplaintResponse seguimiento(String codigo, User actor);

    /** Escribir en el expediente como denunciante, con el código. */
    ComplaintResponse responder(String codigo, ComplaintMessageRequest request, User actor);

    /**
     * Las denuncias que presentó una persona <b>identificándose</b>.
     * Las anónimas no salen aquí ni pueden salir.
     */
    List<ComplaintSummaryResponse> mias(User actor);

    /**
     * Un expediente propio, por id, para quien lo presentó
     * identificándose.
     *
     * Sobre una anónima devuelve 404 <b>aunque sea suya</b>: el sistema
     * no sabe que lo es. A esa se entra solo con el código.
     */
    ComplaintResponse miDetalle(long id, User actor);

    /** Escribir en un expediente propio, sin necesidad del código. */
    ComplaintResponse responderComoDenunciante(
            long id, ComplaintMessageRequest request, User actor);

    /** La bandeja de quien instruye: las de su empresa. */
    List<ComplaintSummaryResponse> bandeja(User actor);

    /** Un expediente completo, por id, para quien instruye. */
    ComplaintResponse detalle(long id, User actor);

    /** Escribir en el expediente como instructor. */
    ComplaintResponse responderComoInstructor(
            long id, ComplaintMessageRequest request, User actor);

    /** Mover la denuncia de estado. Cerrarla exige conclusión. */
    ComplaintResponse cambiarEstado(long id, UpdateComplaintStatusRequest request, User actor);
}
