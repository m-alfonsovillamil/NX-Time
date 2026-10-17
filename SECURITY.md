# Seguridad

## Cómo avisar de un fallo

Si encuentras un fallo de seguridad en NX Time (la API, la web o la app Android),
**escríbenos a [seguridad@nxtime-web.com](mailto:seguridad@nxtime-web.com)** y no
abras una incidencia pública en GitHub hasta que esté arreglado.

Cuéntanos, si puedes:

- qué has encontrado y dónde (la ruta, la pantalla o la versión de la app);
- cómo reproducirlo, paso a paso;
- qué crees que podría hacer alguien con él.

**Lo que hacemos nosotros:**

- te contestamos en **3 días laborables** como mucho, para decirte que lo hemos
  recibido;
- te tenemos al tanto de lo que hacemos, y te avisamos cuando esté arreglado;
- si lo quieres, te nombramos al publicar el arreglo;
- no emprendemos ninguna acción contra quien investiga de buena fe y siguiendo
  estas normas.

**Lo que te pedimos:**

- prueba con **tu propia cuenta o una empresa de prueba** que registres tú; no
  accedas a datos de otras personas ni los cambies, y si das con alguno sin
  querer, para y avísanos;
- nada que degrade el servicio para los demás: ni pruebas de carga ni de
  denegación de servicio, ni envíos masivos de correo;
- nada de ingeniería social ni contra las personas ni contra los proveedores
  (Render, Neon, Cloudflare, Brevo, Google).

## Qué versiones reciben arreglos

Solo la que está en producción (<https://nxtime-web.com>) y la **última versión
publicada de la app** Android. A quien tenga una anterior se le pide actualizar.

## Si un fallo se está aprovechando ya

Si un fallo de la app se está explotando de verdad, además de arreglarlo lo
notificamos como pide el Reglamento de Ciberresiliencia (UE 2024/2847, art. 14):
alerta temprana en 24 horas, notificación en 72 horas e informe final en 14 días.
El procedimiento interno está en `docs/cumplimiento/respuesta-a-incidentes.md`.

---

La versión legible por máquinas está en
<https://nxtime-web.com/.well-known/security.txt> (RFC 9116).
