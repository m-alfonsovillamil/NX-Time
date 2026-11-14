package com.nxtime.nxtime.report;

import com.nxtime.nxtime.config.CondicionesDelServicio;
import com.nxtime.nxtime.config.Marca;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.springframework.stereotype.Component;

/**
 * Vuelca un {@link MonthlyReport} a una hoja de cálculo (Fase 10).
 *
 * Escribe DIRECTAMENTE en el OutputStream que se le pasa (el de la
 * respuesta HTTP), en vez de construir un byte[] y devolverlo: un
 * informe no tiene por qué caber dos veces en memoria.
 *
 * Las horas van como texto ("07:30"), no como número de Excel: el
 * formato de fecha/hora de Excel es un decimal desde 1900 y, según la
 * configuración regional de quien abra el fichero, se ve de una forma
 * o de otra. Para un documento que puede acabar en una inspección,
 * preferimos que ponga exactamente lo mismo en cualquier ordenador.
 *
 * <h2>El aspecto (noviembre de 2026)</h2>
 *
 * Es un documento de la casa, como el PDF: una banda con el color de la marca
 * para el título, el periodo sobre arena, filas alternas, la cabecera fija al
 * desplazarse y con filtro, y preparado para imprimir (apaisado, a lo ancho de
 * una hoja, con la cabecera repetida y «Página X de Y»).
 *
 * <b>Lo que no cambia son las posiciones:</b> el título sigue en la fila 0, el
 * periodo en la 1, las cabeceras en la 3 y los datos desde la 4, con las mismas
 * columnas. Quien lea este Excel por posición no nota nada.
 */
@Component
public class ExcelReportGenerator {

    private static final String[] CABECERAS = {
            "Empleado", "Fecha", "Entrada", "Salida", "Pausa (min)", "Tiempo efectivo", "Incidencia",
            // La última y no junto al nombre (ADR 053): quien ya leyera este
            // Excel por posición de columna no nota el cambio.
            "Código de empleado",
            // Igual, al final (ADR 055). Vacía si la empresa no la pregunta.
            "Modalidad",
            // Al final, por lo mismo (ADR 054): «Sí» si algún fichaje de la
            // jornada se hizo sin conexión y la hora la midió el móvil.
            "Sin conexión"
    };

    /**
     * Anchos de columna, en caracteres.
     *
     * A mano y no con {@code autoSizeColumn} porque SXSSF no puede medir filas
     * que ya ha mandado al disco: para autoajustar habría que retener todas en
     * memoria, que es justo lo que se está evitando. Están calculados sobre el
     * contenido real de cada columna -- un nombre completo, una fecha ISO, una
     * hora "HH:mm" -- y son de sobra.
     */
    private static final int[] ANCHOS = {28, 12, 10, 10, 12, 16, 36, 20, 14, 14};

    /** Las columnas que se leen mejor centradas: fechas, horas y síes. */
    private static final boolean[] CENTRADA = {false, true, true, true, true, true, false, true, true, true};

    private static final int COLUMNA_PAUSA = 4;
    private static final int COLUMNA_INCIDENCIA = 6;
    private static final int FILA_DE_CABECERAS = 3;
    private static final int ULTIMA_COLUMNA = CABECERAS.length - 1;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Filas que se quedan en memoria antes de ir volcándose al disco.
     *
     * Cien es la ventana habitual de SXSSF: suficiente para que escribir sea
     * secuencial y barato, y poco para que el libro no crezca con el número de
     * jornadas.
     */
    private static final int VENTANA_DE_FILAS = 100;

    private final CondicionesDelServicio servicio;

    public ExcelReportGenerator(CondicionesDelServicio servicio) {
        this.servicio = servicio;
    }

