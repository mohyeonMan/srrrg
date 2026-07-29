param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9-]+$')]
    [string]$Scenario
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$scriptPath = Join-Path $PSScriptRoot "$Scenario.js"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "Performance script was not found: $scriptPath"
}

$startedAt = Get-Date
$runName = '{0}-{1}' -f $startedAt.ToString('HHmmss'), $Scenario
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
    # Windows PowerShell 5는 k6의 정상 stderr 로그도 NativeCommandError로 변환하므로 중단하지 않음.
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    $logWriter = New-Object System.IO.StreamWriter($logPath, $false, $utf8WithoutBom)
    try {
        # Tee-Object는 Windows PowerShell 5에서 UTF-16으로 저장하므로 직접 UTF-8 로그를 작성함.
        & $k6Executable run --quiet $scriptPath 2>&1 | ForEach-Object {
            $line = $_.ToString()
            Write-Host $line
            $logWriter.WriteLine($line)
        }
        $exitCode = $LASTEXITCODE
    }
    finally {
        $logWriter.Dispose()
        $ErrorActionPreference = $previousErrorActionPreference
    }
}
finally {
    $ErrorActionPreference = $previousErrorActionPreference
    Pop-Location
}

$metadata = [ordered]@{
    scenario = $Scenario
    startedAt = $startedAt.ToString('o')
    finishedAt = (Get-Date).ToString('o')
    script = $scriptPath
    commitSha = $commitSha
    workingTreeDirty = $workingTreeDirty
    k6Executable = $k6Executable
    infrastructure = [ordered]@{
        commitSha = $env:PERF_INFRA_COMMIT_SHA
        replicaCount = $env:PERF_REPLICA_COUNT
        application = [ordered]@{
            cpuRequest = $env:PERF_APP_CPU_REQUEST
            cpuLimit = $env:PERF_APP_CPU_LIMIT
            memoryRequest = $env:PERF_APP_MEMORY_REQUEST
            memoryLimit = $env:PERF_APP_MEMORY_LIMIT
        }
        postgres = [ordered]@{
            cpuRequest = $env:PERF_POSTGRES_CPU_REQUEST
            cpuLimit = $env:PERF_POSTGRES_CPU_LIMIT
            memoryRequest = $env:PERF_POSTGRES_MEMORY_REQUEST
            memoryLimit = $env:PERF_POSTGRES_MEMORY_LIMIT
        }
        hikariMaximumPoolSize = $env:PERF_HIKARI_MAX_POOL_SIZE
    }
    exitCode = $exitCode
    analysisStatus = 'pending'
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding UTF8

Write-Host "Result directory: $resultDirectory"
exit $exitCode
