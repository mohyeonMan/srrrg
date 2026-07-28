param(
    [string]$BaseUrl = 'https://jhhomehub.gonetis.com/srrrg-dev',
    [ValidateRange(1, 100000)]
    [int]$RedirectRequests = 20
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$startedAt = Get-Date
$runName = '{0}-smoke-redirect-{1}' -f $startedAt.ToString('HHmmss'), $RedirectRequests
$resultDirectory = Join-Path $repoRoot ('docs\performance\results\{0}\{1}' -f $startedAt.ToString('yyyy-MM-dd'), $runName)
$logPath = Join-Path $resultDirectory 'k6-output.log'
$metadataPath = Join-Path $resultDirectory 'metadata.json'

New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

$k6Command = Get-Command k6 -ErrorAction SilentlyContinue
$k6Executable = if ($k6Command) {
    $k6Command.Source
} elseif (Test-Path -LiteralPath 'C:\Program Files\k6\k6.exe') {
    'C:\Program Files\k6\k6.exe'
} else {
    throw 'k6 executable was not found. Install k6 and open a new terminal.'
}

$commitSha = (& git -C $repoRoot rev-parse HEAD).Trim()
$workingTreeDirty = [bool](& git -C $repoRoot status --porcelain)

Push-Location $repoRoot
try {
    & $k6Executable run `
        -e "BASE_URL=$BaseUrl" `
        -e "SMOKE_REDIRECT_REQUESTS=$RedirectRequests" `
        .\scripts\performance\smoke.js 2>&1 |
        Tee-Object -FilePath $logPath
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

$metadata = [ordered]@{
    scenario = 'smoke'
    startedAt = $startedAt.ToString('o')
    finishedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    redirectRequests = $RedirectRequests
    commitSha = $commitSha
    workingTreeDirty = $workingTreeDirty
    k6Executable = $k6Executable
    exitCode = $exitCode
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding UTF8

Write-Host "Result directory: $resultDirectory"
exit $exitCode
