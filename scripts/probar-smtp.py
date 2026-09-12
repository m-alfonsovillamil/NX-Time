"""Prueba las credenciales SMTP contra el servidor de correo, y nada más.

Por qué existe: en producción `/auth/recuperar` devuelve 202 aunque el
envío falle -- es deliberado, para no revelar qué correos tienen cuenta
(ADR 014) --, así que desde fuera no se distingue "enviado" de "Gmail me
ha rechazado". Esto pregunta directamente y enseña la respuesta del
servidor.

Las credenciales se leen de `.env.local`, que está fuera de git: así no
hace falta escribirlas en ningún sitio compartido. El script **no imprime
la contraseña**, solo la respuesta del servidor.

    python scripts/probar-smtp.py

Lee de .env.local: MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD y,
si está, MAIL_FROM. Con --enviar manda además un correo de prueba al
propio remitente.
"""
import os
import smtplib
import sys
from email.message import EmailMessage
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ENV = RAIZ / ".env.local"


def leer_env() -> dict:
    """Lee .env.local sin dependencias externas.

    Tolera el BOM que deja el Bloc de notas en Windows: ya ha costado un
    rato antes en este proyecto.
    """
    if not ENV.exists():
        sys.exit(f"No encuentro {ENV}. Añade ahí MAIL_USERNAME y MAIL_PASSWORD.")

    valores = {}
    for linea in ENV.read_text(encoding="utf-8-sig").splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        clave, valor = linea.split("=", 1)
        valores[clave.strip()] = valor.strip().strip('"').strip("'")
    return valores


def main() -> None:
    env = leer_env()
    host = env.get("MAIL_HOST", "smtp.gmail.com")
    puerto = int(env.get("MAIL_PORT", "587"))
    usuario = env.get("MAIL_USERNAME")
    clave = env.get("MAIL_PASSWORD")
    remitente = env.get("MAIL_FROM", usuario)

    if not usuario or not clave:
        sys.exit("Faltan MAIL_USERNAME o MAIL_PASSWORD en .env.local.")

    # Pistas sobre la FORMA de la contraseña, sin enseñarla: una de
    # aplicación de Google son 16 caracteres sin espacios, y pegarla con
    # los espacios que muestra Google es el error más común.
    print(f"host      : {host}:{puerto}")
    print(f"usuario   : {usuario}")
    print(f"remitente : {remitente}")
    print(f"contraseña: {len(clave)} caracteres"
          f"{', CON ESPACIOS (sospechoso)' if ' ' in clave else ', sin espacios'}")
    if len(clave) != 16:
        print("            OJO: las de Google son 16; las de Brevo son mucho mas largas")
    print()

    try:
        with smtplib.SMTP(host, puerto, timeout=20) as smtp:
            smtp.ehlo()
            smtp.starttls()
            smtp.ehlo()
            print("STARTTLS   : OK")
            smtp.login(usuario, clave)
            print("AUTENTICACIÓN: OK — las credenciales son válidas")

            if "--enviar" in sys.argv:
                mensaje = EmailMessage()
                mensaje["Subject"] = "NX Time: prueba de SMTP"
                mensaje["From"] = remitente
                mensaje["To"] = usuario
                mensaje.set_content(
                    "Si lees esto, el correo de NX Time funciona.\n"
                    "Lo manda scripts/probar-smtp.py."
                )
                smtp.send_message(mensaje)
                print(f"ENVÍO      : OK — mira la bandeja de {usuario}")
    except smtplib.SMTPAuthenticationError as e:
        print(f"AUTENTICACIÓN: RECHAZADA por el servidor\n  {e.smtp_code} {e.smtp_error!r}")
        print("\n  535 significa que el par usuario/contraseña no vale. Y ojo:")
        print("  el fallo puede estar en el USUARIO, no en la clave.")
        print("   - Gmail: el usuario es tu correo y la clave, una DE APLICACIÓN (16).")
        print("   - Brevo: el usuario NO es tu correo, es el 'login' que da su")
        print("     pantalla de SMTP (algo como 9xxxxxx001@smtp-brevo.com).")
        sys.exit(1)
    except Exception as e:  # noqa: BLE001 -- aquí interesa cualquier fallo
        print(f"FALLO: {type(e).__name__}: {e}")
        print("\n  Si es un tiempo de espera agotado, el problema no son las")
        print("  credenciales sino la salida al puerto 587.")
        sys.exit(1)


if __name__ == "__main__":
    main()
