package com.nxtime.nxtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * En qué categoría cae cada tipo de aviso (ADR 059). Escrito tipo a tipo: si
 * alguien añade uno, este test le obliga a decidir dónde va, en vez de que caiga
 * en obligatorios sin que nadie lo haya pensado.
 */
class CategoriaDeAvisoTest {

    @Test
    @DisplayName("Cada tipo de aviso, en su categoría")
    void cadaTipoEnSuCategoria() {
        Map<NoticeType, CategoriaDeAviso> esperado = new EnumMap<>(NoticeType.class);
        esperado.put(NoticeType.AUSENCIA_SOLICITADA, CategoriaDeAviso.AUSENCIAS);
        esperado.put(NoticeType.AUSENCIA_RESUELTA, CategoriaDeAviso.AUSENCIAS);
        esperado.put(NoticeType.BIENVENIDA, CategoriaDeAviso.OBLIGATORIOS);
        esperado.put(NoticeType.CORRECCION_SOLICITADA, CategoriaDeAviso.CORRECCIONES);
        esperado.put(NoticeType.CORRECCION_RESUELTA, CategoriaDeAviso.CORRECCIONES);
        esperado.put(NoticeType.CORRECCION_EN_DISPUTA, CategoriaDeAviso.CORRECCIONES);
        esperado.put(NoticeType.HORAS_EXTRA_DETECTADAS, CategoriaDeAviso.HORAS_EXTRA);
        esperado.put(NoticeType.BOLSA_HORAS_EXTRA_AL_LIMITE, CategoriaDeAviso.HORAS_EXTRA);
        esperado.put(NoticeType.RESUMEN_HORAS_EXTRA, CategoriaDeAviso.HORAS_EXTRA);
        esperado.put(NoticeType.DENUNCIA_RECIBIDA, CategoriaDeAviso.OBLIGATORIOS);
        esperado.put(NoticeType.DENUNCIA_ACTUALIZADA, CategoriaDeAviso.OBLIGATORIOS);
        esperado.put(NoticeType.OFERTA_PUBLICADA, CategoriaDeAviso.OFERTAS);
        esperado.put(NoticeType.CANDIDATURA_RECIBIDA, CategoriaDeAviso.OFERTAS);
        esperado.put(NoticeType.CANDIDATURA_ACTUALIZADA, CategoriaDeAviso.OFERTAS);
        esperado.put(NoticeType.BORRADO_SOLICITADO, CategoriaDeAviso.OBLIGATORIOS);
        esperado.put(NoticeType.BORRADO_RECHAZADO, CategoriaDeAviso.OBLIGATORIOS);
        esperado.put(NoticeType.TRABAJO_EN_DIA_NO_LABORABLE, CategoriaDeAviso.EQUIPO);
        esperado.put(NoticeType.CUADRANTE_DISTINTO_DE_JORNADA, CategoriaDeAviso.EQUIPO);
        esperado.put(NoticeType.INCIDENCIA_DETECTADA, CategoriaDeAviso.INCIDENCIAS);
        esperado.put(NoticeType.RESUMEN_INCIDENCIAS, CategoriaDeAviso.INCIDENCIAS);
        esperado.put(NoticeType.RECORDATORIO_FIRMA, CategoriaDeAviso.OBLIGATORIOS);
        esperado.put(NoticeType.FIRMA_INVALIDADA, CategoriaDeAviso.OBLIGATORIOS);
        esperado.put(NoticeType.PIN_KIOSCO_ANULADO, CategoriaDeAviso.OBLIGATORIOS);
        esperado.put(NoticeType.PIN_KIOSCO_ANULADO_EQUIPO, CategoriaDeAviso.OBLIGATORIOS);
        // AUSENCIA_CANCELADA (ADR 058) cae en AUSENCIAS por su prefijo.
        for (NoticeType tipo : NoticeType.values()) {
            CategoriaDeAviso categoria = CategoriaDeAviso.de(tipo);
            if (tipo.name().startsWith("AUSENCIA_")) {
                assertThat(categoria).as(tipo.name()).isEqualTo(CategoriaDeAviso.AUSENCIAS);
            } else {
                assertThat(esperado).as("falta decidir la categoría de %s", tipo).containsKey(tipo);
                assertThat(categoria).as(tipo.name()).isEqualTo(esperado.get(tipo));
            }
        }
    }

    @Test
    @DisplayName("Solo las obligatorias no se pueden apagar")
    void soloLasObligatoriasNoSeApagan() {
        for (CategoriaDeAviso categoria : CategoriaDeAviso.values()) {
            assertThat(categoria.configurable()).isEqualTo(categoria != CategoriaDeAviso.OBLIGATORIOS);
        }
    }
}
