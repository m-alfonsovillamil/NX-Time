package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.AttachmentResponse;
import com.nxtime.nxtime.service.AttachmentService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de AttachmentController (Fase B2).
 *
 * Lo que se fija aquí, además de las authorities, es la asimetría del
 * {@code Content-Disposition}: la foto va {@code inline} porque se
 * pinta, y el CV {@code attachment} porque se descarga. Es lo que
 * decide si al tocar un avatar la app enseña la imagen o abre un
 * diálogo de "guardar como".
 */
@NxTimeWebMvcTest(AttachmentController.class)
@Import(WebMvcTestSecurityConfig.class)
class AttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttachmentService attachmentService;

    private static AttachmentResponse cv() {
        return new AttachmentResponse(
                5L, AttachmentType.CV, "mi cv.pdf", "application/pdf", 2048,
                Instant.parse("2026-09-01T10:00:00Z"));
    }

    private static MockMultipartFile fichero() {
        return new MockMultipartFile("fichero", "cv.pdf", "application/pdf", "%PDF-1.7".getBytes());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /perfil/adjuntos como EMPLEADO devuelve 200: son SUS ficheros")
    void subir_comoEmpleado_devuelve200() throws Exception {
        when(attachmentService.subir(any(), eq(AttachmentType.CV), any())).thenReturn(cv());

        mockMvc.perform(multipart("/api/v1/perfil/adjuntos")
                        .file(fichero())
                        .param("tipo", "CV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CV"))
                .andExpect(jsonPath("$.mime").value("application/pdf"));
    }

    @Test
    @DisplayName("POST /perfil/adjuntos sin autenticar se rechaza y no llega al servicio")
    void subir_sinAutenticar_seRechaza() throws Exception {
        mockMvc.perform(multipart("/api/v1/perfil/adjuntos").file(fichero()).param("tipo", "CV"))
                .andExpect(status().is4xxClientError());

        verify(attachmentService, never()).subir(any(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /perfil/adjuntos devuelve la lista sin contenido")
    void listar_devuelve200() throws Exception {
        when(attachmentService.listar(any())).thenReturn(List.of(cv()));

        mockMvc.perform(get("/api/v1/perfil/adjuntos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreOriginal").value("mi cv.pdf"))
                .andExpect(jsonPath("$[0].tamanoBytes").value(2048))
                // El contenido no viaja aquí: se pide en su endpoint.
                .andExpect(jsonPath("$[0].contenido").doesNotExist());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un CV se descarga como 'attachment', con su nombre original")
    void descargar_cv_vaComoAttachment() throws Exception {
        when(attachmentService.descargar(eq(5L), any())).thenReturn(
                new AttachmentService.ContenidoDeAdjunto(
                        "%PDF-1.7".getBytes(), "mi cv.pdf", "application/pdf", AttachmentType.CV));

        mockMvc.perform(get("/api/v1/perfil/adjuntos/5"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"mi cv.pdf\""));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Una foto se sirve 'inline', para poder pintarla en el avatar")
    void descargar_foto_vaComoInline() throws Exception {
        when(attachmentService.descargar(eq(7L), any())).thenReturn(
                new AttachmentService.ContenidoDeAdjunto(
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                        "foto.jpg", "image/jpeg", AttachmentType.FOTO));

        mockMvc.perform(get("/api/v1/perfil/adjuntos/7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"foto.jpg\""));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("DELETE /perfil/adjuntos/{id} devuelve 204 sin cuerpo")
    void borrar_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/perfil/adjuntos/5")).andExpect(status().isNoContent());

        verify(attachmentService).borrar(eq(5L), any());
    }
}
