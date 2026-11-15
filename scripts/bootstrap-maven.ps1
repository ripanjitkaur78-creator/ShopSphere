$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$tools = Join-Path $root '.tools'
New-Item -ItemType Directory -Force $tools | Out-Null
$url = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'
$zip = Join-Path $tools 'maven.zip'
Invoke-WebRequest $url -OutFile $zip -UseBasicParsing
$expected = (Invoke-WebRequest ($url + '.sha512') -UseBasicParsing).Content.Trim().Split(' ')[0]
$actual = (Get-FileHash $zip -Algorithm SHA512).Hash
if ($actual -ine $expected) { throw 'Maven download checksum mismatch' }
Expand-Archive $zip -DestinationPath $tools -Force
Write-Host 'Maven installed in project .tools directory.'
