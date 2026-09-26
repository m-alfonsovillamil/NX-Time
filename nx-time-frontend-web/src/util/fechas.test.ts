import { describe, expect, it } from 'vitest';

import {
  aInstante,
  diaEnEspana,
  diasDelRango,
  duracion,
  fechaCorta,
  hora,
  horaDeSalida,
  horaEnEspana,
  lunesDe,
  minutos,
  primeroDeMes,
  segundosTrabajados,
  sumarDias,
  ultimoDeMes,
} from './fechas';

describe('la hora', () => {
  /*
   * El test que justifica que la zona esté cableada. Con
   * `toLocaleTimeString()` a secas esto daría una hora distinta según dónde
   * esté el portátil, y la jornada que se enseña es la del centro de trabajo.
   *
   * 07:03 UTC en septiembre son las 09:03 en España (CEST, UTC+2).
   */
  it('se pinta en hora de España, no en la del navegador', () => {
    expect(hora('2026-09-21T07:03:11Z')).toBe('09:03 h');
  });

  /*
   * En invierno el desplazamiento es de una hora, no de dos. Es el caso que
   * se rompe solo si alguien "simplifica" restando un número fijo.
   */
  it('el cambio de hora lo aplica la zona, no una resta fija', () => {
    expect(hora('2026-01-15T07:03:11Z')).toBe('08:03 h');
  });

  it('sin hora no inventa nada', () => {
    expect(hora(null)).toBe('');
    expect(hora(undefined)).toBe('');
    expect(hora('no es una fecha')).toBe('');
  });
});

describe('la duración', () => {
  it('enseña segundos por debajo de la hora y los deja de enseñar por encima', () => {
    // En un cronómetro que lleva seis horas, el dígito que cambia cada
    // segundo es ruido.
    expect(duracion(45)).toBe('00m 45s');
    expect(duracion(90)).toBe('01m 30s');
    expect(duracion(3600)).toBe('1h 00m');
    expect(duracion(7_530)).toBe('2h 05m');
  });

  it('nunca sale en negativo', () => {
    expect(duracion(-10)).toBe('00m 00s');
  });
});

describe('los segundos trabajados', () => {
  const entrada = '2026-09-21T07:00:00Z';

  it('descuenta la pausa', () => {
    const ahora = new Date('2026-09-21T09:00:00Z');
    expect(segundosTrabajados(entrada, null, 600, ahora)).toBe(6_600);
  });

  /*
   * El caso que destapó el cronómetro en vivo de la app: el DTO manda los
   * minutos truncados (`segundos / 60`), así que una pausa de 40 s llega
   * como 0 y restar ese 0 contaría 40 segundos de trabajo que no existieron.
   * Por eso esta función toma los SEGUNDOS.
   */
  it('una pausa de menos de un minuto también cuenta', () => {
    const ahora = new Date('2026-09-21T07:10:00Z');
    expect(segundosTrabajados(entrada, null, 40, ahora)).toBe(560);
  });

  it('con la jornada cerrada usa la salida y no el reloj', () => {
    const ahora = new Date('2026-09-21T23:00:00Z');
    expect(segundosTrabajados(entrada, '2026-09-21T08:00:00Z', 0, ahora)).toBe(3_600);
  });

  it('sin entrada no hay nada que contar', () => {
    expect(segundosTrabajados(null, null, 0)).toBe(0);
  });
});

describe('minutos', () => {
  it('horas y minutos, con los minutos a dos cifras', () => {
    expect(minutos(450)).toBe('7h 30m');
    expect(minutos(65)).toBe('1h 05m');
  });

  it('por debajo de la hora, solo minutos', () => {
    expect(minutos(45)).toBe('45m');
    expect(minutos(0)).toBe('0m');
  });

  /* Un saldo puede ser negativo (faltan horas), y ahí el signo es el dato. */
  it('un negativo lleva su signo delante de todo', () => {
    expect(minutos(-65)).toBe('-1h 05m');
    expect(minutos(-5)).toBe('-5m');
  });
});

describe('fechaCorta', () => {
  /*
   * Un día sin hora no se mueve con la zona: el 21 es el 21. Se prueba con
   * el primero de mes porque es donde un desplazamiento se vería (el 1 de
   * octubre enseñado como 30 de septiembre).
   */
  it('un LocalDate se enseña como ese mismo día', () => {
    expect(fechaCorta('2026-10-01')).toBe('jue, 1 oct');
  });

  it('algo que no es una fecha no se inventa', () => {
    expect(fechaCorta('ayer')).toBe('');
    expect(fechaCorta(undefined)).toBe('');
  });
});

describe('días e instantes de España', () => {
  it('una hora de España en verano es UTC+2, y en invierno UTC+1', () => {
    expect(aInstante('2026-09-21', '09:03')).toBe('2026-09-21T07:03:00.000Z');
    expect(aInstante('2026-01-15', '09:00')).toBe('2026-01-15T08:00:00.000Z');
  });

  /* El día del cambio de hora de octubre: antes de las 3 aún es verano. */
  it('acierta a los dos lados del cambio de hora', () => {
    expect(aInstante('2026-10-25', '01:00')).toBe('2026-10-24T23:00:00.000Z');
    expect(aInstante('2026-10-25', '12:00')).toBe('2026-10-25T11:00:00.000Z');
  });

  it('la ida y la vuelta dan la misma hora', () => {
    expect(horaEnEspana(aInstante('2026-03-02', '22:52'))).toBe('22:52');
  });

  /* Las 23:30 UTC de un día de verano ya son el día siguiente en España. */
  it('el día de un instante es el de España, no el de UTC', () => {
    expect(diaEnEspana('2026-09-21T22:30:00Z')).toBe('2026-09-22');
    expect(diaEnEspana('2026-09-21T21:59:00Z')).toBe('2026-09-21');
  });

  it('la semana empieza en lunes, también si hoy es domingo', () => {
    expect(lunesDe('2026-09-27')).toBe('2026-09-21');
    expect(lunesDe('2026-09-21')).toBe('2026-09-21');
  });

  it('el mes, con febrero y el cambio de año', () => {
    expect(primeroDeMes('2026-02-17')).toBe('2026-02-01');
    expect(ultimoDeMes('2026-02-17')).toBe('2026-02-28');
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(diasDelRango('2026-09-29', '2026-10-02')).toEqual(['2026-09-29', '2026-09-30', '2026-10-01', '2026-10-02']);
  });

  it('la salida de un turno de noche dice que es de otro día', () => {
    expect(horaDeSalida('2026-09-21T20:52:00Z', '2026-09-21T22:29:00Z')).toBe('00:29 h (+1 d)');
    expect(horaDeSalida('2026-09-21T07:00:00Z', '2026-09-21T15:00:00Z')).toBe('17:00 h');
  });
});
