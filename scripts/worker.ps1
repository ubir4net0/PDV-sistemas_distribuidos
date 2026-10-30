#Requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Verify', 'Run', 'Version')]
    [string]$Action = 'Verify',
    [string]$ProbePrefix = 'pdv.scaffold.probe'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$workerRoot = Join-Path $repoRoot 'backend/worker'
$image = (Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'infra/java-toolchain.json') | ConvertFrom-Json).image
$dockerArgs = @('run', '--rm', '--init',
    '--mount', "type=bind,source=$workerRoot,target=/workspace",
    '--mount', 'type=volume,source=pdv-worker-maven-cache,target=/root/.m2',
    '--workdir', '/workspace', '--env', 'TZ=UTC')
$previousEnv = @{}

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
    foreach ($key in $previousEnv.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousEnv[$key], 'Process')
    }
}
