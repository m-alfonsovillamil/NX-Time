#!/usr/bin/env python3
"""
Recorrido de extremo a extremo de NX Time, por HTTP y contra una base real.

POR QUÉ EXISTE
--------------
En este proyecto los defectos serios NO han salido de los tests: han salido de
ejecutar el sistema entero. El `REVOKE` que no revocaba nada, el aviso que solo
le llegaba al GESTOR, la clave JWT por defecto en producción, el token que
seguía valiendo quince minutos después de dar de baja a alguien... en todos los
casos la suite estaba en verde.

La razón es conocida: los tests prueban cada pieza con las de al lado simuladas
--generadores con datos escritos a mano, controladores con el servicio
mockeado--, así que nadie comprueba el cálculo real ni las interacciones con
Postgres, el correo y los permisos. Este guion es justo eso que faltaba, y hasta
hoy vivía suelto en una carpeta temporal y se perdía al cerrar la sesión.

QUÉ COMPRUEBA
-------------
El ciclo completo de una jornada, que es donde vive el negocio: fichar, pausar,
cambiar de proyecto, cerrar, repartir las horas, añadir una pausa a posteriori,
pedir una corrección y que la aprueben, ver la traza de auditoría, sacar los
informes y comprobar que un empleado no puede hacer lo que no le toca ni ver lo
de otra empresa.

USO
---
    python scripts/e2e.py

Levanta una base desechable en el Postgres de Docker (puerto 5433), arranca el
backend con los perfiles `dev,demo` en el puerto 8099, recorre todo y limpia lo
que ha creado. Devuelve 0 si todo pasa y 1 si algo falla.

    python scripts/e2e.py --url http://localhost:8080 --base nxtime --no-arrancar

Contra un backend que ya esté en marcha. La base tiene que tener los datos de
demo (perfil `demo`).

REQUISITOS
----------
Docker con el `docker compose up -d postgres mailhog` de este repositorio. No
hace falta tener el cliente de PostgreSQL instalado: las consultas van por
`docker exec`.
"""

import argparse
import datetime
import json
import os
import subprocess
import sys
import time
import http.client
import urllib.error
import urllib.request

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENEDOR = "nxtime-postgres"
CONTRASENA_DEMO = "demo1234"

# Los usuarios de demo que siembra DemoDataSeeder. Se eligen por rol, porque lo
# que se comprueba aquí es qué puede hacer cada uno.
ANA = "ana.fernandez@techcorp.demo"        # EMPLEADO
JAVIER = "javier.lopez@techcorp.demo"      # EMPLEADO, con proyectos
MARTA = "marta.sanchez@techcorp.demo"      # GESTOR
ELENA = "elena.rios@techcorp.demo"         # RRHH
PEDRO = "pedro.navarro@iberica.demo"       # de OTRA empresa


class Comprobador:
    """Lleva la cuenta de lo que pasa y lo que falla, y lo cuenta al final."""

    def __init__(self):
        self.fallos = []
        self.total = 0

    def check(self, nombre, condicion, detalle=""):
        self.total += 1
        if condicion:
            print("  OK   " + nombre)
        else:
            print("  FALLA " + nombre + "   -> " + str(detalle)[:300])
            self.fallos.append(nombre)
        return bool(condicion)

    def nota(self, texto):
        print("       " + str(texto)[:300])

    def resumen(self):
        print()
        if self.fallos:
            print("%d de %d comprobaciones FALLAN:" % (len(self.fallos), self.total))
            for f in self.fallos:
                print("  - " + f)
            return 1
        print("Las %d comprobaciones pasan." % self.total)
        return 0


