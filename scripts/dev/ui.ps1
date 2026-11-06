# Ayudas para manejar el emulador de Android por el arbol de accesibilidad
# (uiautomator), nunca por coordenadas fijas: el teclado y los avisos
# recolocan la pantalla.
#
# Uso, desde PowerShell:
#   . .\scripts\dev\ui.ps1
#   Textos                      # lo que hay en pantalla
#   Tocar 'Entrar'              # toca el primer nodo cuyo texto coincide (regex)
#   Tocar 'Guardar' -Ultimo     # el ultimo, si el texto sale repetido (titulo y boton)
#   Escribir 0 'marta.sanchez@techcorp.demo'   # n-esimo campo de texto; solo ASCII
#   Tecla 4                     # 4 = atras / cerrar teclado; 66 = Intro (usar 66 dentro de un dialogo)
#   Captura 'C:\ruta\pantalla.png'

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

function Volcar {
    # Se borra antes: un volcado viejo de otra sesion engana.
    & $adb shell rm -f /sdcard/v.xml | Out-Null
    & $adb shell uiautomator dump /sdcard/v.xml | Out-Null
    return (& $adb shell cat /sdcard/v.xml) -join ''
}

function Textos {
    $x = Volcar
    ([regex]::Matches($x, '(text|content-desc)="([^"]+)"') |
        ForEach-Object { $_.Groups[2].Value } | Select-Object -Unique) -join ' | '
}

function Centro([string]$nodo) {
    $b = [regex]::Match($nodo, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    $cx = ([int]$b.Groups[1].Value + [int]$b.Groups[3].Value) / 2
    $cy = ([int]$b.Groups[2].Value + [int]$b.Groups[4].Value) / 2
    return @($cx, $cy)
}

function Tocar([string]$patron, [int]$n = 0, [switch]$Ultimo) {
    $x = Volcar
    $nodos = @([regex]::Matches($x, '<node [^>]*>') |
        Where-Object { $_.Value -match "(text|content-desc)=""$patron""" })
    if ($nodos.Count -eq 0) { throw "No encuentro '$patron'. En pantalla: $(Textos)" }
    $nodo = if ($Ultimo) { $nodos[-1] } else { $nodos[$n] }
    $c = Centro $nodo.Value
    & $adb shell input tap $c[0] $c[1] | Out-Null
}

function Escribir([int]$n, [string]$texto) {
    if ($texto -match '[^\x20-\x7E]') { throw 'adb input text solo admite ASCII: con tildes deja el campo vacio sin avisar.' }
    $x = Volcar
    $nodo = @([regex]::Matches($x, '<node [^>]*class="android.widget.EditText"[^>]*>'))[$n]
    if (-not $nodo) { throw "No hay campo de texto numero $n" }
    $c = Centro $nodo.Value
    & $adb shell input tap $c[0] $c[1] | Out-Null
    Start-Sleep -Milliseconds 800
    $escapado = ($texto -replace '@', '\@') -replace ' ', '%s'
    & $adb shell input text $escapado | Out-Null
}

function Tecla([int]$codigo) {
    & $adb shell input keyevent $codigo | Out-Null
}

function Captura([string]$destino) {
    & $adb shell screencap -p /sdcard/c.png | Out-Null
    & $adb pull /sdcard/c.png $destino | Out-Null
    if (-not (Test-Path $destino)) { throw "No se ha guardado la captura en $destino" }
}
