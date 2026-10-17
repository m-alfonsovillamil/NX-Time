<#
.SYNOPSIS
    Copia de seguridad de la base de producción de NX Time (Neon) a OneDrive.

.DESCRIPTION
    Paso 4 del piloto (09/2026).

    Neon, en el plan gratuito, solo deja volver 6 horas atrás y guarda un
    único snapshot manual, sin copias programadas. Eso cubre "acabo de romper
    algo", pero no "esto se borró la semana pasada" ni perder el acceso a la
    cuenta. Esta copia sí.

    Hace un pg_dump en formato custom, comprueba que el fichero se puede leer
    ANTES de darlo por bueno y borra las copias de más de -DiasDeRetencion
    días, sin bajar nunca de -MinimoDeCopias: si las copias dejaran de
    funcionar durante un mes, la retención no puede llevarse las últimas
    buenas.

    Las credenciales se leen de .env.local (ignorado por git) y se pasan a
    pg_dump por variables de entorno, no en la línea de comandos, donde las
    vería cualquier proceso del equipo.

    Deja un registro en copias.log y, si falla, un ULTIMA-COPIA-FALLIDA.txt
    en la carpeta de destino, que es lo que se ve al abrir OneDrive.

    CIFRADA desde el 17/10/2026 (plan J): la copia tiene los datos de todas
    las empresas, y OneDrive es otro proveedor más que la guarda. Se cifra
    con gpg (AES-256, el que trae Git para Windows) y una frase que vive
    FUERA de OneDrive (-ClaveFile, por defecto en %LOCALAPPDATA%). La copia
    sin cifrar solo existe un momento, en la carpeta temporal del equipo.

    Guarda también la frase en tu gestor de contraseñas: si se pierde el
    equipo, sin ella las copias de OneDrive no sirven de nada. Para crearla:
        New-Item -ItemType Directory -Force "$env:LOCALAPPDATA\NX Time"
        $b = New-Object byte[] 48
        [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
        [Convert]::ToBase64String($b) |
            Set-Content "$env:LOCALAPPDATA\NX Time\clave-de-las-copias.txt" -NoNewline

    Para restaurar una copia y comprobarla: scripts/probar-restauracion.sh
    (ver docs/DESPLIEGUE.md).

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\copia-neon.ps1
#>
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env.local'),
    [string]$Destino = (Join-Path $env:USERPROFILE 'OneDrive\Copias NX Time'),
    [int]$DiasDeRetencion = 30,
    [int]$MinimoDeCopias = 7,
    [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$ClaveFile = (Join-Path $env:LOCALAPPDATA 'NX Time\clave-de-las-copias.txt'),
    [string]$Gpg = 'C:\Program Files\Git\usr\bin\gpg.exe'
)

$ErrorActionPreference = 'Stop'

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$registro = Join-Path $Destino 'copias.log'
$aviso = Join-Path $Destino 'ULTIMA-COPIA-FALLIDA.txt'
$inicio = Get-Date

function Escribir-Registro([string]$linea) {
    Add-Content -Path $registro -Value ("{0:yyyy-MM-dd HH:mm:ss}  {1}" -f (Get-Date), $linea) -Encoding UTF8
}

# pg_dump y pg_restore se lanzan con Start-Process y no con `&`: en
# PowerShell 5.1, redirigir el stderr de un ejecutable nativo convierte cada
# línea en un ErrorRecord, y con ErrorActionPreference = Stop el script
# moriría con el primer aviso inofensivo.
function Ejecutar([string]$exe, [string[]]$argumentos, [string]$salida) {
    $errores = [IO.Path]::GetTempFileName()
    $parametros = @{
        FilePath               = $exe
        ArgumentList           = $argumentos
        NoNewWindow            = $true
        PassThru               = $true
        RedirectStandardError  = $errores
    }
    if ($salida) { $parametros.RedirectStandardOutput = $salida }
    $proceso = Start-Process @parametros
    # WaitForExit y no -Wait: -Wait espera también a los procesos que deja el
    # programa, y gpg arranca un gpg-agent que se queda vivo; la copia no
    # terminaba nunca. Y leer Handle antes: sin eso, en PowerShell 5.1
    # ExitCode sale vacío al terminar.
    $null = $proceso.Handle
    $proceso.WaitForExit()
    $textoError = (Get-Content $errores -Raw -ErrorAction SilentlyContinue)
    Remove-Item $errores -ErrorAction SilentlyContinue
    if ($proceso.ExitCode -ne 0) {
        throw "$(Split-Path $exe -Leaf) salió con código $($proceso.ExitCode): $textoError"
    }
}

$parcial = $null
$enClaro = $null
$comprobacion = $null
try {
    # --- La frase del cifrado, antes que nada: sin ella no se copia ------
    if (-not (Test-Path $ClaveFile)) { throw "No existe $ClaveFile (la frase con la que se cifran las copias)" }
    $rutaDeLaClave = (Get-Item $ClaveFile).FullName
    foreach ($carpeta in @($env:OneDrive, $env:OneDriveConsumer, $env:OneDriveCommercial)) {
        if ($carpeta -and $rutaDeLaClave.StartsWith($carpeta.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "La frase de las copias no puede estar dentro de OneDrive ($carpeta)"
        }
    }
    if ((Get-Item $ClaveFile).Length -lt 32) { throw "La frase de $ClaveFile es demasiado corta (mínimo 32 caracteres)" }
    if (-not (Test-Path $Gpg)) { throw "No existe $Gpg" }

    # --- Credenciales -------------------------------------------------
    if (-not (Test-Path $EnvFile)) { throw "No existe $EnvFile" }
    $variables = @{}
    foreach ($linea in Get-Content $EnvFile -Encoding UTF8) {
        # El fichero lleva BOM: sin quitarlo, la primera clave no se reconoce.
        $linea = $linea.TrimStart([char]0xFEFF).Trim()
        if ($linea -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $variables[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
        }
    }
    # La conexión DIRECTA, no la del pooler: PgBouncer en modo transacción
    # no admite el estado de sesión que usa pg_dump.
    $url = $variables['DATABASE_URL_UNPOOLED']
    if (-not $url) { throw "DATABASE_URL_UNPOOLED no está en $EnvFile" }

    $uri = [Uri]$url
    $usuario, $clave = $uri.UserInfo.Split(':', 2)
    $env:PGHOST = $uri.Host
    $env:PGPORT = if ($uri.Port -gt 0) { "$($uri.Port)" } else { '5432' }
    $env:PGUSER = [Uri]::UnescapeDataString($usuario)
    $env:PGPASSWORD = [Uri]::UnescapeDataString($clave)
    $env:PGDATABASE = $uri.AbsolutePath.TrimStart('/')
    # Cifrada siempre contra Neon; la URL puede pedir otra cosa (una base local de prueba).
    $env:PGSSLMODE = if ($uri.Query -match 'sslmode=([a-z-]+)') { $Matches[1] } else { 'require' }

    # --- Copia --------------------------------------------------------
    # Con segundos: la tarea que se pone al día al encender el PC y una copia
    # a mano en el mismo minuto chocarían al renombrar, y la buena quedaría
    # registrada como ERROR.
    $nombre = 'nxtime-{0:yyyy-MM-dd_HHmmss}.dump.gpg' -f $inicio
    $final = Join-Path $Destino $nombre
    # Se escribe con otro nombre y se renombra al final: un corte a mitad
    # no puede dejar un fichero que parezca una copia buena.
    $parcial = "$final.parcial"
    # La copia sin cifrar, solo en la carpeta temporal del equipo, fuera de OneDrive.
    $enClaro = Join-Path $env:TEMP ("nxtime-{0:yyyyMMddHHmmss}.dump" -f $inicio)

    Ejecutar (Join-Path $PgBin 'pg_dump.exe') @('--format=custom', "--file=`"$enClaro`"") $null

    # --- Comprobación -------------------------------------------------
    # Que pg_dump acabe con 0 no basta: se lee el índice del fichero y se
    # exige que traiga los datos de las tablas que importan.
    $indice = [IO.Path]::GetTempFileName()
    Ejecutar (Join-Path $PgBin 'pg_restore.exe') @('--list', "`"$enClaro`"") $indice
    $contenido = Get-Content $indice -Raw
    Remove-Item $indice
    foreach ($tabla in 'registros', 'auditoria_fichaje', 'usuarios', 'empresas', 'flyway_schema_history') {
        if ($contenido -notmatch "TABLE DATA public $tabla ") {
            throw "La copia no contiene los datos de la tabla $tabla"
        }
    }

    # --- Cifrado, y comprobar que se descifra en lo mismo ---------------
    $gpgComun = @('--batch', '--yes', '--quiet', '--pinentry-mode', 'loopback', '--passphrase-file', "`"$ClaveFile`"")
    Ejecutar $Gpg ($gpgComun + @('--symmetric', '--cipher-algo', 'AES256', '--output', "`"$parcial`"", "`"$enClaro`"")) $null
    $comprobacion = "$enClaro.descifrado"
    Ejecutar $Gpg ($gpgComun + @('--decrypt', '--output', "`"$comprobacion`"", "`"$parcial`"")) $null
    if ((Get-FileHash $comprobacion).Hash -ne (Get-FileHash $enClaro).Hash) {
        throw "La copia cifrada no se descifra en lo mismo que la original"
    }

    Move-Item $parcial $final
    $parcial = $null
    $tamano = (Get-Item $final).Length

    # --- Retención ----------------------------------------------------
    $copias = Get-ChildItem $Destino -Filter 'nxtime-*.dump.gpg' | Sort-Object LastWriteTime -Descending
    $borradas = 0
    $copias | Select-Object -Skip $MinimoDeCopias |
        Where-Object { $_.LastWriteTime -lt $inicio.AddDays(-$DiasDeRetencion) } |
        ForEach-Object { Remove-Item $_.FullName; $borradas++ }

    Remove-Item $aviso -ErrorAction SilentlyContinue
    Escribir-Registro ("OK     {0}  {1:N0} KB  {2:N1} s  borradas: {3}" -f $nombre, ($tamano / 1KB), ((Get-Date) - $inicio).TotalSeconds, $borradas)
    # Las de antes del cifrado no las borra el script: que lo decida una
    # persona. Pero que se vea que siguen ahí, sin cifrar.
    $enClaroViejas = @(Get-ChildItem $Destino -Filter 'nxtime-*.dump')
    if ($enClaroViejas.Count -gt 0) {
        Escribir-Registro ("AVISO  quedan {0} copias SIN CIFRAR de antes del 17/10/2026 (nxtime-*.dump): bórralas" -f $enClaroViejas.Count)
    }
    exit 0
}
catch {
    if ($parcial -and (Test-Path $parcial)) { Remove-Item $parcial -ErrorAction SilentlyContinue }
    $mensaje = $_.Exception.Message
    # Solo si ya hay contraseña: reemplazar la cadena vacía mete *** entre
    # cada carácter del mensaje (pasaba al fallar antes de leer .env.local).
    if ($env:PGPASSWORD) { $mensaje = $mensaje.Replace($env:PGPASSWORD, '***') }
    Escribir-Registro "ERROR  $mensaje"
    Set-Content -Path $aviso -Encoding UTF8 -Value @(
        "La copia de NX Time del $('{0:dd/MM/yyyy HH:mm}' -f $inicio) ha FALLADO.",
        '',
        $mensaje,
        '',
        "Historial completo en copias.log. Este aviso desaparece con la siguiente copia buena."
    )
    exit 1
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    # La copia en claro no se queda en el equipo, haya ido bien o mal.
    foreach ($temporal in @($enClaro, $comprobacion)) {
        if ($temporal -and (Test-Path $temporal)) { Remove-Item $temporal -Force -ErrorAction SilentlyContinue }
    }
    # Ni el gpg-agent que arranca gpg: se quedaría vivo hasta reiniciar.
    $gpgconf = Join-Path (Split-Path $Gpg) 'gpgconf.exe'
    if (Test-Path $gpgconf) {
        try { Start-Process $gpgconf -ArgumentList '--kill', 'gpg-agent' -NoNewWindow -Wait } catch { }
    }
}
