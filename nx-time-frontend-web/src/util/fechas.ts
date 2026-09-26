/**
 * Todo el formateo de fechas de la web, en un solo sitio.
 *
 * Espeja `DateFormats.kt`, incluida la decisión que más importa: **la zona es
 * fija y española**. El backend guarda instantes en UTC (`Instant`,
 * `TIMESTAMPTZ`) y la jornada laboral que representan es la del centro de
 * trabajo, no la del sitio donde esté abierto el navegador. Alguien de viaje
 * tiene que seguir viendo su jornada en la hora de su empresa, y en un
 * navegador eso no es una hipótesis: la zona del sistema viaja con el portátil.
 *
 * Por eso no se usa `toLocaleTimeString()` a secas en ningún componente. Ese
 * es exactamente el fallo que se cuela solo: funciona en el portátil de quien
 * lo escribe y enseña otra hora en el de al lado.
 */

const ZONA_ESPANA = 'Europe/Madrid';
const ES = 'es-ES';

const HORA = new Intl.DateTimeFormat(ES, {
  timeZone: ZONA_ESPANA,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const FECHA_LARGA = new Intl.DateTimeFormat(ES, {
  timeZone: ZONA_ESPANA,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

const FECHA_HORA_CORTA = new Intl.DateTimeFormat(ES, {
  timeZone: ZONA_ESPANA,
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const FECHA_CORTA = new Intl.DateTimeFormat(ES, {
  // Un `LocalDate` es un día, no un instante (ADR 002). Se construye como
  // medianoche UTC y se formatea en UTC para que ninguna zona lo mueva: es
  // la única forma de que el 21 sea el 21 lo lea quien lo lea.
  timeZone: 'UTC',
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

/** `2026-09-21T07:03:11Z` → `09:03 h`, siempre en hora de España. */
export function hora(instante: string | null | undefined): string {
  const fecha = aFecha(instante);
  return fecha === null ? '' : `${HORA.format(fecha)} h`;
}

/** `lunes, 21 de septiembre`, con la inicial en mayúscula. */
export function fechaLarga(instante: Date = new Date()): string {
  const texto = FECHA_LARGA.format(instante);
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** `2026-09-21T07:03:11Z` → `21 sept, 09:03`: para listas de avisos o de cambios. */
export function fechaHoraCorta(instante: string | null | undefined): string {
  const fecha = aFecha(instante);
  return fecha === null ? '' : FECHA_HORA_CORTA.format(fecha);
}

/**
 * Un día sin hora, como los manda la API para ausencias y festivos:
 * `2026-09-21` → `lun, 21 sept`.
 */
export function fechaCorta(dia: string | null | undefined): string {
  if (!dia || !/^\d{4}-\d{2}-\d{2}$/.test(dia)) return '';
  const fecha = new Date(`${dia}T00:00:00Z`);
  return Number.isNaN(fecha.getTime()) ? '' : FECHA_CORTA.format(fecha);
}

/**
 * Minutos → `7h 30m`, `45m` o `-1h 05m`.
 *
 * Es la unidad de casi todo lo que no es el cronómetro: la jornada semanal,
 * los totales del historial, las horas extra y el saldo. Admite negativos
 * porque un saldo puede serlo (faltan horas), y ahí el signo es el dato.
 */
export function minutos(total: number): string {
  const signo = total < 0 ? '-' : '';
  const absoluto = Math.abs(Math.round(total));
  const h = Math.floor(absoluto / 60);
  const m = absoluto % 60;
  if (h === 0) return `${signo}${m}m`;
  return `${signo}${h}h ${dos(m)}m`;
}

/**
 * Segundos → `2h 05m` o `05m 30s`.
 *
 * Los segundos solo se enseñan por debajo de la hora: en un cronómetro que
 * lleva corriendo seis horas, el dígito que cambia cada segundo es ruido.
 */
export function duracion(segundos: number): string {
  const total = Math.max(0, Math.floor(segundos));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;

  if (h > 0) return `${h}h ${dos(m)}m`;
  return `${dos(m)}m ${dos(s)}s`;
}

/**
 * Los segundos trabajados de una jornada, ya descontada la pausa.
 *
 * Toma `segundosPausaAcumulados` y **no** los minutos. El DTO manda los dos a
 * propósito: los minutos vienen truncados (`segundos / 60`), así que una pausa
 * de 40 s llega como 0 y restarla contaría 40 segundos de trabajo que no
 * existieron. Lo destapó el cronómetro en vivo de la app, que seguía corriendo
 * durante toda la pausa.
 */
export function segundosTrabajados(
  horaEntrada: string | null | undefined,
  horaSalida: string | null | undefined,
  segundosPausa: number,
  ahora: Date = new Date(),
): number {
  const entrada = aFecha(horaEntrada);
  if (entrada === null) return 0;
  const fin = aFecha(horaSalida) ?? ahora;
  const brutos = (fin.getTime() - entrada.getTime()) / 1000;
  return Math.max(0, Math.floor(brutos - segundosPausa));
}

function aFecha(valor: string | null | undefined): Date | null {
  if (!valor) return null;
  const fecha = new Date(valor);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

function dos(n: number): string {
  return n.toString().padStart(2, '0');
}

/* ------------------------------------------------------------------ */
/* Días de España, y de hora de España a instante                      */
/* ------------------------------------------------------------------ */

/*
 * Los días viajan como `aaaa-mm-dd` (el `LocalDate` de la API) y se operan
 * como fechas UTC a medianoche: sumar un día a un `Date` local cruzaría mal el
 * cambio de hora, y en UTC no hay cambio de hora. Qué día es «hoy» o en qué día
 * cae un instante, en cambio, se pregunta siempre a la zona de España.
 */

const PARTES_ESPANA = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA_ESPANA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

function partesEnEspana(fecha: Date): Record<'year' | 'month' | 'day' | 'hour' | 'minute' | 'second', number> {
  const partes = Object.fromEntries(
    PARTES_ESPANA.formatToParts(fecha)
      .filter((p) => p.type !== 'literal')
      .map((p) => [p.type, Number(p.value)]),
  );
  return partes as Record<'year' | 'month' | 'day' | 'hour' | 'minute' | 'second', number>;
}

function aDia(fecha: Date): string {
  return fecha.toISOString().slice(0, 10);
}

function deDia(dia: string): Date {
  return new Date(`${dia}T00:00:00Z`);
}

/** El día de España en que cae un instante: `2026-09-21T23:30:00Z` → `2026-09-22`. */
export function diaEnEspana(instante: string | Date = new Date()): string {
  const p = partesEnEspana(typeof instante === 'string' ? new Date(instante) : instante);
  return `${p.year}-${dos(p.month)}-${dos(p.day)}`;
}

/** Hoy, en España. */
export function hoyEnEspana(ahora: Date = new Date()): string {
  return diaEnEspana(ahora);
}

/** La hora de España de un instante, como la quiere un `<input type="time">`: `09:03`. */
export function horaEnEspana(instante: string | null | undefined): string {
  const fecha = aFecha(instante);
  if (fecha === null) return '';
  const p = partesEnEspana(fecha);
  return `${dos(p.hour)}:${dos(p.minute)}`;
}

/**
 * Un día y una hora de España → el instante UTC que espera la API.
 *
 * Es el `aInstanteIso` de Android. Sin `Temporal`, el desfase de Madrid se
 * averigua preguntándole a `Intl` qué hora de España es un instante de
 * prueba, y se corrige una segunda vez por si el de prueba y el bueno caen a
 * distinto lado de un cambio de hora.
 */
export function aInstante(dia: string, hora: string): string {
  const [a, m, d] = dia.split('-').map(Number) as [number, number, number];
  const [h, mi] = hora.split(':').map(Number) as [number, number];
  const comoSiFueraUtc = Date.UTC(a, m - 1, d, h, mi);

  const desfase = (instante: number) => {
    const p = partesEnEspana(new Date(instante));
    return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - instante;
  };

  let instante = comoSiFueraUtc - desfase(comoSiFueraUtc);
  instante = comoSiFueraUtc - desfase(instante);
  return new Date(instante).toISOString();
}

export function sumarDias(dia: string, dias: number): string {
  const fecha = deDia(dia);
  fecha.setUTCDate(fecha.getUTCDate() + dias);
  return aDia(fecha);
}

/** Días de calendario entre dos días (`hasta - desde`). */
export function diasEntre(desde: string, hasta: string): number {
  return Math.round((deDia(hasta).getTime() - deDia(desde).getTime()) / 86_400_000);
}

/** El lunes de la semana de ese día: la semana empieza en lunes, como en España y en el backend. */
export function lunesDe(dia: string): string {
  const semana = deDia(dia).getUTCDay(); // 0 = domingo
  return sumarDias(dia, -((semana + 6) % 7));
}

export function primeroDeMes(dia: string): string {
  return `${dia.slice(0, 7)}-01`;
}

export function ultimoDeMes(dia: string): string {
  const fecha = deDia(primeroDeMes(dia));
  fecha.setUTCMonth(fecha.getUTCMonth() + 1);
  fecha.setUTCDate(0);
  return aDia(fecha);
}

/** Todos los días de un rango, los dos incluidos. */
export function diasDelRango(desde: string, hasta: string): string[] {
  const dias: string[] = [];
  for (let dia = desde; dia <= hasta; dia = sumarDias(dia, 1)) dias.push(dia);
  return dias;
}

const DIA_SEMANA_CORTO = new Intl.DateTimeFormat(ES, { timeZone: 'UTC', weekday: 'narrow' });

/** La inicial del día de la semana: `L`, `M`, `X`… (para los ejes de los gráficos). */
export function inicialDelDia(dia: string): string {
  const texto = DIA_SEMANA_CORTO.format(deDia(dia));
  return texto.toUpperCase();
}

/**
 * La hora de salida, con `(+1 d)` si la jornada acabó otro día.
 *
 * Existe por el turno de noche: una jornada de 22:52 a 00:29 enseñada como
 * «Entrada 22:52 h / Salida 00:29 h» parece una jornada de menos de nada, o
 * una salida antes de la entrada. En Android pasó exactamente eso.
 */
export function horaDeSalida(entrada: string | null | undefined, salida: string | null | undefined): string {
  if (!salida) return '';
  const texto = hora(salida);
  if (!entrada) return texto;
  const dias = diasEntre(diaEnEspana(entrada), diaEnEspana(salida));
  return dias > 0 ? `${texto} (+${dias} d)` : texto;
}