    public void generar(MonthlyReport informe, OutputStream salida) throws IOException {
        // SXSSF y no XSSF: XSSFWorkbook construye el libro ENTERO en memoria
        // antes de escribir el primer byte, lo que convertía en una
        // contradicción el StreamingResponseBody del controlador -- el
        // streaming era solo de cara al socket, mientras que dentro el informe
        // se materializaba dos veces (las entidades y luego el libro). Con
        // SXSSF solo conviven VENTANA_DE_FILAS filas.
        // El try-with-resources no es opcional aquí: SXSSF respalda las filas
        // que saca de memoria en ficheros temporales del disco, y close() es
        // lo que los borra (así lo documenta POI 5.5: "disposes of the
        // temporary files backing this workbook on disk"). Sin cerrarlo se
        // acumularían hasta llenar el disco efímero del contenedor. El
        // dispose() que hacía falta en versiones antiguas está deprecado.
        try (SXSSFWorkbook libro = new SXSSFWorkbook(VENTANA_DE_FILAS)) {
            // Los temporales van comprimidos: el disco de Render es efímero y
            // pequeño, y un informe anual de una empresa grande no es poca
            // cosa. No afecta al fichero que se entrega, solo a lo que SXSSF
            // deja en el disco mientras lo construye.
            libro.setCompressTempFiles(true);
            // Quién lo ha hecho y qué es, en las propiedades del fichero.
            var propiedades = libro.getXSSFWorkbook().getProperties().getCoreProperties();
            propiedades.setCreator(servicio.producto());
            propiedades.setTitle("Registro horario · " + informe.nombreEmpresa() + " · " + informe.mes());

            Sheet hoja = libro.createSheet("Horas " + informe.mes());
            for (int i = 0; i < ANCHOS.length; i++) {
                hoja.setColumnWidth(i, ANCHOS[i] * 256);
            }
            // Sin la cuadrícula de Excel: las filas ya llevan su raya.
            hoja.setDisplayGridlines(false);

            Estilos estilos = new Estilos(libro);

            int numeroFila = 0;
            numeroFila = escribirTitulo(hoja, informe, estilos, numeroFila);
            numeroFila = escribirCabeceras(hoja, estilos.cabecera, numeroFila);
            int primeraDeDatos = numeroFila;
            numeroFila = escribirFilas(hoja, informe, estilos, numeroFila);
            escribirTotales(hoja, informe, estilos.total, numeroFila);

            // La cabecera y el nombre se quedan a la vista al desplazarse, y
            // la cabecera filtra: es lo primero que hace quien abre el Excel.
            hoja.createFreezePane(1, primeraDeDatos);
            hoja.setAutoFilter(new CellRangeAddress(
                    FILA_DE_CABECERAS, Math.max(FILA_DE_CABECERAS, numeroFila - 1), 0, ULTIMA_COLUMNA));
            prepararImpresion(hoja, informe);

            libro.write(salida);
            // Vaciar lo que quede en el búfer antes de dar por escrito el
            // fichero. El generador de PDF cierra su documento y con él el
            // flujo, así que sus descargas terminaban bien; esta no, y si algo
            // aborta la petición después, lo último del libro no llega nunca.
            salida.flush();
        }
    }

    /**
     * La banda del título, con el color de la marca, y debajo el periodo sobre
     * arena. Las dos van combinadas a lo ancho de la tabla.
     */
    private int escribirTitulo(Sheet hoja, MonthlyReport informe, Estilos estilos, int numeroFila) {
        Row fila = hoja.createRow(numeroFila);
        fila.setHeightInPoints(30);
        banda(hoja, fila, estilos.titulo, "Registro horario - " + informe.nombreEmpresa());
        numeroFila++;

        Row filaMes = hoja.createRow(numeroFila);
        filaMes.setHeightInPoints(20);
        banda(hoja, filaMes, estilos.periodo, "Periodo: " + informe.mes()
                + "   ·   Generado el " + LocalDate.now(informe.zona()).format(FECHA)
                + " con " + servicio.producto());
        numeroFila++;

        hoja.createRow(numeroFila++); // línea en blanco
        return numeroFila;
    }

    /** Una fila que es una sola celda a lo ancho: el estilo en todas, para que el relleno no se corte. */
    private void banda(Sheet hoja, Row fila, CellStyle estilo, String texto) {
        for (int i = 0; i <= ULTIMA_COLUMNA; i++) {
            fila.createCell(i).setCellStyle(estilo);
        }
        fila.getCell(0).setCellValue(texto);
        hoja.addMergedRegion(new CellRangeAddress(fila.getRowNum(), fila.getRowNum(), 0, ULTIMA_COLUMNA));
    }

    private int escribirCabeceras(Sheet hoja, CellStyle estilo, int numeroFila) {
        Row fila = hoja.createRow(numeroFila++);
        fila.setHeightInPoints(22);
        for (int i = 0; i < CABECERAS.length; i++) {
            Cell celda = fila.createCell(i);
            celda.setCellValue(CABECERAS[i]);
            celda.setCellStyle(estilo);
        }
        return numeroFila;
    }

