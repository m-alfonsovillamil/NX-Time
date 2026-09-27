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