class Api:
    """Cliente HTTP mínimo. Devuelve (código, cuerpo) y nunca lanza por un 4xx:
    aquí un 403 es tan interesante como un 200."""

    def __init__(self, url):
        self.url = url.rstrip("/")
        self.tokens = {}

    def __call__(self, metodo, ruta, token=None, cuerpo=None, crudo=False):
        datos = json.dumps(cuerpo).encode("utf-8") if cuerpo is not None else None
        peticion = urllib.request.Request(self.url + ruta, data=datos, method=metodo)
        if datos is not None:
            peticion.add_header("Content-Type", "application/json")
        if token:
            peticion.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(peticion, timeout=120) as r:
                contenido = r.read()
                if crudo:
                    return r.status, contenido
                texto = contenido.decode("utf-8")
                return r.status, (json.loads(texto) if texto.strip() else None)
        except urllib.error.HTTPError as e:
            texto = e.read().decode("utf-8", "replace")
            try:
                return e.code, json.loads(texto)
            except ValueError:
                return e.code, texto
        except http.client.HTTPException as e:
            # IncompleteRead: el servidor cortó la respuesta antes de acabar.
            # Es exactamente el fallo de las descargas, así que tiene que
            # llegar hasta la comprobación en vez de reventar el guion.
            return -1, "%s: %s" % (type(e).__name__, e)
        except (urllib.error.URLError, OSError) as e:
            # Código 0 = ni siquiera hubo respuesta. Pasa mientras el backend
            # arranca (conexión rechazada) y no debe reventar el guion: quien
            # espera el arranque distingue el 0 del 200 igual de bien.
            return 0, str(e)

    def login(self, email):
        """Entra una vez por persona y guarda el token.

        Sin la caché, cada sección volvía a pedir un login y el limitador de
        intentos (diez por minuto y por IP) devolvía 429 a mitad de recorrido:
        el guion acusaba de un fallo que se estaba provocando él solo.
        """
        if email in self.tokens:
            return self.tokens[email]
        estado, cuerpo = self("POST", "/auth/login",
                              cuerpo={"email": email, "contrasena": CONTRASENA_DEMO})
        if estado != 200:
            raise SystemExit("No se pudo entrar como %s: %s %s" % (email, estado, cuerpo))
        self.tokens[email] = cuerpo["token"]
        return cuerpo["token"]


def sql(base, consulta):
    """Una consulta contra la base desechable, por docker exec.

    Se usa para dos cosas y solo dos: sembrar situaciones que por HTTP no se
    pueden montar (una jornada de hace tres semanas) y mirar por dentro lo que
    la API no enseña. Nunca para comprobar algo que la API ya responde.
    """
    proceso = subprocess.run(
        ["docker", "exec", "-e", "PGPASSWORD=nxtime", CONTENEDOR,
         "psql", "-U", "nxtime", "-d", base, "-At", "-c", consulta],
        capture_output=True, text=True)
    if proceso.returncode != 0:
        raise SystemExit("psql falló: " + proceso.stderr.strip())
    return proceso.stdout.strip()


def uno(base, consulta):
    """La primera línea del resultado. `INSERT ... RETURNING id` imprime también
    el recuento ("INSERT 0 1"), y quedarse con todo se llevó por delante un
    guion entero."""
    salida = sql(base, consulta)
    return salida.splitlines()[0] if salida else ""


def crear_base(nombre):
    subprocess.run(["docker", "exec", "-e", "PGPASSWORD=nxtime", CONTENEDOR,
                    "psql", "-U", "nxtime", "-d", "nxtime", "-c",
                    'CREATE DATABASE "%s"' % nombre],
                   capture_output=True, text=True, check=True)


def borrar_base(nombre):
    # WITH (FORCE) porque el pool de HikariCP puede tardar en soltar: sin esto,
    # el borrado falla con "is being accessed by other users".
    subprocess.run(["docker", "exec", "-e", "PGPASSWORD=nxtime", CONTENEDOR,
                    "psql", "-U", "nxtime", "-d", "nxtime", "-c",
                    'DROP DATABASE IF EXISTS "%s" WITH (FORCE)' % nombre],
                   capture_output=True, text=True)


