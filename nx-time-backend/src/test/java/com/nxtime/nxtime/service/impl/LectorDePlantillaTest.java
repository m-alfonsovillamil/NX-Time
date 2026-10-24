package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Leer el fichero de una plantilla tal como sale de Excel (ADR 052). */
class LectorDePlantillaTest {

    private static LectorDePlantilla.Leido csv(String texto, Charset juego) {
        return LectorDePlantilla.leer("plantilla.csv", texto.getBytes(juego));
    }

    @Test
    @DisplayName("El CSV del Excel en español: punto y coma y Windows-1252, con las tildes y las eñes bien")
    void csvDelExcelEnEspanol() {
        LectorDePlantilla.Leido leido = csv("Nombre;Apellidos;Correo electrónico;Departamento\r\n"
                + "Iñaki;Peña Gómez;inaki@empresa.test;Logística\r\n", Charset.forName("windows-1252"));

        assertThat(leido.filas()).hasSize(1);
        LectorDePlantilla.Fila fila = leido.filas().get(0);
        assertThat(fila.numero()).isEqualTo(2);
        assertThat(fila.valor(LectorDePlantilla.NOMBRE)).isEqualTo("Iñaki");
        assertThat(fila.valor(LectorDePlantilla.APELLIDOS)).isEqualTo("Peña Gómez");
        assertThat(fila.valor(LectorDePlantilla.EMAIL)).isEqualTo("inaki@empresa.test");
        assertThat(fila.valor(LectorDePlantilla.DEPARTAMENTO)).isEqualTo("Logística");
    }

    @Test
    @DisplayName("UTF-8 con BOM, separado por comas, con comillas que llevan comas y comillas dentro")
    void utf8ConBomYComillas() {
        LectorDePlantilla.Leido leido = csv("﻿nombre,apellidos,email,jornada\n"
                + "\"Ana, la de RRHH\",\"López \"\"Peque\"\"\",ana@empresa.test,\"37,5\"\n", StandardCharsets.UTF_8);

        LectorDePlantilla.Fila fila = leido.filas().get(0);
        assertThat(fila.valor(LectorDePlantilla.NOMBRE)).isEqualTo("Ana, la de RRHH");
        assertThat(fila.valor(LectorDePlantilla.APELLIDOS)).isEqualTo("López \"Peque\"");
        assertThat(fila.valor(LectorDePlantilla.HORAS)).isEqualTo("37,5");
    }

    @Test
    @DisplayName("Las columnas que no conoce se ignoran y se dicen; las filas vacías se saltan")
    void columnasIgnoradasYFilasVacias() {
        LectorDePlantilla.Leido leido = csv("nombre;apellidos;email;DNI\n;;;\nAna;López;ana@empresa.test;123\n\n",
                StandardCharsets.UTF_8);

        assertThat(leido.columnasIgnoradas()).containsExactly("DNI");
        assertThat(leido.filas()).hasSize(1);
    }

    @Test
    @DisplayName("Sin las columnas obligatorias, o sin nadie, no se puede")
    void columnasObligatorias() {
        assertThatThrownBy(() -> csv("nombre;email\nAna;ana@empresa.test\n", StandardCharsets.UTF_8))
                .isInstanceOf(BusinessException.class).hasMessageContaining("apellidos");
        assertThatThrownBy(() -> csv("nombre;apellidos;email\n", StandardCharsets.UTF_8))
                .isInstanceOf(BusinessException.class).hasMessageContaining("solo tiene la fila de títulos");
        assertThatThrownBy(() -> LectorDePlantilla.leer("x.csv", new byte[0]))
                .isInstanceOf(BusinessException.class).hasMessageContaining("vacío");
    }

    @Test
    @DisplayName("Como mucho 500 personas por fichero")
    void maximoDeFilas() {
        StringBuilder texto = new StringBuilder("nombre;apellidos;email\n");
        for (int i = 0; i < 501; i++) {
            texto.append("A;B;p").append(i).append("@empresa.test\n");
        }
        assertThatThrownBy(() -> csv(texto.toString(), StandardCharsets.UTF_8))
                .isInstanceOf(BusinessException.class).hasMessageContaining("501");
    }

    @Test
    @DisplayName("Un Excel (.xlsx): la primera hoja, con números y textos")
    void excel() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Plantilla");
            Row titulos = hoja.createRow(0);
            String[] cabecera = {"Nombre", "Apellidos", "Email", "Horas semanales", "Vacaciones"};
            for (int i = 0; i < cabecera.length; i++) {
                titulos.createCell(i).setCellValue(cabecera[i]);
            }
            Row fila = hoja.createRow(1);
            fila.createCell(0).setCellValue("Luis");
            fila.createCell(1).setCellValue("Martín");
            fila.createCell(2).setCellValue("luis@empresa.test");
            fila.createCell(3).setCellValue(40);
            fila.createCell(4).setCellValue(23);
            libro.write(salida);
        }

        LectorDePlantilla.Leido leido = LectorDePlantilla.leer("Plantilla.XLSX", salida.toByteArray());

        LectorDePlantilla.Fila fila = leido.filas().get(0);
        assertThat(fila.valor(LectorDePlantilla.NOMBRE)).isEqualTo("Luis");
        assertThat(fila.valor(LectorDePlantilla.HORAS)).isEqualTo("40");
        assertThat(fila.valor(LectorDePlantilla.VACACIONES)).isEqualTo("23");
    }

    @Test
    @DisplayName("Un fichero que dice ser Excel y no lo es se rechaza con un mensaje, no con un error 500")
    void excelRoto() {
        assertThatThrownBy(() -> LectorDePlantilla.leer("plantilla.xlsx", "no soy un excel".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Excel");
    }
}
