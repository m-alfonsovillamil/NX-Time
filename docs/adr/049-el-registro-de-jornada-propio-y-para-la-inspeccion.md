# 49. El registro de jornada: el propio, y todo junto para la Inspección

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

El art. 34.9 del Estatuto de los Trabajadores obliga a la empresa a conservar el
registro cuatro años y a tenerlo a disposición de las personas trabajadoras, de
sus representantes y de la Inspección de Trabajo. Hasta el plan J:

- **El PDF mensual oficial solo lo descargaba RRHH o ADMIN** (`informe:exportar`).
  Un trabajador veía sus fichajes, pero el documento tenía que pedirlo.
- **Para una inspección había que ir mes a mes y persona a persona**, y la traza
  de correcciones y las firmas no salían de la aplicación.

El real decreto de registro digital no está publicado (comprobado en el BOE el
17/10/2026), así que no se adelanta nada de su borrador: el texto puede cambiar.

## Decisión

- **`GET /api/v1/informes/mensual/mio`**, con `fichaje:leer`: el mismo PDF que
  descarga RRHH, de la propia persona. El id sale del token, no de la URL. En la
  web y en la app, un botón en cada mes de «Firma mensual». No se anota en el
  registro de accesos (ADR 047): son datos propios.
- **`GET /api/v1/informes/inspeccion.zip?desde=AAAA-MM&hasta=AAAA-MM`**, con
  `informe:exportar`, de hasta doce meses. Lleva el Excel de cada mes, el PDF de
  cada persona que fichó, `correcciones.csv` (cada movimiento de la traza de
  auditoría con quién lo hizo y por qué), `firmas.csv` y un `LEEME.txt`.
  - **Se prepara entero antes de escribir**: una vez empezado el ZIP ya se ha
    mandado el 200, y un fallo a medias no se podría explicar.
  - **Una sola anotación en el registro de accesos** por exportación, no una por
    persona y mes: `informesDeLaPlantilla` no anota, lo hace quien exporta.
  - **Sin la IP de cada fichaje**: no la pide el ET y la Inspección la puede
    pedir aparte (minimización).
  - Dos personas con el mismo nombre no se pisan dentro del ZIP (un ZIP no
    admite entradas repetidas y fallaría entero).

## Lo que queda fuera: la baja de una empresa con borrado

Las condiciones (punto 9) dicen que, cuando una empresa se va, se le entregan
sus datos (este ZIP sirve para eso) y se borran a los 30 días. **El borrado no
se hace aquí**, y el motivo es técnico: la cadena de huellas de la auditoría es
**una sola para todas las empresas** (cada fila enlaza con la anterior, sea de
quien sea; ADR 018). Borrar las filas de una empresa rompería la verificación de
todas. Las salidas posibles —una cadena por empresa, o anonimizar en vez de
borrar— se deciden aparte. **Decidido el mismo día: una cadena por empresa, en el [ADR 050](050-una-cadena-de-auditoria-por-empresa-y-la-baja.md).**
