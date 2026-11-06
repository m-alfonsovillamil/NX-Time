# Puesta en marcha

Para quien administra la empresa en NX Time (el rol **Administración**) y para
RRHH. En orden: lo de arriba hace falta para lo de abajo. Todo se hace en la web,
**nxtime-web.com**, desde un ordenador.

## 1. Registrar la empresa

1. En nxtime-web.com, **Registrar una empresa**. Quien la registra queda como
   administrador.
2. Confirma tu correo con el código que te llega.
3. La empresa queda **pendiente de aprobación**. Te llega un correo cuando está
   aprobada; hasta entonces no se puede entrar.
4. La primera vez que entres te pedirá aceptar las condiciones del servicio y el
   contrato de encargo del tratamiento. Léelos: son los que firmáis con NX Time.

## 2. Proteger las cuentas que lo ven todo

Administración y RRHH ven y cambian los datos de toda la plantilla.

1. **Ajustes** (tu cuenta) → **Segundo factor** → actívalo con una aplicación de
   autenticación del móvil (Google Authenticator, Microsoft Authenticator…).
   **Guarda los diez códigos de recuperación** en un sitio seguro: son lo que te
   deja entrar si pierdes el móvil.
2. **Ajustes de la empresa** → **Segundo factor** → **Exigir el segundo factor a
   administradores y RRHH**. Recomendado. Para poder exigirlo, tienes que
   tenerlo activado tú antes.

## 3. Lo que es de la empresa

En **Ajustes de la empresa**:

- **Zona horaria**: la de la empresa. Las horas y los días se cuentan en ella.
- **Kioscos**: si vais a tener una tablet para fichar en la entrada (punto 7).
- **Presencial o a distancia**: si se enciende, cada persona dice al entrar si
  trabaja en el centro o a distancia. Sale en su jornada, en el Excel de horas y
  en el PDF mensual.
- **Pedir la ubicación al entrar y al salir**: lee el punto 4 **antes** de
  encenderla.

En **Calendario laboral**: los festivos de la empresa. Los nacionales ya están;
añadid los autonómicos y locales.

En **Departamentos**: cread los departamentos **antes** de importar a la
plantilla, porque la importación solo admite departamentos que ya existen.

## 4. La ubicación al fichar: antes de encenderla

Sirve para el trabajo fuera del centro (técnicos, obras, visitas). Se guarda dónde
está la persona **solo al fichar la entrada y la salida**, nunca durante la
jornada. La ven la persona, su gestor, RRHH y Administración; no sale en el Excel
ni en el PDF, y se borra a los 12 meses. Quien no da permiso ficha igual, y queda
«sin ubicación». En el kiosco no se pide.

Se enciende para **toda la empresa**, también para quien trabaja en la oficina.

Antes de encenderla, la ley pide (art. 90 de la Ley Orgánica 3/2018):

1. **Informar a la plantilla**, de forma expresa y clara, de que se toma la
   ubicación, cuándo y para qué. Hay un texto de partida en
   `docs/cumplimiento/plantillas-para-clientes.md`.
2. **Informar a la representación de los trabajadores**, si la hay.
3. **Revisarlo con vuestro asesor**: puede hacer falta una evaluación de
   impacto, sobre todo si se pide a quien no trabaja fuera.

## 5. Dar de alta a la plantilla

**Plantilla** → **Importar la plantilla**. Sube un Excel (`.xlsx`) o un CSV con
una persona por fila. La primera fila lleva los títulos:

| Columna | Obligatoria | Qué va |
|---|---|---|
| `nombre` | Sí | |
| `apellidos` | Sí | |
| `email` | Sí | Su correo: ahí le llega la invitación. Uno por persona |
| `rol` | No | `empleado` (si no se pone), `gestor`, `rrhh` o `admin` |
| `departamento` | No | Tiene que existir ya (punto 3) |
| `horas` | No | Jornada semanal, por ejemplo `40` o `37,5` |
| `vacaciones` | No | Días al año |
| `codigo` | No | Su número en vuestro ERP o en nóminas |

- Vale el Excel tal cual: los títulos pueden ir con mayúsculas o tildes, y se
  entienden variantes como «correo», «jornada» o «nº empleado».
- Como mucho 500 personas por fichero.
- **Primero se comprueba sin crear nada**, y te enseña cada fila con sus
  errores. Si alguna fila está mal, no se crea nadie: corrige el fichero y
  vuelve a subirlo.
- Las fórmulas de Excel se leen por su resultado.

**Las invitaciones salen en tandas: 50 al día como mucho.** Las cuentas se crean
todas en el momento, pero el correo para elegir contraseña sale a 50 personas al
día. Con 120 personas: el primer día salen 50, y al día siguiente hay que volver
a **Plantilla** y pulsar **Mandar las invitaciones** para las siguientes. La
lista marca a quien todavía no tiene invitación.

**Cambiar el rol** de alguien: **Plantilla** → la persona → **Cambiar rol**.
Vale desde ese momento. Nadie puede cambiarse el suyo.

## 6. Que cada persona tenga la app

El correo de invitación lleva el enlace para **descargar la app de Android** y
explica cómo elegir la contraseña con el código que trae. El código caduca en 24
horas; desde la app se puede pedir otro.

- **Android**: la app se instala desde ese enlace. Android pedirá permiso para
  instalar una aplicación que no viene de Google Play: es normal.
- **iPhone**: todavía no hay app. Se ficha desde el navegador del móvil, en
  nxtime-web.com. **Sin cobertura no se puede fichar desde la web**: solo la app
  de Android guarda el fichaje y lo manda al volver la red.

Mándales también la guía [Fichar y el día a día](fichar.md).

## 7. Un kiosco en la entrada (opcional)

Una tablet donde se ficha sin abrir la sesión de nadie: con una tarjeta QR o con
el nombre y un PIN.

1. En la tablet, abre **nxtime-web.com/kiosco**. Enseña un código de 8 letras.
2. En tu ordenador, **Ajustes de la empresa** → **Kioscos**: teclea ese código y
   ponle un nombre («Entrada almacén»). El código caduca a los 10 minutos.
3. Las tarjetas: **Tarjetas del kiosco** → imprimirlas. Cada persona también
   puede descargar la suya desde su perfil, o elegir un PIN para el kiosco.
4. Si se pierde una tablet: **Ajustes de la empresa** → **Kioscos** →
   revocarla. Deja de fichar al momento; sus fichajes se quedan.

## 8. La primera semana

- Mirad cada día **Panel de gestión**: correcciones y ausencias pendientes, y
  quién no ha fichado.
- Explicad a los gestores la guía [Gestores y RRHH](gestion.md), sobre todo
  «Dar por bueno» si hay gente que ficha sin cobertura.
- Si algo no funciona, la guía de [soporte](soporte.md).
