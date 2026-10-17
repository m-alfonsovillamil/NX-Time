# Transferencias internacionales (RGPD, capítulo V)

Los datos se guardan en la UE (Render y Neon en Frankfurt). Salen de la UE, o
pueden verse desde fuera, en estos casos:

| Proveedor | Qué sale o se ve | Garantía | Comprobar |
|---|---|---|---|
| Render (EE. UU.) | Acceso de soporte e incidentes a los servidores de Frankfurt | DPF (desde enero de 2025, según Render) + SCC en su DPA | dataprivacyframework.gov/list |
| Neon / Databricks (EE. UU.) | Igual, a la base de Frankfurt | DPF + SCC en su DPA | Lista DPF y su página de subencargados |
| Cloudflare (EE. UU.) | DNS y el reenvío de los correos de contacto | DPF + SCC | Lista DPF |
| Google / Firebase (EE. UU.) | El token y el contenido de cada aviso push | DPF + SCC | Lista DPF |
| Sentry (EE. UU.) | Informes de errores (almacenados en Frankfurt si la organización es de la región UE; algún dato puede quedar en EE. UU.) | DPF + SCC | Región de la organización en Sentry |
| Google y Microsoft (SSO) | Solo si la persona elige entrar con ellos | DPF; la persona ya es usuaria suya | Lista DPF |
| Brevo (Francia) | Nada: es UE | — | — |

**El riesgo que hay:** el Marco de Privacidad de Datos UE-EE. UU. puede caer
como cayeron Safe Harbor y Privacy Shield. Si cae, cada proveedor pasa a las
cláusulas contractuales tipo, que ya están en sus DPA. Entonces habría que hacer
una evaluación de impacto de la transferencia, sobre todo para el push (lo único
que sale con contenido). Vigilarlo: es una de las cosas que quedan en manos de
Miguel al cerrar el plan J.

**Para reducirlo, si hiciera falta:** que el texto de los avisos push no lleve
datos personales (solo «Tienes un aviso nuevo»), y Sentry solo en la región UE.