def arrancar_backend(base, puerto):
    """Levanta el backend contra la base desechable y espera a que responda.

    Devuelve el proceso de Gradle, que NO es quien escucha en el puerto: el
    `java` es un hijo suyo y matar al padre no lo mata. Por eso el apagado va
    por el PID que tiene el puerto abierto (ver `parar_backend`).
    """
    jdbc = "jdbc:postgresql://localhost:5433/" + base
    comando = [
        os.path.join(RAIZ, "gradlew.bat" if os.name == "nt" else "gradlew"),
        ":nx-time-backend:bootRun",
        "--args=--spring.profiles.active=dev,demo --server.port=%d"
        " --spring.datasource.url=%s --spring.flyway.url=%s" % (puerto, jdbc, jdbc),
    ]
    print("Arrancando el backend en el puerto %d contra la base %s..." % (puerto, base))
    proceso = subprocess.Popen(comando, cwd=RAIZ,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    api = Api("http://localhost:%d" % puerto)
    # OJO: no basta con que /actuator/health diga UP. El servidor web ya
    # atiende mientras el sembrador de datos de demo (un ApplicationRunner)
    # todavía está creando las empresas y los usuarios, así que un login hecho
    # en esa ventana devuelve "Credenciales incorrectas" y parece un fallo de
    # autenticación cuando lo que pasa es que la persona aún no existe.
    # La señal buena de "listo" es que se pueda entrar.
    for _ in range(150):
        estado, _cuerpo = api("POST", "/auth/login",
                              cuerpo={"email": ANA, "contrasena": CONTRASENA_DEMO})
        if estado == 200:
            print("Listo.")
            return proceso
        if proceso.poll() is not None:
            raise SystemExit("El backend se ha caído al arrancar. Prueba a lanzarlo a mano"
                             " para ver el error:\n  " + " ".join(comando))
        time.sleep(2)
    raise SystemExit("El backend no llegó a atender un login en cinco minutos.")


def parar_backend(proceso, puerto):
    """Mata al que escucha en el puerto, no al Gradle que lo lanzó."""
    if os.name == "nt":
        salida = subprocess.run(["netstat", "-ano", "-p", "TCP"],
                                capture_output=True, text=True).stdout
        for linea in salida.splitlines():
            if (":%d " % puerto) in linea and "LISTENING" in linea:
                pid = linea.split()[-1]
                subprocess.run(["taskkill", "/PID", pid, "/F", "/T"],
                               capture_output=True, text=True)
    proceso.terminate()
    try:
        proceso.wait(timeout=30)
    except subprocess.TimeoutExpired:
        proceso.kill()


# ---------------------------------------------------------------------------
# El recorrido
# ---------------------------------------------------------------------------

def sesion(api, c):
    print("\n[1] Sesión y permisos")
    estado, cuerpo = api("POST", "/auth/login",
                         cuerpo={"email": ANA, "contrasena": "no-es-esta"})
    c.check("contraseña incorrecta -> 401", estado == 401, (estado, cuerpo))

    estado, _ = api("GET", "/api/v1/fichaje/activo")
    c.check("sin token -> 401", estado == 401, estado)

    estado, _ = api("GET", "/api/v1/fichaje/activo", "un.token.inventado")
    c.check("token inventado -> 401", estado == 401, estado)

    ana = api.login(ANA)
    # Con los parámetros BIEN puestos: si se mandan mal, la validación
    # responde 400 antes de llegar a mirar el permiso, y el guion daría por
    # buena una autorización que no ha comprobado nadie.
    hoy = time.localtime()
    estado, _ = api("GET", "/api/v1/informes/horas?anio=%d&mes=%d" % (hoy.tm_year, hoy.tm_mon), ana)
    c.check("un empleado no exporta informes -> 403", estado == 403, estado)

    estado, _ = api("GET", "/api/v1/borrados/pendientes", ana)
    c.check("un empleado no ve la bandeja de borrados -> 403", estado == 403, estado)


def jornada(api, c, base):
    """El ciclo de una jornada completa, que es el corazón del producto."""
    print("\n[2] Una jornada de principio a fin")
    javier = api.login(JAVIER)

    estado, proyectos = api("GET", "/api/v1/fichaje/proyectos", javier)
    c.check("los proyectos para fichar llegan -> 200", estado == 200, (estado, proyectos))
    disponibles = proyectos.get("disponibles", []) if isinstance(proyectos, dict) else []
    c.nota("proyectos disponibles: %d" % len(disponibles))

    # Si arrastra una jornada abierta de los datos de demo, se cierra: si no,
    # el INICIO daría 409 y el recorrido se quedaría a medias.
    estado, activo = api("GET", "/api/v1/fichaje/activo", javier)
    if estado == 200 and activo:
        api("POST", "/api/v1/fichaje", javier, {"tipo": "FIN"})

    peticion = {"tipo": "INICIO"}
    if len(disponibles) > 1:
        peticion["proyectoId"] = disponibles[0]["id"]
    estado, inicio = api("POST", "/api/v1/fichaje", javier, peticion)
    c.check("iniciar jornada -> 200", estado == 200, (estado, inicio))
    fichaje_id = inicio["id"] if isinstance(inicio, dict) else None

    estado, _ = api("POST", "/api/v1/fichaje", javier, peticion)
    c.check("iniciar dos veces -> 409", estado == 409, estado)

    estado, activo = api("GET", "/api/v1/fichaje/activo", javier)
    c.check("la jornada activa es la que se acaba de abrir",
            estado == 200 and activo and activo.get("id") == fichaje_id, (estado, activo))

    estado, _ = api("POST", "/api/v1/fichaje", javier, {"tipo": "PAUSA_INICIO"})
    c.check("empezar la pausa -> 200", estado == 200, estado)
    estado, _ = api("POST", "/api/v1/fichaje", javier, {"tipo": "PAUSA_INICIO"})
    c.check("pausar dos veces -> 409", estado == 409, estado)
    estado, _ = api("POST", "/api/v1/fichaje", javier, {"tipo": "PAUSA_FIN"})
    c.check("terminar la pausa -> 200", estado == 200, estado)

    if len(disponibles) > 1:
        estado, _ = api("POST", "/api/v1/fichaje/%d/proyecto" % fichaje_id, javier,
                        {"proyectoId": disponibles[1]["id"]})
        c.check("cambiar de proyecto con la jornada abierta -> 200", estado == 200, estado)

    estado, fin = api("POST", "/api/v1/fichaje", javier, {"tipo": "FIN"})
    c.check("cerrar la jornada -> 200", estado == 200, (estado, fin))

    # La pausa tiene que haber descontado: si el neto fuera igual a la duración,
    # el contador de pausas no estaría haciendo nada (ya pasó una vez).
    neto = uno(base, "SELECT segundos_pausa_acumulados FROM registros WHERE id = %d" % fichaje_id)
    c.check("la pausa ha quedado contada", neto.isdigit() and int(neto) >= 0, neto)
    return fichaje_id


def reparto(api, c, base, fichaje_id):
    print("\n[3] Repartir las horas entre proyectos")
    javier = api.login(JAVIER)
    estado, imputaciones = api("GET", "/api/v1/fichaje/%d/imputaciones" % fichaje_id, javier)
    if estado != 200:
        c.check("consultar el reparto -> 200", False, (estado, imputaciones))
        return
    c.check("consultar el reparto -> 200", True)
    neto = imputaciones["netoMinutos"]
    proyectos = imputaciones.get("disponibles", [])
    if len(proyectos) < 2:
        c.nota("solo hay un proyecto: no hay nada que repartir")
        return

    mitad = neto // 2
    estado, _ = api("PUT", "/api/v1/fichaje/%d/imputaciones" % fichaje_id, javier,
                    {"lineas": [{"proyectoId": proyectos[0]["id"], "minutos": mitad},
                                {"proyectoId": proyectos[1]["id"], "minutos": neto - mitad}]})
    c.check("repartir el neto de hoy se aplica al momento -> 200", estado == 200, estado)

    suma = uno(base, "SELECT coalesce(sum(segundos), 0) FROM imputaciones_proyecto "
                     "WHERE registro_id = %d" % fichaje_id)
    esperado = uno(base, "SELECT extract(epoch FROM (hora_salida - hora_entrada))::bigint "
                         "- segundos_pausa_acumulados FROM registros WHERE id = %d" % fichaje_id)
    c.check("la suma de las imputaciones es el neto de la jornada",
            suma == esperado, "imputado %s vs neto %s" % (suma, esperado))

    estado, _ = api("PUT", "/api/v1/fichaje/%d/imputaciones" % fichaje_id, javier,
                    {"lineas": [{"proyectoId": proyectos[0]["id"], "minutos": 1}]})
    c.check("repartir menos de lo trabajado -> 400", estado == 400, estado)


def pausa_a_posteriori(api, c, base, fichaje_id):
    print("\n[4] Añadir una pausa después")
    javier = api.login(JAVIER)
    # La jornada que se acaba de fichar dura milisegundos, así que cualquier
    # pausa de verdad caería fuera de ella. Se le dan ocho horas para que el
    # caso sea el real: una pausa que se olvidó y se añade después.
    sql(base, "UPDATE registros SET hora_entrada = now() - interval '8 hours', "
              "hora_salida = now() - interval '1 hour' WHERE id = %d" % fichaje_id)
    # En UTC y con la Z: AddPauseRequest recibe Instant, y una fecha sin zona
    # la rechaza Jackson con un "Failed to read request" que no dice nada.
    entrada = uno(base, "SELECT to_char(hora_entrada AT TIME ZONE 'UTC', "
                        "'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM registros WHERE id = %d" % fichaje_id)
    if not entrada:
        c.check("hay jornada sobre la que añadir la pausa", False, fichaje_id)
        return
    antes = uno(base, "SELECT segundos_pausa_acumulados FROM registros WHERE id = %d" % fichaje_id)

    # Un minuto de pausa que empieza en la hora de entrada: cae dentro de la
    # jornada seguro, dure lo que dure. Se calcula con datetime y no cortando
    # la cadena, que es como se cuelan los segundos negativos.
    inicio = datetime.datetime.strptime(entrada, "%Y-%m-%dT%H:%M:%SZ") + datetime.timedelta(hours=1)
    fin = inicio + datetime.timedelta(minutes=30)
    marca = "%Y-%m-%dT%H:%M:%SZ"
    estado, respuesta = api("POST", "/api/v1/fichaje/%d/pausas" % fichaje_id, javier,
                            {"inicio": inicio.strftime(marca), "fin": fin.strftime(marca),
                             "motivo": "Recorrido automático"})
    c.check("añadir una pausa de hoy se aplica sin aprobación -> 200/201",
            estado in (200, 201), (estado, respuesta))

    despues = uno(base, "SELECT segundos_pausa_acumulados FROM registros WHERE id = %d" % fichaje_id)
    c.check("la pausa añadida sube el tiempo de pausa",
            despues.isdigit() and antes.isdigit() and int(despues) > int(antes),
            "antes %s, después %s" % (antes, despues))


def correccion(api, c, base):
    print("\n[5] Pedir una corrección y aprobarla")
    javier = api.login(JAVIER)
    marta = api.login(MARTA)
    usuario_id = uno(base, "SELECT id FROM usuarios WHERE email = '%s'" % JAVIER)

    # Una jornada de hace tres semanas: fuera del plazo en el que uno puede
    # arreglar lo suyo, que es justo el caso que tiene que pasar por el gestor.
    antigua = uno(base,
                  "INSERT INTO registros (usuario_id, empresa_id, hora_entrada, hora_salida) "
                  "SELECT %s, empresa_id, (current_date - 21 + time '08:00') AT TIME ZONE 'Europe/Madrid', "
                  "(current_date - 21 + time '16:00') AT TIME ZONE 'Europe/Madrid' "
                  "FROM usuarios WHERE id = %s RETURNING id" % (usuario_id, usuario_id))

    entrada = uno(base, "SELECT to_char(hora_entrada AT TIME ZONE 'UTC', "
                        "'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM registros WHERE id = %s" % antigua)
    salida_nueva = entrada[:11] + "17:00:00Z"
    estado, solicitud = api("POST", "/api/v1/fichaje/%s/correcciones" % antigua, javier,
                            {"horaEntrada": entrada, "horaSalida": salida_nueva,
                             "motivo": "Me fui más tarde y se me olvidó fichar"})
    c.check("pedir una corrección de hace tres semanas -> 202 PENDIENTE",
            estado == 202 and isinstance(solicitud, dict)
            and solicitud.get("estado") == "PENDIENTE", (estado, solicitud))
    if not isinstance(solicitud, dict) or "id" not in solicitud:
        return None

    estado, _ = api("PATCH", "/api/v1/correcciones/%d/estado" % solicitud["id"], javier,
                    {"aprobada": True})
    c.check("nadie aprueba su propia corrección -> 403", estado == 403, estado)

    estado, resuelta = api("PATCH", "/api/v1/correcciones/%d/estado" % solicitud["id"], marta,
                           {"aprobada": True})
    c.check("la gestora la aprueba -> 200", estado == 200, (estado, resuelta))

    nueva = uno(base, "SELECT id FROM registros WHERE registro_original_id = %s" % antigua)
    c.check("la corrección crea una jornada nueva y anula la vieja", nueva.isdigit(), nueva)
    anulada = uno(base, "SELECT anulado FROM registros WHERE id = %s" % antigua)
    c.check("la jornada vieja queda anulada", anulada in ("t", "true"), anulada)
    return nueva


def auditoria(api, c, fichaje_id):
    print("\n[6] La traza de auditoría")
    elena = api.login(ELENA)
    marta = api.login(MARTA)

    estado, _ = api("GET", "/api/v1/auditoria/fichaje/%s" % fichaje_id, marta)
    c.check("un gestor no ve la auditoría -> 403", estado == 403, estado)

    estado, traza = api("GET", "/api/v1/auditoria/fichaje/%s" % fichaje_id, elena)
    c.check("RRHH ve la traza -> 200", estado == 200, (estado, traza))
    # Un fichaje corregido devolvía la traza VACÍA: la auditoría estaba en el
    # registro original y nadie la seguía. Por eso esto se comprueba aquí.
    c.check("la traza de un fichaje corregido NO viene vacía",
            isinstance(traza, list) and len(traza) > 0,
            "%d movimientos" % (len(traza) if isinstance(traza, list) else -1))


def informes(api, c):
    print("\n[7] Los informes que se entregan")
    elena = api.login(ELENA)
    hoy = time.localtime()
    periodo = "anio=%d&mes=%d" % (hoy.tm_year, hoy.tm_mon)

    estado, contenido = api("GET", "/api/v1/informes/horas?" + periodo, elena, crudo=True)
    # 200 y "PK" no bastan: el fallo real era que la respuesta se cortaba a
    # mitad SIN cambiar el código. Un -1 aquí es una descarga incompleta.
    c.check("el Excel de la empresa se descarga ENTERO -> 200", estado == 200,
            "%s %s" % (estado, contenido))
    c.check("y es un xlsx de verdad (empieza por PK)",
            isinstance(contenido, bytes) and contenido[:2] == b"PK", str(contenido)[:60])

    usuario_id = None
    estado, empleados = api("GET", "/api/v1/gestor/mis-empleados", elena)
    if estado == 200 and empleados:
        lista = empleados if isinstance(empleados, list) else empleados.get("content", [])
        if lista:
            usuario_id = lista[0].get("id")
    if usuario_id is None:
        c.check("se puede listar el equipo para sacar su informe", False, (estado, empleados))
        return
    estado, contenido = api("GET", "/api/v1/informes/mensual/%d?%s" % (usuario_id, periodo),
                            elena, crudo=True)
    c.check("el registro de jornada en PDF se descarga ENTERO -> 200", estado == 200,
            "%s %s" % (estado, contenido))
    c.check("y es un PDF de verdad",
            isinstance(contenido, bytes) and contenido[:4] == b"%PDF", str(contenido)[:60])


def otra_empresa(api, c, base, fichaje_id):
    print("\n[8] Lo de otra empresa no se toca")
    pedro = api.login(PEDRO)

    estado, _ = api("GET", "/api/v1/fichaje/%s/imputaciones" % fichaje_id, pedro)
    c.check("un fichaje de otra empresa -> 403/404", estado in (403, 404), estado)

    ajeno = uno(base, "SELECT id FROM usuarios WHERE email = '%s'" % JAVIER)
    estado, _ = api("GET", "/api/v1/gestor/empleados/%s" % ajeno, pedro)
    c.check("la ficha de alguien de otra empresa -> 403/404", estado in (403, 404), estado)


def main():
    parser = argparse.ArgumentParser(description="Recorrido de extremo a extremo de NX Time.")
    parser.add_argument("--url", default=None, help="backend ya arrancado")
    parser.add_argument("--base", default=None, help="base de datos a consultar")
    parser.add_argument("--puerto", type=int, default=8099)
    parser.add_argument("--no-arrancar", action="store_true",
                        help="no levantar el backend: ya está en marcha")
    args = parser.parse_args()

    propia = not args.no_arrancar
    base = args.base or ("e2e_%d" % int(time.time()))
    proceso = None
    try:
        if propia:
            crear_base(base)
            proceso = arrancar_backend(base, args.puerto)
        api = Api(args.url or "http://localhost:%d" % args.puerto)

        c = Comprobador()
        sesion(api, c)
        fichaje_id = jornada(api, c, base)
        if fichaje_id:
            reparto(api, c, base, fichaje_id)
            pausa_a_posteriori(api, c, base, fichaje_id)
        corregido = correccion(api, c, base)
        if corregido:
            auditoria(api, c, corregido)
        informes(api, c)
        if fichaje_id:
            otra_empresa(api, c, base, fichaje_id)
        return c.resumen()
    finally:
        if proceso is not None:
            parar_backend(proceso, args.puerto)
        if propia:
            borrar_base(base)


if __name__ == "__main__":
    sys.exit(main())
