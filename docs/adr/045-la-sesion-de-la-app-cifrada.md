# 45. La sesión de la app, cifrada en el disco

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

La app guardaba los dos tokens de la sesión, el de acceso y el de refresco, tal
cual en sus preferencias. El de refresco vale un mes.

Esas preferencias están en la carpeta privada de la app y fuera de las copias
de seguridad (`allowBackup="false"`), así que otra app no las lee y no acaban
en la nube. Lo que quedaba sin cubrir es quien saca los ficheros del teléfono:
con acceso de root, o desde una imagen del almacenamiento. Se llevaba una
sesión lista para usar.

Salió al repasar la app contra una lista corriente de comprobaciones de
seguridad («cifrar los datos sensibles»). Era la única de esa lista que la app
cumplía a medias.

## Decisión

Los dos tokens se escriben cifrados con AES-256-GCM, con una clave que se crea
en el almacén de claves de Android y no sale de él (`CifradoDeLaSesion`). Los
ficheros de la app, solos, dejan de servir.

Lo que no es obvio:

- **No es `EncryptedSharedPreferences`.** Esa librería de AndroidX quedó sin
  mantenimiento en 2025. Lo que hacía falta de ella cabe en una clase.
- **Solo los tokens.** El nombre, los permisos y la zona horaria siguen como
  estaban: no abren nada, y cifrarlos sería descifrar más cosas al arrancar.
- **Quien actualiza no vuelve a entrar.** Al arrancar, un token guardado por
  una versión anterior se reconoce porque no lleva la marca `c1:`, y se
  reescribe cifrado en ese momento.
- **La clave no pide huella ni desbloqueo.** Renovar el token y registrar el
  push pasan con la app en segundo plano y la pantalla apagada.
- **Se descifra una vez, al arrancar.** El token de acceso se pide en cada
  petición, y descifrar es una llamada al almacén del sistema. En memoria está
  igual que estaba antes: las preferencias ya lo tenían cargado.
- **Si el teléfono no puede cifrar, se guarda como antes.** Hay dispositivos
  con el almacén de claves estropeado. Una app que no deja entrar es peor que
  una que guarda la sesión como lo hizo siempre; queda apuntado en el registro
  del sistema.
- **Si lo cifrado ya no se puede leer, no hay sesión.** La clave se pierde, por
  ejemplo, al restaurar los datos de la app en otro teléfono. Se vuelve a
  entrar, que es lo que tiene que pasar.

## Lo que se ha probado

En el emulador, con el almacén de claves de verdad: se instala la 1.15, se
entra, y el fichero de preferencias tiene el JWT y el refresh legibles. Se
instala la 1.16 encima y se abre: sigue dentro sin pedir nada, el historial
carga, y en el fichero los dos valores empiezan por `c1:` y ya no hay ningún
JWT. Aguanta cerrar la app y abrirla otra vez. Se hizo con la variante
minificada (la que se publica) y con la de depuración, que es la que deja leer
el fichero.

Los tests cubren lo que decide el gestor de la sesión alrededor del cifrado,
con uno de mentira: el almacén de claves no existe en la JVM donde corren.

## Lo que esto no arregla

- **Un teléfono con root comprometido mientras la app corre.** Los tokens
  están en memoria, y quien controla el sistema puede pedirle al almacén que
  descifre. Esto protege los ficheros, no un sistema ya tomado.
- **El verificador del SSO** (ADR 036) sigue en claro en sus propias
  preferencias. Vive lo que dura una ida al navegador y no vale sin el código
  que vuelve.
- **No se sabe en cuántos teléfonos cae al camino de «guardar como antes».**
  Solo queda en el registro del sistema de ese teléfono.

## Consecuencias

- La app sube a la 1.16 (versionCode 17).
- Una clase más y una dependencia menos de la que preocuparse: no entra
  ninguna librería.
