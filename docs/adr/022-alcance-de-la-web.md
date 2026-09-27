# 22. La web empieza por los cimientos, y lo que queda fuera tiene nombre

**Estado:** aceptada; **su parte de alcance la sustituye el [ADR 029](029-la-web-alcanza-a-la-app.md)** · **Fecha:** septiembre 2026

## Contexto

El [ADR 021](021-la-web-es-un-proyecto-aparte.md) decide **cómo** se hace la
web. Este decide **cuánta** web hay, qué pantallas le tocan a ella y no al
móvil, y qué se dejó fuera a propósito.

Hace falta escribirlo porque una web con dos pantallas, vista sin contexto,
parece una web a medias. Lo es, pero por decisión: el día que alguien se
pregunte por qué la web no tiene el calendario, o por qué la analítica no está
en el móvil, la respuesta tiene que estar aquí y no en la memoria de nadie.

## Lo que se decidió

### En este plan, solo los cimientos

Había tres alcances posibles para la web en el plan de septiembre de 2026:

| Alcance | Qué incluía |
|---|---|
| **Solo los cimientos** | Backend listo para navegador, contrato generado, tokens de diseño, y login + fichar de punta a punta |
| Cimientos + web de empleado | Además: historial, calendario, ausencias y perfil |
| Cimientos + web de gestión | Además: historial del equipo, ausencias pendientes, correcciones, informes y proyectos |

**Se eligieron los cimientos.** La web de hoy tiene login y fichar, y nada más.

El motivo es de orden, no de ambición: las dos pantallas bastan para probar
que todo lo que hay debajo funciona —el contrato generado, los tokens, el
refresco serializado, el arranque en frío de Render, CORS, la CSP y el
despliegue—, y cada pantalla que se añada después se apoya en eso en vez de
descubrirlo. Hacer quince pantallas encima de unos cimientos sin probar habría
sido encontrar cada uno de esos problemas quince veces.

### Hay pantallas que son de la web y no del móvil

No todo tiene que existir en los dos sitios. Tres pantallas del bloque de
funcionalidades de septiembre **irán solo a la web**. Ninguna existe todavía:
lo que se decidió es dónde van, y se escribe ahora porque condiciona cómo se
harán.

- **El editor de cuadrantes.** Montar un turno partido en 400 dp de ancho es
  trabajo tirado. La app enseñará el cuadrante (solo lectura) y la hora de
  entrada prevista; editarlo es cosa de quien planifica, sentado.
- **La analítica de absentismo y puntualidad.** Una tabla cruzada departamento
  × mes no cabe en un móvil, y quien la mira trabaja con teclado. La app
  tendrá **una tarjeta** con los dos porcentajes del mes y un enlace a la web.
- **El visado de las firmas mensuales** por parte de RRHH.

### Las pantallas candidatas, sin orden fijado

Lo que la web debería tener, además de lo de arriba: **informes**, **gestión de
empleados** y **canal de denuncias** (la parte de quien instruye). Son las
pantallas en las que una pantalla grande aporta más que el móvil.

**El orden no está decidido**, y no se inventa aquí. Lo que sí se sabe es lo
que lo condiciona:

- Informes, gestión de empleados y canal de denuncias **ya tienen backend**:
  se pueden hacer en cualquier momento.
- El editor de cuadrantes, el visado de firmas y la analítica **no**: dependen
  del bloque B, que está sin hacer, y la analítica además depende de las
  incidencias de cuadrante.

## Lo que queda fuera, con su nombre

Estas no son omisiones: se hablaron, se pospusieron, y están aquí para que no
se pierdan.

- **iOS**, en SwiftUI nativo (ver ADR 021). Hereda el contrato y los permisos
  resueltos por el servidor; las reglas que hoy copian Android y la web
  —fechas y errores— serían su tercera copia.
- **Fichaje en kiosco (QR o PIN)**: una tablet en la entrada donde varias
  personas fichan sin sesión personal. Abre el producto a obra, almacén y
  tienda, donde nadie tiene el móvil a mano.
- **SSO con Google y Microsoft (OIDC)**: lo primero que pide una empresa al
  evaluar un SaaS. Su sitio natural es la web, porque el flujo OAuth es mucho
  más cómodo en un navegador que en un móvil, y por eso no tenía sentido antes
  de que la web existiera.
- **Zona horaria y ajustes por empresa.** Hoy `Europe/Madrid` está cableado en
  **32 ficheros del backend** —entre ellos **4 consultas SQL nativas** de
  `TimeEntryRepository`, que son lo más caro de cambiar—, en `fechas.ts` de la
  web y en cuatro ficheros de la app Android (contado el 23/09/2026). No es un fallo hoy, es deuda estructural: una empresa en Canarias
  vería los días, los umbrales diarios de horas extra y los informes mensuales
  desplazados una hora. Es requisito para vender fuera de la península.
- **Fichaje sin conexión**, con cola de sincronización. Se ofreció y no se
  eligió; sigue siendo candidata.

Y dos cosas que la web **no hace a propósito**, con su decisión en otro sitio:

- **No recuerda la sesión al recargar.** Los tokens viven en memoria hasta que
  haya dominio propio ([ADR 020](020-tokens-en-el-navegador.md)).
- **No ejecuta su test de extremo a extremo en CI**, porque necesita un backend
  con datos de demostración. Se ejecuta a mano (`npm run e2e`).

## Consecuencias

- **La web de hoy hace menos que la app, y el README lo dice.** Quien la abra
  espera más de dos pantallas; la sección de estado del README la presenta
  como lo que es.
- **La analítica y el editor de cuadrantes obligan a la web a existir.** Cuando
  se hagan, no habrá versión móvil a la que recurrir; eso es intencionado, pero
  también significa que el bloque B no se cierra sin tocar la web.
- **Las funcionalidades aplazadas se revisan al hacer el próximo plan**, no
  antes: se pidió expresamente que no se perdieran por el camino.
