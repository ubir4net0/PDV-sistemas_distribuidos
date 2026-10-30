#Requires -Version 5.1
[CmdletBinding()]
param(
    # Restarts only the two services selected by this Compose project.
    [switch]$Restart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repoRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Execute scripts/init-local-env.ps1 antes da verificacao.'
}
$composeArgs = @('compose', '--project-directory', $repoRoot, '--env-file', $envPath, '-f', (Join-Path $repoRoot 'compose.yaml'))

function Invoke-LocalCompose {
    param([string[]]$DockerArgs)
    $result = & docker @composeArgs @DockerArgs
    if ($LASTEXITCODE -ne 0) { throw 'Comando Docker Compose falhou; consulte o estado dos servicos locais.' }
    return $result
}

function Invoke-LocalSql {
    param([string]$Sql)
    # Credentials stay in the container environment; SQL travels over stdin.
    $result = $Sql | & docker @composeArgs exec -T postgres sh -ec 'export PGPASSWORD="$POSTGRES_PASSWORD"; exec psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -v ON_ERROR_STOP=1 -Atq'
    if ($LASTEXITCODE -ne 0) { throw 'Consulta autenticada ao PostgreSQL falhou.' }
    return $result
}

function Assert-Healthy {
    foreach ($service in @('postgres', 'rabbitmq')) {
        $containerId = Invoke-LocalCompose -DockerArgs @('ps', '-q', $service)
        if (-not $containerId) { throw "Servico $service nao iniciado." }
        $health = & docker inspect --format '{{.State.Health.Status}}' $containerId
        if ($LASTEXITCODE -ne 0 -or $health -ne 'healthy') { throw "Servico $service sem health saudavel." }
    }
}

$nonce = [Guid]::NewGuid().ToString('N')
$table = "pdv_infra_smoke_$nonce"
$queue = "pdv.infra-smoke.$nonce"
$tableCreated = $false
$queueCreated = $false
$queueUri = $null
$headers = $null
$verificationSucceeded = $false

try {
    $null = Invoke-LocalCompose -DockerArgs @('config', '--quiet')
    Assert-Healthy
    $timezone = Invoke-LocalSql -Sql 'SHOW timezone;'
    if ($timezone -ne 'UTC') { throw 'PostgreSQL precisa usar UTC.' }
    $null = Invoke-LocalSql -Sql "BEGIN; CREATE TABLE public.$table (value text NOT NULL); INSERT INTO public.$table VALUES ('$nonce'); COMMIT;"
    $tableCreated = $true

    # Capture only the required credentials; never print or pass them on the host command line.
    $credentials = @(Invoke-LocalCompose -DockerArgs @('exec', '-T', 'rabbitmq', 'printenv', 'RABBITMQ_DEFAULT_USER', 'RABBITMQ_DEFAULT_PASS'))
    if ($credentials.Count -ne 2) { throw 'Credenciais locais RabbitMQ indisponiveis.' }
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($credentials[0] + ':' + $credentials[1])))
    $headers = @{ Authorization = "Basic $encoded" }
    $vhost = Invoke-LocalCompose -DockerArgs @('exec', '-T', 'rabbitmq', 'printenv', 'RABBITMQ_DEFAULT_VHOST')
    $binding = Invoke-LocalCompose -DockerArgs @('port', 'rabbitmq', '15672')
    if ($binding -notmatch '^127\.0\.0\.1:\d+$') { throw 'Management precisa estar limitado a 127.0.0.1.' }
    $api = "http://$binding/api"
    $escapedVhost = [Uri]::EscapeDataString($vhost)
    $queueUri = "$api/queues/$escapedVhost/$queue"
    $queueBody = @{ durable = $true; auto_delete = $false; arguments = @{} } | ConvertTo-Json -Compress
    $null = Invoke-RestMethod -Method Put -Uri $queueUri -Headers $headers -ContentType 'application/json' -Body $queueBody -TimeoutSec 15
    $queueCreated = $true

    $message = @{ properties = @{ delivery_mode = 2 }; routing_key = $queue; payload = $nonce; payload_encoding = 'string' } | ConvertTo-Json -Depth 4 -Compress
    $published = Invoke-RestMethod -Method Post -Uri "$api/exchanges/$escapedVhost/amq.default/publish" -Headers $headers -ContentType 'application/json' -Body $message -TimeoutSec 15
    if (-not $published.routed) { throw 'Mensagem de teste nao foi roteada.' }

    if ($Restart) {
        $null = Invoke-LocalCompose -DockerArgs @('restart', 'postgres', 'rabbitmq')
        $null = Invoke-LocalCompose -DockerArgs @('up', '-d', '--wait', '--wait-timeout', '180', 'postgres', 'rabbitmq')
        Assert-Healthy
    }

    $saved = Invoke-LocalSql -Sql "SELECT value FROM public.$table;"
    if ($saved -ne $nonce) { throw 'Marcador PostgreSQL nao foi preservado.' }
    $getBody = @{ count = 1; ackmode = 'ack_requeue_false'; encoding = 'auto'; truncate = 1024 } | ConvertTo-Json -Compress
    $received = @(Invoke-RestMethod -Method Post -Uri "$queueUri/get" -Headers $headers -ContentType 'application/json' -Body $getBody -TimeoutSec 15)
    if ($received.Count -ne 1 -or $received[0].payload -ne $nonce) { throw 'Mensagem RabbitMQ nao foi preservada/consumida.' }
    Write-Host 'PASS: PostgreSQL autenticado/UTC e RabbitMQ autenticado com publish/consume real via HTTP management.'
    if ($Restart) { Write-Host 'PASS: dados PostgreSQL e mensagem persistente RabbitMQ preservados apos reinicio dos dois servicos.' }
    $verificationSucceeded = $true
}
finally {
    # Names are unique to this invocation; never purge shared queues or reset databases.
    $cleanupFailed = $false
    if ($tableCreated) {
        try { $null = Invoke-LocalSql -Sql "DROP TABLE public.$table;" }
        catch { $cleanupFailed = $true; Write-Warning "Limpeza pendente da tabela de teste public.$table" }
    }
    if ($queueCreated) {
        try { $null = Invoke-RestMethod -Method Delete -Uri $queueUri -Headers $headers -TimeoutSec 15 }
        catch { $cleanupFailed = $true; Write-Warning "Limpeza pendente da fila de teste $queue" }
    }
    if ($cleanupFailed -and $verificationSucceeded) { throw 'Verificacao funcional passou, mas a limpeza dos objetos de teste precisa de atencao.' }
}
