<#
.SYNOPSIS
    Harmonia Mobile — Fast PC-Based Development & Emulator Workflow
.DESCRIPTION
    Manages the Android emulator, rapid incremental builds, app installation,
    and testing without needing a physical device.

.PARAMETER Action
    run            : Verify emulator, build/install debug APK incrementally, and launch app (default)
    start-emulator : Launch the Android emulator in the background and wait until boot completes
    relaunch       : Quickly restart Harmonia on the emulator without rebuilding
    test           : Run full JVM unit test suite (391+ tests)
    status         : Inspect emulator connection, installed package version, and running process
    screenshot     : Capture an instant PNG screenshot from the emulator to test-artifacts

.PARAMETER Clean
    If specified, uninstalls the existing app package before installing (resets app database/state).

.PARAMETER NoLaunch
    If specified, builds and installs without launching the MainActivity.

.PARAMETER AvdName
    Name of the AVD to launch (default: medium_phone).

.EXAMPLE
    .\tools\mobile_dev.ps1
    .\tools\mobile_dev.ps1 -Action relaunch
    .\tools\mobile_dev.ps1 -Action status
    .\tools\mobile_dev.ps1 -Action screenshot
    .\tools\mobile_dev.ps1 -Action test
    .\tools\mobile_dev.ps1 -Clean
#>

[CmdletBinding()]
param (
    [ValidateSet("run", "start-emulator", "relaunch", "test", "status", "screenshot")]
    [string]$Action = "run",

    [switch]$Clean,
    [switch]$NoLaunch,
    [string]$AvdName = "medium_phone"
)

$ErrorActionPreference = "Stop"

# ── 1. Locate Directories and Tools ──────────────────────────────────────────
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Get-Item $scriptDir).Parent.FullName
$mobileDir = Join-Path $repoRoot "harmonia-mobile"

# Locate Android SDK
$sdkDir = $null
if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $sdkDir = $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
    $sdkDir = $env:ANDROID_SDK_ROOT
} elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk") {
    $sdkDir = "$env:LOCALAPPDATA\Android\Sdk"
} else {
    # Check local.properties
    $localProps = Join-Path $mobileDir "local.properties"
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match "^sdk\.dir\s*=\s*(.*)" } | Select-Object -First 1
        if ($line -match "^sdk\.dir\s*=\s*(.*)") {
            $parsed = $matches[1].Replace("\:", ":").Replace("\\", "\")
            if (Test-Path $parsed) { $sdkDir = $parsed }
        }
    }
}

if (-not $sdkDir) {
    Write-Error "Android SDK not found! Please set ANDROID_HOME or check $mobileDir\local.properties."
    exit 1
}

$adb = Join-Path $sdkDir "platform-tools\adb.exe"
$emulator = Join-Path $sdkDir "emulator\emulator.exe"

if (-not (Test-Path $adb)) {
    Write-Error "ADB executable not found at: $adb"
    exit 1
}

function Get-RunningEmulator {
    $raw = & $adb devices
    $devicesText = if ($raw) { $raw -join "`n" } else { "" }
    if ($devicesText -match "(emulator-\d+)\s+device") {
        return $matches[1]
    }
    return $null
}

function Ensure-EmulatorRunning {
    param([string]$targetAvd)

    $emu = Get-RunningEmulator
    if ($emu) {
        Write-Host "[OK] Emulator is online: $emu" -ForegroundColor Green
        return $emu
    }

    Write-Host "[*] No active emulator detected. Starting '$targetAvd' in background..." -ForegroundColor Yellow
    if (-not (Test-Path $emulator)) {
        Write-Error "Emulator executable not found at: $emulator"
        exit 1
    }

    # Verify AVD exists
    $avdList = & $emulator -list-avds
    if ($avdList -notmatch $targetAvd) {
        Write-Error "AVD '$targetAvd' was not found in: `n$avdList"
        exit 1
    }

    Start-Process $emulator -ArgumentList "-avd $targetAvd -no-audio -no-boot-anim"
    Write-Host "[*] Waiting for emulator device to register with ADB..." -ForegroundColor Yellow
    & $adb wait-for-device

    Write-Host "[*] Waiting for Android system boot to complete..." -ForegroundColor Yellow
    $timeout = 90
    $elapsed = 0
    while ($elapsed -lt $timeout) {
        try {
            $boot = (& $adb shell getprop sys.boot_completed).Trim()
            if ($boot -eq "1") { break }
        } catch { }
        Start-Sleep -Seconds 2
        $elapsed += 2
    }

    $emu = Get-RunningEmulator
    if (-not $emu) {
        Write-Error "Emulator did not finish booting within $timeout seconds."
        exit 1
    }

    Write-Host "[OK] Emulator booted successfully: $emu" -ForegroundColor Green
    return $emu
}

# ── 2. Handle Actions ────────────────────────────────────────────────────────

