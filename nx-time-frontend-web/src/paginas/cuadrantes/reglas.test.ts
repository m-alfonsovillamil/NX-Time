/**
 * Las reglas de un cuadrante: las mismas que el backend, con los mismos casos.
 */

import { describe, expect, it } from 'vitest';

import { aEditable, aTramo, minutosSemanales, problemaEnLaSemana, solapeEnUnDia } from './reglas';

describe('reglas de un cuadrante', () => {
  it('el turno de noche va en un solo tramo que pasa de 1440', () => {
    expect(aTramo({ inicio: '22:00', fin: '06:00', alDiaSiguiente: true })).toEqual({ inicio: 1320, fin: 1800 });
    expect(aEditable(1320, 1800)).toEqual({ inicio: '22:00', fin: '06:00', alDiaSiguiente: true });
    expect(aTramo({ inicio: '9:00', fin: 'nueve', alDiaSiguiente: false })).toBeNull();
  });

  it('un tramo que acaba antes de empezar, sin marcar el día siguiente, se dice', () => {
    expect(problemaEnLaSemana([{ diaSemana: 1, inicio: 1320, fin: 360 }])).toMatch(/^Lunes: un tramo tiene que terminar después de empezar/);
  });

  it('turno partido del mismo día: pegados vale, pisados no', () => {
    expect(problemaEnLaSemana([{ diaSemana: 2, inicio: 540, fin: 840 }, { diaSemana: 2, inicio: 840, fin: 1080 }])).toBeNull();
    expect(problemaEnLaSemana([{ diaSemana: 2, inicio: 540, fin: 900 }, { diaSemana: 2, inicio: 840, fin: 1080 }])).toBe(
      'hay dos tramos del martes que se pisan.',
    );
  });

  /* Lo que el EXCLUDE de la base no ve: el solape entre días. */
  it('la noche del lunes pisa la mañana del martes, y la del domingo el lunes', () => {
    expect(problemaEnLaSemana([{ diaSemana: 1, inicio: 1320, fin: 1800 }, { diaSemana: 2, inicio: 300, fin: 600 }])).toBe(
      'el tramo del lunes termina después de que empiece el del martes.',
    );
    expect(problemaEnLaSemana([{ diaSemana: 7, inicio: 1320, fin: 1800 }, { diaSemana: 1, inicio: 300, fin: 600 }])).not.toBeNull();
    expect(problemaEnLaSemana([{ diaSemana: 1, inicio: 1320, fin: 1800 }, { diaSemana: 2, inicio: 360, fin: 600 }])).toBeNull();
  });

  it('las excepciones de un día y los minutos de la semana', () => {
    expect(solapeEnUnDia([{ inicio: 480, fin: 840 }, { inicio: 600, fin: 900 }])).toBe('hay dos tramos que se pisan.');
    expect(minutosSemanales([{ diaSemana: 1, inicio: 540, fin: 1020 }, { diaSemana: 2, inicio: 1320, fin: 1800 }])).toBe(960);
  });
});
