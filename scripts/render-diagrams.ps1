param([switch]$Preview, [string[]]$Diagram)
$ErrorActionPreference = 'Stop'
$pdvRepo = Split-Path -Parent $PSScriptRoot
$pdvDiagrams = Join-Path $pdvRepo 'docs/diagrams'
$pdvManifest = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $pdvDiagrams 'manifest.json') | ConvertFrom-Json
$pdvConfig = Join-Path $pdvDiagrams 'mermaid-config.json'
$pdvPreviousSkip = $env:PUPPETEER_SKIP_DOWNLOAD
$pdvPreviousBrowser = $env:PUPPETEER_EXECUTABLE_PATH
try {
    $env:PUPPETEER_SKIP_DOWNLOAD = 'true'
    if (-not $env:PUPPETEER_EXECUTABLE_PATH) {
        $pdvX86 = [Environment]::GetFolderPath('ProgramFilesX86')
        $pdvBrowserCandidates = @(
            (Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe'),
            (Join-Path $pdvX86 'Microsoft/Edge/Application/msedge.exe'),
            (Join-Path $env:ProgramFiles 'Microsoft/Edge/Application/msedge.exe')
        )
        $env:PUPPETEER_EXECUTABLE_PATH = $pdvBrowserCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
    if (-not $env:PUPPETEER_EXECUTABLE_PATH -or -not (Test-Path -LiteralPath $env:PUPPETEER_EXECUTABLE_PATH)) {
        throw 'Defina PUPPETEER_EXECUTABLE_PATH para um Chrome/Chromium instalado.'
    }
    $pdvSvgDir = Join-Path $pdvDiagrams 'svg'
    New-Item -ItemType Directory -Path $pdvSvgDir -Force | Out-Null
    if ($Preview) {
        $pdvGitDir = git -C $pdvRepo rev-parse --absolute-git-dir
        if ($LASTEXITCODE -ne 0) { throw 'Diretorio Git nao encontrado para as previas.' }
        $pdvPreviewDir = Join-Path $pdvGitDir 'diagrams-preview'
        New-Item -ItemType Directory -Path $pdvPreviewDir -Force | Out-Null
    }
    $pdvSelected = @($pdvManifest.diagrams | Where-Object { -not $Diagram -or $_.id -in $Diagram })
    if ($Diagram -and $pdvSelected.Count -ne @($Diagram | Select-Object -Unique).Count) {
        throw 'ID de diagrama nao encontrado no manifesto.'
    }
    foreach ($pdvDiagram in $pdvSelected) {
        $pdvInput = Join-Path $pdvDiagrams $pdvDiagram.mermaid
        $pdvOutput = Join-Path $pdvDiagrams $pdvDiagram.svg
        npm exec --yes --package=@mermaid-js/mermaid-cli@12.0.0 -- mmdc -i $pdvInput -o $pdvOutput -c $pdvConfig -b white
        if ($LASTEXITCODE -ne 0) { throw "Falha de renderizacao: $($pdvDiagram.id)" }
        if ($Preview) {
            $pdvPng = Join-Path $pdvPreviewDir ($pdvDiagram.id + '.png')
            npm exec --yes --package=@mermaid-js/mermaid-cli@12.0.0 -- mmdc -i $pdvInput -o $pdvPng -c $pdvConfig -b white --size 2200
            if ($LASTEXITCODE -ne 0) { throw "Falha de previa: $($pdvDiagram.id)" }
        }
        Write-Output ("Renderizado: " + $pdvDiagram.id)
    }
} finally {
    $env:PUPPETEER_SKIP_DOWNLOAD = $pdvPreviousSkip
    $env:PUPPETEER_EXECUTABLE_PATH = $pdvPreviousBrowser
}