    private int escribirFilas(Sheet hoja, MonthlyReport informe, Estilos estilos, int numeroFila) {
        int numero = 0;
        for (ReportRow linea : informe.filas()) {
            // Filas alternas, como en el PDF: un mes de una empresa son cientos
            // de filas, y sin ellas la vista se salta de una a otra.
            EstilosDeFila estilo = numero++ % 2 == 1 ? estilos.alterna : estilos.normal;
            Row fila = hoja.createRow(numeroFila++);
            texto(fila, 0, linea.nombreEmpleado(), estilo);
            texto(fila, 1, linea.fecha().toString(), estilo);
            texto(fila, 2, linea.horaEntrada().toString(), estilo);
            texto(fila, 3, linea.horaSalida().toString(), estilo);
            // El valor exacto, en minutos con decimales, y a la vista sin
            // ellos: así, quien sume la columna obtiene el total real. Antes
            // iban los minutos truncados y se perdía hasta un minuto por fila.
            Cell pausa = fila.createCell(COLUMNA_PAUSA);
            pausa.setCellValue(linea.segundosPausa() / 60.0);
            pausa.setCellStyle(estilo.minutos);
            texto(fila, 5, linea.duracionLegible(), estilo);
            // Se marcan las jornadas que cerró el sistema (Fase 9) y las que
            // llegaron sin conexión pasadas 72 h y nadie ha revisado (ADR
            // 054): son menos fiables que el resto y hay que poder distinguirlas.
            texto(fila, COLUMNA_INCIDENCIA, linea.textoDeIncidencia(), estilo);
            // Como texto: «000123» como número perdería los ceros.
            texto(fila, 7, linea.codigoEmpleado() == null ? "" : linea.codigoEmpleado(), estilo);
            texto(fila, 8, com.nxtime.nxtime.domain.Modalidad.texto(linea.modalidad()), estilo);
            texto(fila, 9, linea.sinConexion() ? "Sí" : "", estilo);
        }
        return numeroFila;
    }

    private void texto(Row fila, int columna, String valor, EstilosDeFila estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        celda.setCellStyle(columna == COLUMNA_INCIDENCIA ? estilo.incidencia
                : CENTRADA[columna] ? estilo.centrado : estilo.texto);
    }

    private void escribirTotales(Sheet hoja, MonthlyReport informe, CellStyle estilo, int numeroFila) {
        hoja.createRow(numeroFila++); // línea en blanco

        Row fila = hoja.createRow(numeroFila);
        fila.setHeightInPoints(20);
        // El estilo en toda la fila: el total es una banda, no dos celdas sueltas.
        for (int i = 0; i <= ULTIMA_COLUMNA; i++) {
            fila.createCell(i).setCellStyle(estilo);
        }
        fila.getCell(0).setCellValue("TOTAL (" + informe.diasTrabajados() + " días trabajados)");
        fila.getCell(5).setCellValue(informe.totalLegible());
    }

    /**
     * Apaisado, a lo ancho de una hoja y con la cabecera repetida: un mes de una
     * plantilla entera son varias páginas, y sin esto salía cortado por la
     * derecha y sin saber qué era cada columna a partir de la segunda.
     */
    private void prepararImpresion(Sheet hoja, MonthlyReport informe) {
        PrintSetup impresion = hoja.getPrintSetup();
        impresion.setLandscape(true);
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        hoja.setFitToPage(true);
        impresion.setFitWidth((short) 1);
        impresion.setFitHeight((short) 0);
        hoja.setRepeatingRows(new CellRangeAddress(FILA_DE_CABECERAS, FILA_DE_CABECERAS, 0, ULTIMA_COLUMNA));
        // &P y &N son los códigos de Excel para la página y el total.
        hoja.getHeader().setLeft(servicio.producto() + " · Registro horario");
        hoja.getHeader().setRight(informe.nombreEmpresa() + " · " + informe.mes());
        hoja.getFooter().setCenter("Página &P de &N");
    }

    /** Los estilos del libro. Se crean una vez: Excel admite un número limitado. */
    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle periodo;
        final CellStyle cabecera;
        final CellStyle total;
        final EstilosDeFila normal;
        final EstilosDeFila alterna;

