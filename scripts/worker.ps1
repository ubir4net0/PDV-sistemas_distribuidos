#Requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Verify', 'Run', 'Version')]
    [string]$Action = 'Verify',
    [string]$ProbePrefix = 'pdv.scaffold.probe',
    [switch]$ExpireReservations,
    [switch]$SimulatePayments
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (($ExpireReservations -or $SimulatePayments) -and $Action -ne 'Run') { throw 'Modos de estoque/pagamento requerem -Action Run.' }
$repoRoot = Split-Path -Parent $PSScriptRoot
$workerRoot = Join-Path $repoRoot 'backend/worker'
$qualityRoot = Join-Path $repoRoot 'config'
$image = (Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'infra/java-toolchain.json') | ConvertFrom-Json).image
$dockerArgs = @('run', '--rm', '--init',
    '--mount', "type=bind,source=$workerRoot,target=/workspace",
    '--mount', "type=bind,source=$qualityRoot,target=/config,readonly",
    '--mount', 'type=volume,source=pdv-worker-maven-cache,target=/root/.m2',
    '--workdir', '/workspace', '--env', 'TZ=UTC')
$previousEnv = @{}
$verifyDbName = $null
$verifyDbCreated = $false

try {
    if ($Action -ne 'Version') {
        $composeArgs = @('compose', '--project-directory', $repoRoot, '-f', (Join-Path $repoRoot 'compose.yaml'))
        $brokerId = & docker @composeArgs ps -q rabbitmq
        if ($LASTEXITCODE -ne 0 -or -not $brokerId) { throw 'Inicie o RabbitMQ da PDV-004 primeiro.' }
        $health = & docker inspect --format '{{.State.Health.Status}}' $brokerId
        if ($LASTEXITCODE -ne 0 -or $health -ne 'healthy') { throw 'RabbitMQ local nao esta saudavel.' }
        $networks = (& docker inspect --format '{{json .NetworkSettings.Networks}}' $brokerId | ConvertFrom-Json).PSObject.Properties.Name
        if ($LASTEXITCODE -ne 0 -or @($networks).Count -ne 1) { throw 'Rede local RabbitMQ ambigua.' }
        $credentials = @(& docker @composeArgs exec -T rabbitmq printenv RABBITMQ_DEFAULT_USER RABBITMQ_DEFAULT_PASS RABBITMQ_DEFAULT_VHOST)
        if ($LASTEXITCODE -ne 0 -or $credentials.Count -ne 3) { throw 'Credenciais locais indisponiveis.' }
        $values = @{
            PDV_RABBITMQ_HOST = 'rabbitmq'; PDV_RABBITMQ_PORT = '5672'
            PDV_RABBITMQ_USER = $credentials[0]; PDV_RABBITMQ_PASSWORD = $credentials[1]
            PDV_RABBITMQ_VHOST = $credentials[2]; PDV_PROBE_PREFIX = $ProbePrefix
        }
        $expiryEnabled = $Action -eq 'Run' -and ($ExpireReservations -or $env:PDV_RESERVATION_EXPIRY_ENABLED -eq 'true')
        $values['PDV_RESERVATION_EXPIRY_ENABLED'] = if ($expiryEnabled) { 'true' } else { 'false' }
        $paymentEnabled = $Action -eq 'Run' -and ($SimulatePayments -or $env:PDV_SIMULATED_PAYMENTS_ENABLED -eq 'true')
        $values['PDV_SIMULATED_PAYMENTS_ENABLED'] = if ($paymentEnabled) { 'true' } else { 'false' }
        if ($Action -eq 'Verify' -or $expiryEnabled -or $paymentEnabled) {
            $postgresId = & docker @composeArgs ps -q postgres
            if ($LASTEXITCODE -ne 0 -or -not $postgresId) { throw 'Inicie PostgreSQL PDV primeiro.' }
            $postgresHealth = & docker inspect --format '{{.State.Health.Status}}' $postgresId
            if ($LASTEXITCODE -ne 0 -or $postgresHealth -ne 'healthy') { throw 'PostgreSQL PDV nao esta saudavel.' }
            $postgresCredentials = @(& docker @composeArgs exec -T postgres printenv POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD)
            if ($LASTEXITCODE -ne 0 -or $postgresCredentials.Count -ne 3) { throw 'Credenciais PostgreSQL indisponiveis.' }
            $database = $postgresCredentials[0]
            if ($Action -eq 'Verify') {
                $verifyDbName = 'pdv_verify_' + [Guid]::NewGuid().ToString('N')
                & docker @composeArgs exec -T postgres psql -X -U $postgresCredentials[1] -d $postgresCredentials[0] -v ON_ERROR_STOP=1 -c "CREATE DATABASE $verifyDbName"
                if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar banco proprio do worker.' }
                $verifyDbCreated = $true
                $database = $verifyDbName
                $apiMigrations = Join-Path $repoRoot 'backend/api/src/main/resources/db/migration'
                $dockerArgs += @('--mount', "type=bind,source=$apiMigrations,target=/api/src/main/resources/db/migration,readonly")
            }
            $values['PDV_DB_URL'] = 'jdbc:postgresql://postgres:5432/' + [Uri]::EscapeDataString($database)
            $values['PDV_DB_USER'] = $postgresCredentials[1]; $values['PDV_DB_PASSWORD'] = $postgresCredentials[2]
            $values['PDV_DB_SCHEMA'] = 'pdv'
        }
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
            if (-not (Test-Path -LiteralPath (Join-Path $workerRoot 'target/pdv-worker-0.0.1-SNAPSHOT.jar'))) {
                throw 'Execute worker.ps1 -Action Verify primeiro.'
            }
            $dockerArgs += @('--name', 'pdv-worker-local', '--label', 'dev.pdv.component=worker-scaffold',
                $image, 'java', '-jar', 'target/pdv-worker-0.0.1-SNAPSHOT.jar')
        }
    }
    & docker @dockerArgs
    if ($Action -eq 'Run' -and $LASTEXITCODE -in @(130, 143)) { return }
    if ($LASTEXITCODE -ne 0) { throw 'Execucao do worker/toolchain falhou.' }
}
finally {
    try {
        if ($verifyDbCreated) {
            if ($verifyDbName -notmatch '^pdv_verify_[a-f0-9]{32}$') { throw 'Limpeza de banco fora do escopo recusada.' }
            & docker @composeArgs exec -T postgres psql -X -U $postgresCredentials[1] -d $postgresCredentials[0] -v ON_ERROR_STOP=1 -c "DROP DATABASE $verifyDbName WITH (FORCE)"
            if ($LASTEXITCODE -ne 0) { throw 'Falha na limpeza do banco proprio do worker.' }
        }
    } finally {
        foreach ($key in $previousEnv.Keys) {
            [Environment]::SetEnvironmentVariable($key, $previousEnv[$key], 'Process')
        }
    }
}
