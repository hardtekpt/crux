<#
.SYNOPSIS
  One-stop dev script for Crux: build, test, and run the app on the Crux emulator.

.EXAMPLE
  .\crux run            # build, boot the emulator if needed, install and open the app
  .\crux test           # JVM unit tests + Robolectric Compose UI tests (no emulator)
  .\crux device-test    # instrumented Compose/Room tests on the emulator
  .\crux check          # test + device-test
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('run', 'build', 'test', 'device-test', 'check', 'emulator', 'stop', 'avd', 'help')]
    [string]$Command = 'help',
    # Show the emulator window (default) or run it headless, e.g. for test-only runs.
    [switch]$Headless
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$AvdName = 'Crux_Pixel_9'
$SystemImage = 'system-images;android-36.1;google_apis_playstore;x86_64'
$AppId = 'com.hardtekpt.crux.debug'
$Activity = 'com.hardtekpt.crux.MainActivity'

# --- Toolchain discovery --------------------------------------------------------
if (-not $env:JAVA_HOME) {
    $studioJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path $studioJbr) { $env:JAVA_HOME = $studioJbr }
    else { throw 'JAVA_HOME is not set and Android Studio''s bundled JDK was not found.' }
}
$Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path $Sdk)) { throw "Android SDK not found at $Sdk. Set ANDROID_HOME." }
$env:ANDROID_HOME = $Sdk
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'
$Emulator = Join-Path $Sdk 'emulator\emulator.exe'
$AvdManager = Join-Path $Sdk 'cmdline-tools\latest\bin\avdmanager.bat'

$localProps = Join-Path $Root 'local.properties'
if (-not (Test-Path $localProps)) {
    "sdk.dir=$($Sdk -replace '\\', '/')" | Set-Content -Encoding ascii $localProps
}

function Invoke-Gradle([string[]]$Tasks) {
    Push-Location $Root
    try {
        & (Join-Path $Root 'gradlew.bat') @Tasks
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $($Tasks -join ' ')" }
    } finally { Pop-Location }
}

function Get-RunningEmulator {
    $lines = & $Adb devices | Select-String '^emulator-\d+\s+device$'
    foreach ($line in $lines) {
        $serial = ($line -split '\s+')[0]
        $name = (& $Adb -s $serial emu avd name 2>$null | Select-Object -First 1)
        if ($name -and $name.Trim() -eq $AvdName) { return $serial }
    }
    return $null
}

function New-CruxAvd {
    $existing = & $Emulator -list-avds
    if ($existing -contains $AvdName) { return }
    Write-Host "Creating emulator '$AvdName'..."
    # cmd.exe splits unquoted ';' so the package id is passed pre-quoted.
    'no' | & $AvdManager create avd --name $AvdName --device pixel_9 --package "`"$SystemImage`"" --force | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'avdmanager failed to create the emulator.' }
}

function Start-CruxEmulator {
    $serial = Get-RunningEmulator
    if ($serial) { return $serial }
    New-CruxAvd
    $emuArgs = @('-avd', $AvdName, '-netdelay', 'none', '-netspeed', 'full')
    if ($Headless) { $emuArgs += @('-no-window', '-no-audio', '-no-boot-anim') }
    Write-Host "Booting '$AvdName'..."
    Start-Process -FilePath $Emulator -ArgumentList $emuArgs -WindowStyle Hidden | Out-Null

    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        $serial = Get-RunningEmulator
        if ($serial -and (& $Adb -s $serial shell getprop sys.boot_completed 2>$null) -match '1') {
            Write-Host "Emulator ready ($serial)."
            return $serial
        }
    }
    throw 'Timed out waiting for the emulator to boot.'
}

switch ($Command) {
    'build' { Invoke-Gradle @('assembleDebug') }
    'test' { Invoke-Gradle @('testDebugUnitTest') }
    'avd' { New-CruxAvd; Write-Host "Emulator '$AvdName' is ready to boot." }
    'emulator' { Start-CruxEmulator | Out-Null }
    'stop' {
        $serial = Get-RunningEmulator
        if ($serial) { & $Adb -s $serial emu kill | Out-Null; Write-Host 'Emulator stopped.' }
    }
    'device-test' {
        $serial = Start-CruxEmulator
        $env:ANDROID_SERIAL = $serial
        Invoke-Gradle @('connectedDebugAndroidTest')
    }
    'check' {
        Invoke-Gradle @('testDebugUnitTest')
        $env:ANDROID_SERIAL = Start-CruxEmulator
        Invoke-Gradle @('connectedDebugAndroidTest')
    }
    'run' {
        Invoke-Gradle @('assembleDebug')
        $serial = Start-CruxEmulator
        $apk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
        & $Adb -s $serial install -r $apk | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'APK install failed.' }
        & $Adb -s $serial shell am start -n "$AppId/$Activity" | Out-Null
        Write-Host "Crux is running on $serial."
    }
    default { Get-Help $PSCommandPath -Examples }
}
