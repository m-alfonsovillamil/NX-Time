package com.nxtime.nxtime.domain;

/**
 * De dónde viene un festivo (Fase C). Columna "festivos.ambito".
 *
 * No es una etiqueta decorativa: decide <b>quién puede tocarlo</b>.
 * {@link #NACIONAL} es la única fila compartida por todas las empresas
 * (es la que se guarda con {@code empresa == null}, ver {@link
 * Holiday}), así que la genera el sistema y ningún gestor la edita ni
 * la borra por API -- borrar "Navidad" desde una empresa se la quitaría
 * a todas las demás. Los otros tres ámbitos son filas de UNA empresa y
 * son los que se gestionan desde la aplicación.
 *
 * La distinción entre {@link #AUTONOMICO}, {@link #LOCAL} y {@link
 * #EMPRESA} no cambia ningún cálculo -- los tres son días no hábiles
 * igual -- pero sí cambia lo que se puede explicar en pantalla: "12 de
 * octubre, nacional" y "15 de mayo, local" son dos cosas distintas para
 * quien mira su calendario, y agruparlas como "festivo de empresa"
 * perdería el único dato que hace falta para revisarlas cuando cambia
 * el año.
 */
public enum HolidayScope {

    /** Fiesta estatal. Fila única, sin empresa, sembrada por el sistema. */
    NACIONAL,

    /** Fiesta de la comunidad autónoma. La añade el gestor. */
    AUTONOMICO,

    /** Fiesta del municipio del centro de trabajo. La añade el gestor. */
    LOCAL,

    /** Día de convenio o cierre propio de la empresa. */
    EMPRESA;

    /**
     * Si este ámbito pertenece a una empresa concreta, y por tanto se
     * puede crear, editar y borrar desde la aplicación.
     *
     * Es el mismo predicado que impone {@code ck_festivos_ambito_coherente}
     * en la base de datos; tenerlo aquí evita repetir el
     * {@code != NACIONAL} en el servicio, el validador y los tests, que
     * es justo donde una de las tres copias se olvidaría de cambiar.
     */
    public boolean esDeEmpresa() {
        return this != NACIONAL;
    }
}
