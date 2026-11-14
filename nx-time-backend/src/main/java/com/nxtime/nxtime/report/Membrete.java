package com.nxtime.nxtime.report;

import com.nxtime.nxtime.config.Marca;
import java.awt.Color;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;

/**
 * El membrete de los PDF que entrega la aplicación: lo que hace que el registro
 * mensual y «Tus datos» se reconozcan como documentos de la misma casa.
 *
 * En <b>cada página</b>:
 * <ul>
 *   <li>arriba, una franja del color de la marca, el logotipo con el nombre del
 *       producto y, a la derecha, qué documento es y de qué empresa;</li>
 *   <li>abajo, el mismo dato en pequeño y «Página X de Y».</li>
 * </ul>
 *
 * Va como evento de página y no como párrafos del documento: así sale también
 * en la segunda hoja de un mes largo, que antes no decía de quién era.
 *
 * El «de Y» se escribe al cerrar el documento, que es cuando se sabe: mientras
 * tanto se reserva un hueco ({@link PdfTemplate}) en cada pie.
 *
 * El logotipo se dibuja con trazos, igual que el icono de la app y de la web
 * (`ic_marca_*.xml`, `iconos/icono.svg`): el backend no tiene la imagen, y un
 * vector no se pixela al imprimir. <b>Si cambia la marca, cambia aquí también.</b>
 */
final class Membrete extends PdfPageEventHelper {

    /** Márgenes del documento que dejan sitio al membrete y al pie. */
    static final float MARGEN_LADO = 40;
    static final float MARGEN_ARRIBA = 72;
    static final float MARGEN_ABAJO = 50;

    private static final float ALTO_FRANJA = 6;
    private static final float LADO_LOGO = 26;
    /** El hueco del «de Y»: caben tres cifras. */
    private static final float ANCHO_TOTAL = 14;
    private static final float ESPACIO = 2.5f;

    private static final Color PRIMARIO = Marca.awt(Marca.PRIMARIO);
    private static final Color BORDE = Marca.awt(Marca.BORDE);

    private static final Font PRODUCTO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, PRIMARIO);
    private static final Font DOCUMENTO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Marca.awt(Marca.TEXTO));
    private static final Font EMPRESA =
            FontFactory.getFont(FontFactory.HELVETICA, 9, Marca.awt(Marca.TEXTO_SECUNDARIO));
    private static final Font PIE = FontFactory.getFont(FontFactory.HELVETICA, 8, Marca.awt(Marca.TEXTO_SECUNDARIO));

    private final String producto;
    private final String documento;
    private final String empresa;
    private PdfTemplate totalDePaginas;

    /**
     * @param producto  el nombre del servicio («NX Time»)
     * @param documento qué documento es («Registro horario mensual»)
     * @param empresa   de qué empresa; puede venir vacío
     */
    Membrete(String producto, String documento, String empresa) {
        this.producto = producto;
        this.documento = documento;
        this.empresa = empresa == null ? "" : empresa;
    }

    /** Un A4 con los márgenes que necesita el membrete. */
    static Document documentoA4() {
        return new Document(org.openpdf.text.PageSize.A4, MARGEN_LADO, MARGEN_LADO, MARGEN_ARRIBA, MARGEN_ABAJO);
    }

    @Override
    public void onOpenDocument(PdfWriter escritor, Document documentoPdf) {
        totalDePaginas = escritor.getDirectContent().createTemplate(ANCHO_TOTAL, 10);
        documentoPdf.addTitle(empresa.isEmpty() ? documento : documento + " · " + empresa);
        documentoPdf.addCreator(producto);
    }

    @Override
    public void onEndPage(PdfWriter escritor, Document documentoPdf) {
        PdfContentByte lienzo = escritor.getDirectContent();
        Rectangle pagina = documentoPdf.getPageSize();
        float izquierda = documentoPdf.left();
        float derecha = documentoPdf.right();

        // La franja, a sangre: de borde a borde del papel.
        lienzo.saveState();
        lienzo.setColorFill(PRIMARIO);
        lienzo.rectangle(0, pagina.getTop() - ALTO_FRANJA, pagina.getWidth(), ALTO_FRANJA);
        lienzo.fill();
        lienzo.restoreState();

        float baseDelLogo = pagina.getTop() - ALTO_FRANJA - 14 - LADO_LOGO;
        logo(lienzo, izquierda, baseDelLogo, LADO_LOGO);
        ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT, new Phrase(producto, PRODUCTO),
                izquierda + LADO_LOGO + 9, baseDelLogo + 8, 0);
        ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT, new Phrase(documento, DOCUMENTO),
                derecha, baseDelLogo + 15, 0);
        ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT, new Phrase(empresa, EMPRESA),
                derecha, baseDelLogo + 3, 0);
        raya(lienzo, izquierda, derecha, baseDelLogo - 9);

        // El pie.
        float basePie = documentoPdf.bottom() - 26;
        raya(lienzo, izquierda, derecha, basePie + 12);
        String queEs = empresa.isEmpty() ? producto + " · " + documento : producto + " · " + documento + " · " + empresa;
        ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT, new Phrase(queEs, PIE), izquierda, basePie, 0);
        ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT,
                new Phrase("Página " + escritor.getPageNumber() + " de", PIE),
                derecha - ANCHO_TOTAL - ESPACIO, basePie, 0);
        lienzo.addTemplate(totalDePaginas, derecha - ANCHO_TOTAL, basePie);
    }

    @Override
    public void onCloseDocument(PdfWriter escritor, Document documentoPdf) {
        // Al cerrar, el contador ya va una página por delante de la última.
        ColumnText.showTextAligned(totalDePaginas, Element.ALIGN_LEFT,
                new Phrase(String.valueOf(escritor.getPageNumber() - 1), PIE), 0, 0, 0);
    }

    private static void raya(PdfContentByte lienzo, float desde, float hasta, float y) {
        lienzo.saveState();
        lienzo.setColorStroke(BORDE);
        lienzo.setLineWidth(0.8f);
        lienzo.moveTo(desde, y);
        lienzo.lineTo(hasta, y);
        lienzo.stroke();
        lienzo.restoreState();
    }

    /**
     * La N trazada como una línea de tiempo, con el punto del «ahora», sobre un
     * cuadrado de esquinas redondas. Las medidas son las del icono (lienzo de
     * 108, con el eje vertical hacia abajo; en un PDF va hacia arriba).
     */
    static void logo(PdfContentByte lienzo, float x, float y, float lado) {
        float e = lado / 108f;
        lienzo.saveState();
        lienzo.setColorFill(PRIMARIO);
        lienzo.roundRectangle(x, y, lado, lado, 24 * e);
        lienzo.fill();

        lienzo.setColorStroke(Color.WHITE);
        lienzo.setLineWidth(11 * e);
        lienzo.setLineCap(PdfContentByte.LINE_CAP_ROUND);
        lienzo.setLineJoin(PdfContentByte.LINE_JOIN_ROUND);
        lienzo.moveTo(x + 37 * e, y + (108 - 71) * e);
        lienzo.lineTo(x + 37 * e, y + (108 - 37) * e);
        lienzo.lineTo(x + 71 * e, y + (108 - 71) * e);
        lienzo.lineTo(x + 71 * e, y + (108 - 37) * e);
        lienzo.stroke();

        lienzo.setColorFill(Color.WHITE);
        lienzo.circle(x + 71 * e, y + (108 - 37) * e, 7 * e);
        lienzo.fill();
        lienzo.restoreState();
    }
}
