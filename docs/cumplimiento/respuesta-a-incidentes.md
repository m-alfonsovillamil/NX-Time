# Respuesta a incidentes

Qué hacer si pasa algo. Escrito para leerlo con prisa.

## 0. Plazos que corren solos

| Desde que sabes que… | Hay que | Plazo | Norma |
|---|---|---|---|
| …se han visto afectados datos de una empresa cliente | Avisar a sus ADMIN | **48 horas** (lo promete el contrato de encargo) | RGPD 33.2 |
| …se han visto afectados datos propios (cuentas de administradores, registros) | Notificar a la AEPD, si hay riesgo | **72 horas** | RGPD 33 |
| …y el riesgo es alto para las personas | Avisar a las personas | Sin dilación | RGPD 34 |
| …una vulnerabilidad de la **app** se está explotando | Alerta temprana a ENISA (plataforma única) | **24 horas** | CRA 14.2.a |
| | Notificación | **72 horas** | CRA 14.2.b |
| | Informe final | **14 días** desde que hay arreglo | CRA 14.2.c |
| …un incidente grave afecta a la seguridad de la app | Alerta temprana / notificación / informe | 24 h / 72 h / 1 mes | CRA 14.4 |

Notificar a la AEPD: sede electrónica, «Notificación de brechas de datos
personales». A ENISA: la plataforma única de notificación del CRA.

## 1. Contener (la primera hora)

1. **Cortar el acceso**, según lo que sea:
   - una cuenta: darla de baja (cierra sus sesiones);
   - una empresa: suspenderla desde el panel de plataforma;
   - todo el servicio: suspender el servicio en Render (el plan gratuito no
     tiene modo mantenimiento: *Suspend*).
2. **Rotar el secreto afectado** (ver la tabla de abajo). Si no sabes cuál, todos.
3. **No borrar nada**: los registros de Render duran pocos días; descargarlos.

## 2. Inventario de accesos y cómo rotar cada secreto

| Secreto | Dónde vive | Cómo se rota | Efecto |
|---|---|---|---|
| `JWT_SECRET` | Render | Generar otro (`openssl rand -base64 64`) y desplegar | Todas las sesiones caen a los 15 min como mucho |
| Contraseña de `nxtime_app` | Neon + `DATABASE_URL` en Render | Neon → Roles → Reset; actualizar Render | Reinicio del servicio |
| Contraseña de `neondb_owner` | Neon + `.env.local` (fuera de OneDrive) | Neon → Roles → Reset | Afecta a las migraciones y a las copias |
| Clave SMTP de Brevo | Render (`MAIL_PASSWORD`) | Brevo → SMTP & API → nueva clave; borrar la vieja | Correos parados hasta actualizarla |
| Cuenta de servicio de Firebase | Render (`FIREBASE_CREDENTIALS_JSON`) | Google Cloud → IAM → cuentas de servicio → nueva clave; borrar la vieja | Push parado hasta actualizarla |
| Secreto del cliente OAuth de Google / Microsoft | Render | Consola del proveedor → nuevo secreto | SSO parado hasta actualizarlo |
| DSN de Sentry | Render y la app | Sentry → Client Keys → revocar y crear | Los informes de la app vieja se pierden |
| Frase de las copias | `%LOCALAPPDATA%\NX Time` + gestor de contraseñas | Crear otra; las copias viejas siguen necesitando la vieja | — |
| Clave de firma de la app | Fuera del repositorio (ver `DESPLIEGUE.md`) | No se rota: si se pierde, otra app | Grave: guardarla bien |

## 3. Valorar

- ¿Qué datos, de qué empresas, cuántas personas? ¿Hay categorías especiales
  (motivos de ausencia de salud, denuncias)?
- ¿Desde cuándo? Mirar `acciones_de_plataforma`, los registros de Render y la
  traza de auditoría (comprobación de integridad desde el panel).
- ¿Riesgo para las personas? Si no hay riesgo, no se notifica a la AEPD, pero
  **se documenta igual** (RGPD 33.5): este fichero de incidente es el registro.

## 4. Avisar

- A cada empresa afectada, por correo a sus ADMIN, desde privacidad@: qué ha
  pasado, qué datos y cuántas personas, qué consecuencias, qué se ha hecho y qué
  les toca a ellas (notificar a la AEPD y a su plantilla es de cada empresa).
- A quien avisó (si fue un investigador): qué se ha hecho, como dice `SECURITY.md`.

## 5. Después

- Escribir aquí debajo el incidente: fecha, qué, alcance, decisiones, avisos.
- Un test que reproduzca el fallo y falle sin el arreglo, como en J1.
- Si fue en la app: versión nueva y aviso de actualizar.

## Registro de incidentes

| Fecha | Qué | Alcance | Notificado a | Cerrado |
|---|---|---|---|---|
| — | Ninguno a 17/10/2026 | — | — | — |
