param([string]$MySqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe', [string]$AdminUser = 'root')
$ErrorActionPreference = 'Stop'
if (!(Test-Path $MySqlExe)) { throw 'MySQL client not found. Pass -MySqlExe with its full path.' }
$root = Split-Path $PSScriptRoot -Parent
$local = Join-Path $root '.local'
New-Item -ItemType Directory -Force $local | Out-Null
$secretPath = Join-Path $local 'db-password.dpapi'
if (Test-Path $secretPath) { throw 'Local database credentials already exist. Nothing changed. Reuse start.ps1, or back up credentials before re-provisioning.' }
$adminSecure = Read-Host "MySQL password for $AdminUser (not saved)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecure)
$previous = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes); $rng.Dispose()
    $password = [Convert]::ToBase64String($bytes)
    # CREATE USER fails safely if this username already exists; it never resets another account.
    $sql = "CREATE DATABASE IF NOT EXISTS shopsphere CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER 'shopsphere'@'localhost' IDENTIFIED BY '$password'; GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON shopsphere.* TO 'shopsphere'@'localhost';"
    $sql | & $MySqlExe --host=localhost --user=$AdminUser --batch
    if ($LASTEXITCODE -ne 0) { throw 'MySQL setup failed. No local credentials saved. Check client error above; an existing shopsphere account is never overwritten.' }
    ConvertTo-SecureString $password -AsPlainText -Force | ConvertFrom-SecureString | Set-Content $secretPath
    Write-Host 'Database and dedicated account created. Password saved with Windows user-bound DPAPI encryption in .local (ignored by Git).'
    Write-Host 'Next: powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1'
} finally {
    $env:MYSQL_PWD = $previous
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $password = $null; $sql = $null
}