        Estilos(Workbook libro) {
            titulo = estiloTitulo(libro);
            periodo = estiloPeriodo(libro);
            cabecera = estiloCabecera(libro);
            total = estiloTotal(libro);
            normal = new EstilosDeFila(libro, null);
            alterna = new EstilosDeFila(libro, Marca.FONDO);
        }
    }

    /** Los cuatro estilos de una fila de datos, con su fondo o sin él. */
    private static final class EstilosDeFila {
        final CellStyle texto;
        final CellStyle centrado;
        final CellStyle minutos;
        final CellStyle incidencia;

        EstilosDeFila(Workbook libro, String fondo) {
            // Una fuente creada aquí, como la de la cabecera: con la del estilo
            // por defecto, Excel pintaba los datos con otra letra que los títulos.
            Font tinta = libro.createFont();
            colorear(tinta, Marca.TEXTO);
            texto = celda(libro, fondo, tinta);
            centrado = celda(libro, fondo, tinta);
            centrado.setAlignment(HorizontalAlignment.CENTER);
            minutos = celda(libro, fondo, tinta);
            minutos.setAlignment(HorizontalAlignment.CENTER);
            minutos.setDataFormat(libro.createDataFormat().getFormat("0"));
            // En el rojo de los avisos, como las jornadas marcadas del PDF.
            Font roja = libro.createFont();
            colorear(roja, Marca.ERROR);
            incidencia = celda(libro, fondo, roja);
        }

        private static CellStyle celda(Workbook libro, String fondo, Font fuente) {
            CellStyle estilo = libro.createCellStyle();
            estilo.setFont(fuente);
            estilo.setVerticalAlignment(VerticalAlignment.CENTER);
            estilo.setBorderBottom(BorderStyle.THIN);
            if (estilo instanceof XSSFCellStyle xssf) {
                xssf.setBottomBorderColor(color(Marca.BORDE));
            }
            if (fondo != null) {
                rellenar(estilo, fondo);
            }
            return estilo;
        }
    }

    /** El título: blanco y grande sobre el primario de la marca. */
    private static CellStyle estiloTitulo(Workbook libro) {
        Font fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 14);
        colorear(fuente, Marca.SOBRE_PRIMARIO);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        rellenar(estilo, Marca.PRIMARIO);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setIndention((short) 1);
        return estilo;
    }

    /** El periodo, en pequeño sobre el arena del fondo de la marca. */
    private static CellStyle estiloPeriodo(Workbook libro) {
        Font fuente = libro.createFont();
        colorear(fuente, Marca.TEXTO_SECUNDARIO);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        rellenar(estilo, Marca.FONDO);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setIndention((short) 1);
        return estilo;
    }

    /**
     * La cabecera con el primario de la marca y letra blanca, como las tablas
     * de la app y del PDF. Hasta noviembre de 2026 era gris. Un color propio y
     * no uno de la paleta indexada de Excel: ahí no está el de la marca.
     */
    private static CellStyle estiloCabecera(Workbook libro) {
        Font negrita = libro.createFont();
        negrita.setBold(true);
        colorear(negrita, Marca.SOBRE_PRIMARIO);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        rellenar(estilo, Marca.PRIMARIO);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        return estilo;
    }

    /** El total, en el contenedor claro de la marca, como en el PDF. */
    private static CellStyle estiloTotal(Workbook libro) {
        Font negrita = libro.createFont();
        negrita.setBold(true);
        colorear(negrita, Marca.SOBRE_CONTENEDOR_PRIMARIO);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        rellenar(estilo, Marca.CONTENEDOR_PRIMARIO);
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        return estilo;
    }

    private static void rellenar(CellStyle estilo, String hex) {
        estilo.setFillForegroundColor(color(hex));
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    /**
     * Con el alfa delante (FF, opaco): el estándar pide ARGB de ocho cifras, y
     * con solo las seis del RGB algunas versiones de Excel pintan el relleno
     * transparente.
     */
    private static XSSFColor color(String hex) {
        byte[] rgb = Marca.rgb(hex);
        return new XSSFColor(new byte[] {(byte) 0xFF, rgb[0], rgb[1], rgb[2]}, null);
    }

    /** SXSSF crea fuentes de XSSF por debajo: son las que admiten un color RGB. */
    private static void colorear(Font fuente, String hex) {
        if (fuente instanceof XSSFFont xssf) {
            xssf.setColor(color(hex));
        }
    }
}
