# Cumplimiento

Lo que el titular de NX Time necesita tener por escrito para el RGPD, la LSSI, el
Reglamento de servicios digitales y el de ciberresiliencia (plan J, octubre de
2026). Los textos que ven las empresas y la gente (aviso legal, privacidad,
cookies, condiciones, contrato de encargo, subencargados) están en la web, en
`/legal`, y salen de `nx-time-frontend-web/src/paginas/legal/`.

> **Son borradores serios, no asesoramiento jurídico.** Antes de aprobar la
> primera empresa real, los textos de `/legal` y estos documentos tienen que
> pasar por un abogado. Ningún documento garantiza que no haya sanciones: la
> ley cambia (el real decreto de registro digital de jornada sigue sin
> aprobarse a 17/10/2026) y el marco de transferencias a EE. UU. está en
> entredicho.

| Documento | Para qué |
|---|---|
| [Registro de actividades](registro-de-actividades.md) | Art. 30.1 (como responsable) y 30.2 (como encargado) |
| [Medidas de seguridad](medidas-de-seguridad.md) | Art. 32: lo que hay, y lo que falta |
| [Conservación](conservacion.md) | Cuánto se guarda cada dato y quién lo borra |
| [Respuesta a incidentes](respuesta-a-incidentes.md) | Qué hacer ante una brecha o una vulnerabilidad explotada |
| [Transferencias internacionales](transferencias.md) | Qué sale de la UE, a quién y con qué garantía |
| [Plantillas para las empresas](plantillas-para-clientes.md) | Lo que le toca a cada empresa cliente, para que no empiece de cero |

## Qué papel tiene el titular

- **Encargado del tratamiento** de cada empresa cliente: los datos de su
  plantilla, candidatos e informantes. Trata por cuenta de ella y con sus
  instrucciones, con el contrato de encargo de `/legal/encargo`.
- **Responsable** de lo suyo: las cuentas de quien registra y administra cada
  empresa (para el alta y la relación con ella), los registros técnicos y de
  seguridad, los informes de errores, el historial del panel de plataforma y lo
  que le escriben.
- **Prestador de servicios de la sociedad de la información** (LSSI) y de
  alojamiento (Reglamento 2022/2065, hipótesis razonable): punto de contacto y
  avisos de contenido ilícito al correo de privacidad.
- **Fabricante de un producto con elementos digitales** (la app; Reglamento
  2024/2847): desde el 11/09/2026, notificar a ENISA las vulnerabilidades
  explotadas; el resto, desde diciembre de 2027.

## Lo que NO le toca al titular

Lo hace cada empresa cliente: el registro de jornada (art. 34.9 ET), informar a
su plantilla (RGPD, art. 13), la evaluación de impacto si procede, la
desconexión digital (art. 88 LOPDGDD), el sistema interno de información y su
responsable si tiene 50 o más personas (Ley 2/2023), y la información por
escrito de las condiciones de trabajo (RD 723/2026, en vigor desde el
5/10/2026). Las [plantillas](plantillas-para-clientes.md) son ayuda, no
sustituyen su asesoramiento.

## Qué no aplica hoy

NIS2 (por tamaño, y sin ley española de trasposición), la Ley 11/2023 de
accesibilidad (el servicio es entre empresas) y el Reglamento de IA (no hay IA).
