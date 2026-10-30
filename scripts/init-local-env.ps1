#Requires -Version 5.1
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repoRoot '.env'

if (Test-Path -LiteralPath $envPath) {
    Write-Host '.env existente preservado; nenhuma credencial foi alterada.'
    return
}

function New-LocalPassword {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) }
    finally { $generator.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

$template = [IO.File]::ReadAllText((Join-Path $repoRoot '.env.example'))
$content = $template.Replace('PDV_POSTGRES_PASSWORD=', ('PDV_POSTGRES_PASSWORD=' + (New-LocalPassword)))
$content = $content.Replace('PDV_RABBITMQ_PASSWORD=', ('PDV_RABBITMQ_PASSWORD=' + (New-LocalPassword)))
# CreateNew prevents overwriting a file created concurrently.
$stream = [IO.File]::Open($envPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $bytes = (New-Object Text.UTF8Encoding $false).GetBytes($content)
    $stream.Write($bytes, 0, $bytes.Length)
}
finally { $stream.Dispose() }

Write-Host '.env local criado com senhas aleatorias; valores nao exibidos. Arquivo ignorado pelo Git.'
