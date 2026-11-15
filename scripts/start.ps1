param([switch]$Demo)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
New-Item -ItemType Directory -Force '.local' | Out-Null
function Read-PrivateFile([string]$Path) {
    $secure = Get-Content $Path | ConvertTo-SecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$oldDbPassword = $env:DB_PASSWORD
$oldDbUser = $env:DB_USERNAME
$oldAdminPassword = $env:ADMIN_PASSWORD
$oldAdminEmail = $env:ADMIN_EMAIL
try {
    if (!$Demo -and !$env:DB_PASSWORD) {
        if (!(Test-Path '.local/db-password.dpapi')) { throw 'Run scripts/setup-mysql.ps1 first, or set DB_URL, DB_USERNAME and DB_PASSWORD. For isolated local testing use -Demo.' }
        $env:DB_PASSWORD = Read-PrivateFile '.local/db-password.dpapi'
        if (!$env:DB_USERNAME) { $env:DB_USERNAME = 'shopsphere' }
    }
    $adminFile = if ($Demo) { '.local/demo-admin-password.dpapi' } else { '.local/admin-password.dpapi' }
    if (!$env:ADMIN_EMAIL) { $env:ADMIN_EMAIL = 'admin@shopsphere.local' }
    if (!$env:ADMIN_PASSWORD) {
        if (!(Test-Path $adminFile)) {
            Write-Host "Choose initial administrator password for $env:ADMIN_EMAIL (12+ characters). Saved encrypted for this Windows account."
            $secure = Read-Host 'New admin password' -AsSecureString
            $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
            try { $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr); if ($plain.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($plain) -gt 72) { throw 'Use 12 to 72 UTF-8 bytes.' } }
            finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr); $plain = $null }
            $secure | ConvertFrom-SecureString | Set-Content $adminFile
        }
        $env:ADMIN_PASSWORD = Read-PrivateFile $adminFile
    }
    $ErrorActionPreference = 'Continue'
    & '.\mvnw.cmd' -q package -DskipTests 2>&1 | Tee-Object -FilePath '.local/build.log'
    if ($LASTEXITCODE -ne 0) { throw 'Build failed. See .local/build.log for the exact Maven error.' }
    Write-Host 'ShopSphere: http://localhost:8080 (Ctrl+C stops it). Admin passwords only seed new accounts; they never reset existing passwords.'
    if ($Demo) { & java -jar target/shopsphere-1.0.0.jar --spring.profiles.active=demo 2>&1 | Tee-Object -FilePath '.local/application.log' }
    else { & java -jar target/shopsphere-1.0.0.jar 2>&1 | Tee-Object -FilePath '.local/application.log' }
    if ($LASTEXITCODE -ne 0) { throw 'Application exited with an error. See .local/application.log (search for Caused by). No database reset has been performed.' }
} finally {
    $env:DB_PASSWORD = $oldDbPassword; $env:DB_USERNAME = $oldDbUser
    $env:ADMIN_PASSWORD = $oldAdminPassword; $env:ADMIN_EMAIL = $oldAdminEmail
}