switch ($Action) {

    "start-emulator" {
        Write-Host "=== Harmonia Mobile: Start Emulator ===" -ForegroundColor Cyan
        $emu = Ensure-EmulatorRunning -targetAvd $AvdName
        Write-Host "Ready on: $emu" -ForegroundColor Green
    }

    "status" {
        Write-Host "=== Harmonia Mobile: Environment Status ===" -ForegroundColor Cyan
        Write-Host "Repository : $repoRoot"
        Write-Host "Android SDK: $sdkDir"
        Write-Host "ADB Path   : $adb"

        $emu = Get-RunningEmulator
        if ($emu) {
            Write-Host "Emulator   : $emu (ONLINE)" -ForegroundColor Green
            $deviceModel = (& $adb shell getprop ro.product.model).Trim()
            $androidVer  = (& $adb shell getprop ro.build.version.release).Trim()
            $apiLevel    = (& $adb shell getprop ro.build.version.sdk).Trim()
            Write-Host "Device Info: $deviceModel (Android $androidVer, API $apiLevel)"

            $pkg = & $adb shell "pm list packages org.harmonia.player"
            if ($pkg -match "org.harmonia.player") {
                Write-Host "App Status : INSTALLED" -ForegroundColor Green
                $pidRaw = & $adb shell "pidof org.harmonia.player"
                $runningPid = if ($pidRaw) { ($pidRaw -join " ").Trim() } else { "" }
                if ($runningPid) {
                    Write-Host "Process    : RUNNING (PID: $runningPid)" -ForegroundColor Green
                } else {
                    Write-Host "Process    : STOPPED" -ForegroundColor Yellow
                }
            } else {
                Write-Host "App Status : NOT INSTALLED" -ForegroundColor Yellow
            }
        } else {
            Write-Host "Emulator   : OFFLINE (No running emulator detected)" -ForegroundColor Red
            Write-Host "Run '.\tools\mobile_dev.ps1 -Action start-emulator' to launch it."
        }
    }

    "relaunch" {
        Write-Host "=== Harmonia Mobile: Fast Relaunch ===" -ForegroundColor Cyan
        $emu = Ensure-EmulatorRunning -targetAvd $AvdName

        Write-Host "[*] Stopping existing app instance..." -ForegroundColor Yellow
        & $adb shell am force-stop org.harmonia.player

        Write-Host "[*] Launching org.harmonia.player/.presentation.MainActivity..." -ForegroundColor Green
        & $adb shell am start -n org.harmonia.player/.presentation.MainActivity | Out-Null
        Write-Host "[OK] Harmonia relaunched on $emu." -ForegroundColor Green
    }

    "test" {
        Write-Host "=== Harmonia Mobile: Full Test Suite ===" -ForegroundColor Cyan
        Push-Location $mobileDir
        try {
            Write-Host "[*] Executing JVM unit tests (testDebugUnitTest)..." -ForegroundColor Yellow
            & .\gradlew.bat :app:testDebugUnitTest --console=plain
            if ($LASTEXITCODE -ne 0) {
                Write-Error "Tests failed with exit code $LASTEXITCODE."
                exit $LASTEXITCODE
            }
            Write-Host "[OK] All unit tests passed!" -ForegroundColor Green
        } finally {
            Pop-Location
        }
    }

    "screenshot" {
        Write-Host "=== Harmonia Mobile: Capture Screenshot ===" -ForegroundColor Cyan
        $emu = Ensure-EmulatorRunning -targetAvd $AvdName

        $artifactDir = Join-Path $mobileDir "app\build\test-artifacts\screenshots"
        if (-not (Test-Path $artifactDir)) {
            New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
        }

        $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
        $targetFile = Join-Path $artifactDir "capture_$timestamp.png"

        & $adb shell screencap -p /data/local/tmp/harmonia_screencap.png
        & $adb pull /data/local/tmp/harmonia_screencap.png $targetFile | Out-Null
        & $adb shell rm -f /data/local/tmp/harmonia_screencap.png
        Write-Host "[OK] Screenshot captured:" -ForegroundColor Green
        Write-Host "     $targetFile" -ForegroundColor Cyan
    }

    "run" {
        Write-Host "=== Harmonia Mobile: Fast Iteration Workflow ===" -ForegroundColor Cyan
        $startTime = [System.Diagnostics.Stopwatch]::StartNew()

        # 1. Ensure emulator is active
        $emu = Ensure-EmulatorRunning -targetAvd $AvdName

        # 2. Optional clean uninstall
        if ($Clean) {
            Write-Host "[*] Clean install requested: Uninstalling org.harmonia.player..." -ForegroundColor Yellow
            & $adb uninstall org.harmonia.player | Out-Null
        }

        # 3. Incremental build and install
        Write-Host "[*] Building and installing debug APK (:app:installDebug)..." -ForegroundColor Yellow
        Push-Location $mobileDir
        try {
            & .\gradlew.bat :app:installDebug --console=plain
            if ($LASTEXITCODE -ne 0) {
                Write-Error "Build/Install failed with exit code $LASTEXITCODE. Inspect errors above."
                exit $LASTEXITCODE
            }
        } finally {
            Pop-Location
        }

        # 4. Launch app
        if (-not $NoLaunch) {
            Write-Host "[*] Launching Harmonia (MainActivity)..." -ForegroundColor Yellow
            & $adb shell am start -n org.harmonia.player/.presentation.MainActivity | Out-Null
        }

        $startTime.Stop()
        $elapsedSec = [math]::Round($startTime.Elapsed.TotalSeconds, 1)
        Write-Host "[OK] Harmonia Mobile updated & running on $emu ($elapsedSec s)" -ForegroundColor Green
    }
}
