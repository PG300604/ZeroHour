<#
.SYNOPSIS
    Setup, backup, and restore environment configuration and credentials for ZeroHour.
.DESCRIPTION
    This script manages sensitive configuration files and environment variables safely
    outside the Git repository, allowing rapid backup and restore when cloning or resetting.
.PARAMETER Action
    Specify 'Backup' to copy files to your local user directory, or 'Restore' to restore them.
#>
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("Backup", "Restore")]
    [string]$Action
)

$BackupDir = Join-Path $env:USERPROFILE ".zerohour_backup"
$SecretsFile = Join-Path $BackupDir "secrets.json"

if ($Action -eq "Backup") {
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    }
    
    Write-Host "💾 Starting backup to $BackupDir..." -ForegroundColor Cyan

    # Backup files
    if (Test-Path "firebase-b64.txt") {
        Copy-Item "firebase-b64.txt" -Destination $BackupDir -Force
        Write-Host "✅ Backed up firebase-b64.txt" -ForegroundColor Green
    }
    if (Test-Path "backend/src/main/resources/firebase-service-account.json") {
        Copy-Item "backend/src/main/resources/firebase-service-account.json" -Destination $BackupDir -Force
        Write-Host "✅ Backed up firebase-service-account.json" -ForegroundColor Green
    }

    # Backup Environment Variables
    $EnvVars = @{}
    $VarsToBackup = @("GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "GEMINI_API_KEY", "FIREBASE_PROJECT_ID", "FIREBASE_SERVICE_ACCOUNT_BASE64", "FRONTEND_URL", "AES_SECRET_KEY")
    
    foreach ($var in $VarsToBackup) {
        $val = [System.Environment]::GetEnvironmentVariable($var, "Process")
        if (-not $val) {
            $val = [System.Environment]::GetEnvironmentVariable($var, "User")
        }
        if (-not $val) {
            $val = [System.Environment]::GetEnvironmentVariable($var, "Machine")
        }
        if ($val) {
            $EnvVars[$var] = $val
            Write-Host "✅ Captured environment variable: $var" -ForegroundColor Green
        } else {
            Write-Host "⚠️ Variable $var not currently set in system. Skipping." -ForegroundColor Yellow
        }
    }

    $EnvVars | ConvertTo-Json | Out-File -FilePath $SecretsFile -Encoding utf8
    Write-Host "🎉 Backup completed successfully!" -ForegroundColor Green

} elseif ($Action -eq "Restore") {
    if (-not (Test-Path $BackupDir)) {
        Write-Error "❌ Backup folder not found at $BackupDir. Run with -Action Backup first on your original setup."
        return
    }

    Write-Host "🔄 Restoring configuration from $BackupDir..." -ForegroundColor Cyan

    # Restore files
    if (Test-Path (Join-Path $BackupDir "firebase-b64.txt")) {
        Copy-Item (Join-Path $BackupDir "firebase-b64.txt") -Destination "." -Force
        Write-Host "✅ Restored firebase-b64.txt" -ForegroundColor Green
    }
    
    $DestJson = "backend/src/main/resources/firebase-service-account.json"
    if (Test-Path (Join-Path $BackupDir "firebase-service-account.json")) {
        $DestDir = Split-Path $DestJson -Parent
        if (-not (Test-Path $DestDir)) {
            New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
        }
        Copy-Item (Join-Path $BackupDir "firebase-service-account.json") -Destination $DestJson -Force
        Write-Host "✅ Restored firebase-service-account.json" -ForegroundColor Green
    }

    # Restore Environment Variables
    if (Test-Path $SecretsFile) {
        $EnvVars = Get-Content $SecretsFile | ConvertFrom-Json
        foreach ($prop in $EnvVars.psobject.Properties) {
            $name = $prop.Name
            $value = $prop.Value
            [System.Environment]::SetEnvironmentVariable($name, $value, "User")
            Write-Host "✅ Registered persistent user environment variable: $name" -ForegroundColor Green
        }
        Write-Host "💡 Please restart your terminal/IDE/editor to apply the new environment variables." -ForegroundColor Yellow
    } else {
        Write-Host "⚠️ secrets.json not found in backup directory. No environment variables were restored." -ForegroundColor Yellow
    }
    
    Write-Host "🎉 Restore completed successfully!" -ForegroundColor Green
}
