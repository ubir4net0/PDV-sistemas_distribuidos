#Requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Verify', 'Run', 'Version')]
    [string]$Action = 'Verify'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$apiRoot = Join-Path $repoRoot 'backend/api'
$qualityRoot = Join-Path $repoRoot 'config'
$toolchain = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'infra/java-toolchain.json') | ConvertFrom-Json
$image = $toolchain.image
$dockerArgs = @('run', '--rm', '--init',
    '--mount', "type=bind,source=$apiRoot,target=/workspace",
    '--mount', "type=bind,source=$qualityRoot,target=/config,readonly",
    '--mount', 'type=volume,source=pdv-api-maven-cache,target=/root/.m2',
    '--workdir', '/workspace', '--env', 'TZ=UTC')

switch ($Action) {
    'Version' { $dockerArgs += @($image, 'sh', '-ec', 'java -version; javac -version; sh ./mvnw --version') }
    'Verify' { $dockerArgs += @($image, 'sh', './mvnw', '-B', '-ntp', 'clean', 'verify') }
    'Run' {
        if (-not (Test-Path -LiteralPath (Join-Path $apiRoot 'target/pdv-api-0.0.1-SNAPSHOT.jar'))) {
            throw 'JAR ausente. Execute scripts/api.ps1 -Action Verify primeiro.'
        }
        $dockerArgs += @('--name', 'pdv-api-local', '--label', 'dev.pdv.component=api-scaffold',
            '--publish', '127.0.0.1:8081:8081', '--env', 'SERVER_ADDRESS=0.0.0.0',
            $image, 'java', '-jar', 'target/pdv-api-0.0.1-SNAPSHOT.jar')
    }
}
& docker @dockerArgs
if ($Action -eq 'Run' -and $LASTEXITCODE -in @(130, 143)) { return }
if ($LASTEXITCODE -ne 0) { throw 'Execucao da API/toolchain falhou.' }
