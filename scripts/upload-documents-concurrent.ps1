# 并发上传文件夹内文档到指定知识库（Windows PowerShell）
# 默认读取 .env / km-ai-service/.env 中的 MAX_CONCURRENT_PARSE_JOBS 作为上传并发数。
param(
    [Parameter(Mandatory = $true)]
    [string]$KbId,

    [Parameter(Mandatory = $true)]
    [string]$Dir,

    [string]$ApiBaseUrl,

    [string]$UserId,

    [string]$Tags,

    [int]$Concurrency = 0,

    [switch]$Recurse,

    [string[]]$Extensions = @("pdf", "docx", "pptx", "xlsx", "md", "txt", "jpg", "jpeg", "png")
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            continue
        }
        $parts = $trimmed.Split("=", 2)
        $key = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($key) {
            $values[$key] = $value
        }
    }
    return $values
}

function Get-EnvValue {
    param(
        [hashtable]$Values,
        [string]$Name,
        [string]$Default = ""
    )
    $processValue = [Environment]::GetEnvironmentVariable($Name)
    if ($processValue) {
        return $processValue
    }
    if ($Values.ContainsKey($Name) -and $Values[$Name]) {
        return $Values[$Name]
    }
    return $Default
}

function Resolve-BackendBaseUrl {
    param([hashtable]$Values)
    if ($ApiBaseUrl) {
        return $ApiBaseUrl.TrimEnd("/")
    }
    $statusUrl = Get-EnvValue -Values $Values -Name "KM_BACKEND_STATUS_URL"
    if ($statusUrl) {
        try {
            $uri = [Uri]$statusUrl
            return "$($uri.Scheme)://$($uri.Authority)"
        } catch {
            Write-Warning "KM_BACKEND_STATUS_URL 无法解析，回退到 http://localhost:8091"
        }
    }
    return "http://localhost:8091"
}

$envValues = Read-DotEnv (Join-Path $Root ".env")
$aiEnvValues = Read-DotEnv (Join-Path $Root "km-ai-service\.env")
foreach ($key in $aiEnvValues.Keys) {
    $envValues[$key] = $aiEnvValues[$key]
}

$resolvedApiBaseUrl = Resolve-BackendBaseUrl -Values $envValues
$resolvedUserId = if ($UserId) { $UserId } else { Get-EnvValue -Values $envValues -Name "KM_USER_ID" -Default "1" }

if ($Concurrency -le 0) {
    $configuredConcurrency = Get-EnvValue -Values $envValues -Name "MAX_CONCURRENT_PARSE_JOBS" -Default "1"
    if (-not [int]::TryParse($configuredConcurrency, [ref]$Concurrency) -or $Concurrency -le 0) {
        $Concurrency = 1
    }
}

$targetDir = Resolve-Path -LiteralPath $Dir
$extensionSet = @{}
foreach ($extension in $Extensions) {
    $extensionSet[$extension.TrimStart(".").ToLowerInvariant()] = $true
}

$childItemArgs = @{
    LiteralPath = $targetDir
    File = $true
}
if ($Recurse) {
    $childItemArgs["Recurse"] = $true
}

$files = @(Get-ChildItem @childItemArgs | Where-Object {
    $extensionSet.ContainsKey($_.Extension.TrimStart(".").ToLowerInvariant())
} | Sort-Object FullName)

if ($files.Count -eq 0) {
    Write-Host "No uploadable files found in $targetDir"
    exit 0
}

$endpoint = "$resolvedApiBaseUrl/api/knowledge-bases/$KbId/documents"
Write-Host "Backend: $resolvedApiBaseUrl"
Write-Host "Endpoint: $endpoint"
Write-Host "Directory: $targetDir"
Write-Host "Files: $($files.Count)"
Write-Host "Concurrency: $Concurrency"
Write-Host "UserId: $resolvedUserId"

$running = @()
$succeeded = 0
$failed = 0

function Receive-CompletedJobs {
    param([switch]$WaitAny)
    if ($WaitAny -and $running.Count -gt 0) {
        Wait-Job -Job $running -Any | Out-Null
    }

    $completed = @($running | Where-Object { $_.State -ne "Running" })
    foreach ($job in $completed) {
        $result = Receive-Job -Job $job
        Remove-Job -Job $job
        $script:running = @($script:running | Where-Object { $_.Id -ne $job.Id })

        foreach ($item in $result) {
            if ($item.Success) {
                $script:succeeded++
                Write-Host "[OK] $($item.File)"
            } else {
                $script:failed++
                Write-Host "[FAIL] $($item.File)"
                if ($item.Output) {
                    Write-Host $item.Output
                }
            }
        }
    }
}

foreach ($file in $files) {
    while ($running.Count -ge $Concurrency) {
        Receive-CompletedJobs -WaitAny
    }

    $job = Start-Job -ArgumentList $endpoint, $file.FullName, $resolvedUserId, $Tags -ScriptBlock {
        param($Endpoint, $FilePath, $HeaderUserId, $TagsValue)

        $arguments = @(
            "-sS",
            "-f",
            "-X", "POST",
            "-H", "userid: $HeaderUserId",
            "-F", "file=@$FilePath"
        )
        if ($TagsValue) {
            $arguments += @("-F", "tags=$TagsValue")
        }
        $arguments += $Endpoint

        $output = & curl.exe @arguments 2>&1
        [pscustomobject]@{
            File = $FilePath
            Success = ($LASTEXITCODE -eq 0)
            Output = ($output -join "`n")
        }
    }
    $running += $job
}

while ($running.Count -gt 0) {
    Receive-CompletedJobs -WaitAny
}

Write-Host "=========================================="
Write-Host "Uploaded: $succeeded succeeded, $failed failed"
Write-Host "=========================================="

if ($failed -gt 0) {
    exit 1
}
