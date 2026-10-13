#Requires -Version 5.1
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$PlanOnly,
    [switch]$AllowExternalUpload,
    [ValidateRange(1, 86400)][int]$GradleTimeoutSeconds = 3600
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$branch = & git -C $projectRoot branch --show-current
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branch)) {
    throw "Sonar analysis requires a named Git branch."
}
$gradleArguments = @("--no-daemon", "--console=plain", "assembleDebug", "sonar")
$analysisMode = "main branch"
$blocker = $null
if ($branch -eq "main") {
    $gradleArguments += "-Dsonar.branch.name=main"
} else {
    $analysisMode = "pull request"
    if (-not (Get-Command gh -CommandType Application -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI (gh) is required to find the pull request for Sonar analysis."
    }
    $prJson = & gh pr list --repo Insaner1980/Numeris-Android --head $branch --base main `
        --state open --json number,headRefName,baseRefName,headRepository
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub pull request lookup failed. Check gh auth status."
    }
    $pullRequests = @($prJson | ConvertFrom-Json | Where-Object {
        $_.headRefName -eq $branch -and $_.baseRefName -eq "main" -and
        $_.headRepository.nameWithOwner -eq "Insaner1980/Numeris-Android"
    })
    if ($pullRequests.Count -eq 0) {
        $blocker = "Nykyinen SonarCloud-tilaus tarvitsee haaralle '$branch' avoimen PR:n main-haaraan. Luo PR itse GitHubissa ja aja sonar uudelleen. Buildia tai analyysia ei käynnistetty."
    } elseif ($pullRequests.Count -gt 1) {
        throw "Multiple open pull requests matched branch '$branch'; Sonar analysis cannot choose one."
    } else {
        $pullRequest = $pullRequests[0]
        $analysisMode = "pull request #$($pullRequest.number) -> main"
        $gradleArguments += @(
            "-Dsonar.pullrequest.key=$($pullRequest.number)",
            "-Dsonar.pullrequest.branch=$branch",
            "-Dsonar.pullrequest.base=main"
        )
    }
}
if ($PlanOnly) {
    Write-Output @(
        "sonar",
        "  - root: $projectRoot",
        "  - project: Insaner1980_Numeris-Android (organization: insaner1980)",
        "  - server: https://sonarcloud.io",
        "  - branch: $branch",
        "  - mode: $analysisMode",
        $(if ($blocker) { "  - BLOCKED: $blocker" } else { "  - Gradle: $($gradleArguments -join ' ')" }),
        "  - uploads source and analysis metadata; waits for the quality gate",
        "  - report: reports/sonar.txt",
        "  - actual execution requires -AllowExternalUpload and SONAR_TOKEN"
    )
    exit $(if ($blocker) { 2 } else { 0 })
}
if ($blocker) {
    Write-Error $blocker -ErrorAction Continue
    exit 2
}
if (-not $AllowExternalUpload) {
    throw "External upload requires -AllowExternalUpload. Use sonar -PlanOnly to review the scope."
}
if ([string]::IsNullOrWhiteSpace($env:SONAR_TOKEN)) {
    throw "SONAR_TOKEN is unavailable. Run sonar through the configured PowerShell profile."
}
$reportDirectory = Join-Path $projectRoot "reports"
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
$reportPath = Join-Path $reportDirectory "sonar.txt"
Set-Content -LiteralPath $reportPath -Encoding utf8 -Value @(
    "sonar", "Root: $projectRoot", "Branch: $branch", "Mode: $analysisMode", "Started: $(Get-Date -Format o)"
)
try {
    Import-Module "C:\Dev\Android-check\tools\CheckRuntime.psm1" -Force -ErrorAction Stop
    $result = Invoke-ManagedProcess -Executable (Join-Path $projectRoot "gradlew.bat") `
        -Arguments $gradleArguments -WorkingDirectory $projectRoot -TimeoutSeconds $GradleTimeoutSeconds
    foreach ($stream in @($result.StandardOutput, $result.StandardError)) {
        if (-not [string]::IsNullOrWhiteSpace($stream)) {
            $safeOutput = $stream.Replace($env:SONAR_TOKEN, "[REDACTED_SECRET]")
            Add-Content -LiteralPath $reportPath -Encoding utf8 -Value $safeOutput
            Write-Output $safeOutput
        }
    }
    if ($result.TimedOut) {
        throw "Sonar analysis timed out after $GradleTimeoutSeconds seconds."
    }
    if ($result.ExitCode -ne 0) {
        throw "Sonar analysis or quality gate failed (exit $($result.ExitCode))."
    }
    Write-Output "sonar: analysis and quality gate passed -> $reportPath"
    exit 0
} catch {
    $message = $_.Exception.Message.Replace($env:SONAR_TOKEN, "[REDACTED_SECRET]")
    Add-Content -LiteralPath $reportPath -Encoding utf8 -Value "ERROR: $message"
    Write-Error $message -ErrorAction Continue
    exit 2
}
