/**
 * Las reglas de un cuadrante, en la web: el espejo de `ReglasDeCuadrante` del
 * backend, para decir lo que está mal **antes** de mandar la plantilla.
 *
 * El servidor lo vuelve a comprobar (y la base, con su EXCLUDE): esto no lo
 * sustituye, solo evita el viaje de ida y vuelta y señala el tramo concreto.
 * Los mensajes son los mismos que devolvería el servidor.
 *
 * Los horarios van en **minutos desde medianoche del día en que empieza el
 * tramo**: 09:00 es 540, y un turno de 22:00 a 06:00 es [1320, 1800). Así el
 * turno de noche cabe en un solo tramo, y los solapes se comparan con enteros.
 */

export const MINUTOS_DIA = 24 * 60;
export const MINUTOS_SEMANA = 7 * MINUTOS_DIA;

export const DIAS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'] as const;

/** Un tramo como se edita: dos horas y si acaba al día siguiente. */
export interface TramoEditable {
  inicio: string;
  fin: string;
  alDiaSiguiente: boolean;
}

/** Un tramo como lo quiere la API. `diaSemana` en ISO: 1 = lunes. */
export interface TramoSemanal {
  diaSemana: number;
  inicio: number;
  fin: number;
}

export function aMinutos(hora: string): number | null {
  const partes = /^(\d{1,2}):(\d{2})$/.exec(hora);
  if (partes === null) return null;
  const [h, m] = [Number(partes[1]), Number(partes[2])];
  return h < 24 && m < 60 ? h * 60 + m : null;
}

export function aHora(minutos: number): string {
  const m = ((minutos % MINUTOS_DIA) + MINUTOS_DIA) % MINUTOS_DIA;
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

/** De lo que se edita a minutos. `null` si una de las horas no es una hora. */
export function aTramo(t: TramoEditable): { inicio: number; fin: number } | null {
  const inicio = aMinutos(t.inicio);
  const fin = aMinutos(t.fin);
  if (inicio === null || fin === null) return null;
  return { inicio, fin: fin + (t.alDiaSiguiente ? MINUTOS_DIA : 0) };
}

/** De minutos a lo que se edita: 1320–1800 es 22:00–06:00 al día siguiente. */
export function aEditable(inicio: number, fin: number): TramoEditable {
  return { inicio: aHora(inicio), fin: aHora(fin), alDiaSiguiente: fin > MINUTOS_DIA };
}

/** Lo que está mal en un tramo suelto, o `null`. También vale para las excepciones. */
export function problemaEnElTramo(inicio: number, fin: number): string | null {
  if (inicio < 0 || inicio >= MINUTOS_DIA) return 'un tramo tiene que empezar entre las 00:00 y las 23:59.';
  if (fin <= inicio) return 'un tramo tiene que terminar después de empezar. Si acaba al día siguiente, márcalo.';
  if (fin - inicio > MINUTOS_DIA) return 'un tramo no puede durar más de 24 horas.';
  return null;
}

function nombreDelDia(diaSemana: number): string {
  return DIAS[diaSemana - 1] ?? '';
}

/**
 * Lo primero que está mal en los tramos de una semana, o `null`.
 *
 * Lo que la base no ve (su EXCLUDE compara tramos del mismo día) y aquí sí:
 * **el solape entre días**. Un turno del lunes de 22:00 a 06:00 pisa un tramo
 * del martes que empiece antes de las 06:00; y el del domingo que acaba el
 * lunes pisa el lunes, porque la semana se repite.
 */
export function problemaEnLaSemana(tramos: readonly TramoSemanal[]): string | null {
  for (const t of tramos) {
    const problema = problemaEnElTramo(t.inicio, t.fin);
    if (problema !== null) return `${nombreDelDia(t.diaSemana).replace(/^./, (c) => c.toUpperCase())}: ${problema}`;
  }
  const enLaSemana: [number, number, number][] = [];
  for (const t of tramos) {
    const base = (t.diaSemana - 1) * MINUTOS_DIA;
    enLaSemana.push([base + t.inicio, base + t.fin, t.diaSemana]);
    if (base + t.fin > MINUTOS_SEMANA) enLaSemana.push([0, base + t.fin - MINUTOS_SEMANA, t.diaSemana]);
  }
  enLaSemana.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  for (let i = 1; i < enLaSemana.length; i++) {
    const anterior = enLaSemana[i - 1] as [number, number, number];
    const actual = enLaSemana[i] as [number, number, number];
    // Semiabiertos: acabar a las 14:00 y empezar a las 14:00 no es pisarse.
    if (actual[0] < anterior[1]) {
      return anterior[2] === actual[2]
        ? `hay dos tramos del ${nombreDelDia(actual[2])} que se pisan.`
        : `el tramo del ${nombreDelDia(anterior[2])} termina después de que empiece el del ${nombreDelDia(actual[2])}.`;
    }
  }
  return null;
}

/** Si dos tramos de un mismo día (una excepción) se pisan. */
export function solapeEnUnDia(tramos: readonly { inicio: number; fin: number }[]): string | null {
  const ordenados = [...tramos].sort((a, b) => a.inicio - b.inicio);
  for (let i = 1; i < ordenados.length; i++) {
    if ((ordenados[i] as { inicio: number }).inicio < (ordenados[i - 1] as { fin: number }).fin) return 'hay dos tramos que se pisan.';
  }
  return null;
}

export function minutosSemanales(tramos: readonly TramoSemanal[]): number {
  return tramos.reduce((s, t) => s + (t.fin - t.inicio), 0);
}
