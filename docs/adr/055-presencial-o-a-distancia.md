# 55. Presencial o a distancia

**Estado:** aceptada · **Fecha:** octubre 2026 · Junto al [ADR 054](054-fichar-sin-conexion.md)

## Contexto

Logroot, el sistema que usa hoy IIDD (la primera empresa real, plan K),
registra en cada fichaje si se trabaja en el centro o a distancia. Desde la Ley
10/2021 de trabajo a distancia a una empresa le interesa poder enseñarlo, y en
el registro de jornada es donde se ve día a día. NX Time no lo tenía.

## Decisión

- **Opcional por empresa.** `empresas.preguntar_modalidad` (V51), apagado por
  defecto. Lo enciende un ADMIN en Ajustes de empresa, en su propia tarjeta,
  como el segundo factor. En la API, null es «no tocar»: un cliente que no lo
  conozca no lo apaga al guardar los ajustes.
- **Se elige al entrar.** `TimeEntryRequest.modalidad` (PRESENCIAL o
  A_DISTANCIA), solo en INICIO. `GET /fichaje/hoy`, que la web y la app ya
  piden antes de empezar, dice si hay que preguntarla (`preguntarModalidad`).
  La web la pregunta con dos botones y ofrece primero la última elegida en ese
  navegador. **No es obligatoria aunque la empresa la pida:** una app anterior
  tiene que poder fichar, y fichar es lo único que nunca puede quedar
  bloqueado. Esa jornada queda sin modalidad.
- **Si la empresa no la pregunta, no se guarda** aunque la mande el cliente.
- **En un kiosco es presencial**, por definición: está en el centro.
- **Se corrige como las horas.** `CorrectionRequestDTO.modalidad` (null = no se
  toca) y `solicitudes_correccion.modalidad_propuesta`. La corrección la
  hereda, y si propone otra, pone la propuesta. En la web solo se ofrece en
  jornadas que ya la tienen: es la señal de que la empresa la pregunta.
- **Dónde se ve:**
  - en los historiales, con la insignia «A distancia» (presencial no se marca:
    es lo de siempre);
  - en el Excel de horas, como columna al final, como el código de empleado
    (ADR 053);
  - en el PDF mensual, como columna, solo si alguna jornada del mes la tiene;
  - en «Mis datos»;
  - en la instantánea de auditoría.
- **No entra en la huella del mes** (`HuellaDelMes`, versión 1): cambiarla no
  invalida una firma. Lo firmado son las horas.

## Consecuencias

- Con el ajuste encendido, cada entrada pide un clic más.
- Los textos legales que listan los datos tratados la recogen en el ADR 056,
  junto con la ubicación, en la misma versión nueva de las condiciones.
