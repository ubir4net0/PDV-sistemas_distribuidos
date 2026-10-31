#Requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Verify', 'Run', 'Version', 'Migrate', 'Validate', 'Info')]
    [string]$Action = 'Verify',
    [switch]$Development
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Development -and $Action -ne 'Run') { throw '-Development requer -Action Run.' }
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

$previousEnv = @{}
$verifyDb = $null
$verifyDbCreated = $false
try {
    if ($Action -ne 'Version') {
        $composeArgs = @('compose', '--project-directory', $repoRoot, '-f', (Join-Path $repoRoot 'compose.yaml'))
        $postgresId = & docker @composeArgs ps -q postgres
        if ($LASTEXITCODE -ne 0 -or -not $postgresId) { throw 'Inicie PostgreSQL PDV primeiro.' }
        $health = & docker inspect --format '{{.State.Health.Status}}' $postgresId
        if ($LASTEXITCODE -ne 0 -or $health -ne 'healthy') { throw 'PostgreSQL PDV nao esta saudavel.' }
        $networks = (& docker inspect --format '{{json .NetworkSettings.Networks}}' $postgresId | ConvertFrom-Json).PSObject.Properties.Name
        if ($LASTEXITCODE -ne 0 -or @($networks).Count -ne 1) { throw 'Rede PostgreSQL ambigua.' }
        $credentials = @(& docker @composeArgs exec -T postgres printenv POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD)
        if ($LASTEXITCODE -ne 0 -or $credentials.Count -ne 3) { throw 'Credenciais locais indisponiveis.' }
        $database = $credentials[0]
        if ($Action -eq 'Verify') {
            $verifyDb = 'pdv_verify_' + [Guid]::NewGuid().ToString('N')
            & docker @composeArgs exec -T postgres psql -X -U $credentials[1] -d $credentials[0] -v ON_ERROR_STOP=1 -c "CREATE DATABASE $verifyDb"
            if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel criar banco de teste proprio.' }
            $verifyDbCreated = $true
            $database = $verifyDb
        }
        $values = @{
            PDV_DB_URL = 'jdbc:postgresql://postgres:5432/' + [Uri]::EscapeDataString($database)
            PDV_DB_USER = $credentials[1]; PDV_DB_PASSWORD = $credentials[2]
        }
        if ($Development) { $values['SPRING_PROFILES_ACTIVE'] = 'development' }
        foreach ($key in $values.Keys) {
            $previousEnv[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
            $dockerArgs += @('--env', $key)
        }
        $dockerArgs += @('--network', $networks)
    }
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
        default { $dockerArgs += @($image, 'sh', './mvnw', '-B', '-ntp', ('flyway:' + $Action.ToLowerInvariant())) }
    }
    & docker @dockerArgs
    if ($Action -eq 'Run' -and $LASTEXITCODE -in @(130, 143)) { return }
    if ($LASTEXITCODE -ne 0) { throw 'Execucao da API/toolchain falhou.' }
}
finally {
    try {
        if ($verifyDbCreated) {
            if ($verifyDb -notmatch '^pdv_verify_[a-f0-9]{32}$') { throw 'Nome de banco de teste inesperado; limpeza recusada.' }
            & docker @composeArgs exec -T postgres psql -X -U $credentials[1] -d $credentials[0] -v ON_ERROR_STOP=1 -c "DROP DATABASE $verifyDb WITH (FORCE)"
            if ($LASTEXITCODE -ne 0) { throw "Limpeza pendente somente do banco de teste $verifyDb." }
        }
    }
    finally {
        foreach ($key in $previousEnv.Keys) {
            [Environment]::SetEnvironmentVariable($key, $previousEnv[$key], 'Process')
        }
    }
}
