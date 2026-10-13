#Requires -Version 5.1
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$PlanOnly,
    [switch]$AllowExternalUpload,
    [ValidateRange(1, 86400)][int]$GradleTimeoutSeconds = 3600
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$gradleArguments = @(
    "--no-daemon", "--no-configuration-cache", "--console=plain", "--max-workers=1",
    "-Pkotlin.compiler.execution.strategy=in-process",
    "-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8",
    "assembleDebug", "coverageReport", "sonar"
)
$analysisMode = "local working tree -> SonarCloud main branch"
if ($PlanOnly) {
    Write-Output @(
        "sonar",
        "  - root: $projectRoot",
        "  - project: Insaner1980_Numeris-Android (organization: insaner1980)",
        "  - server: https://sonarcloud.io",
        "  - mode: $analysisMode",
        "  - Gradle: $($gradleArguments -join ' ')",
        "  - uploads source and analysis metadata; waits for the quality gate",
        "  - report: reports/sonar.txt",
        "  - actual execution requires -AllowExternalUpload and SONAR_TOKEN"
    )
    exit 0
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
    "sonar", "Root: $projectRoot", "Mode: $analysisMode", "Started: $(Get-Date -Format o)"
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
        if (($result.StandardOutput + "`n" + $result.StandardError) -match '(?m)^QUALITY GATE STATUS: FAILED\b') {
            $summary = "sonar: FINDINGS - analysis completed, but the SonarCloud quality gate failed. " +
                "See https://sonarcloud.io/dashboard?id=Insaner1980_Numeris-Android and $reportPath"
            Add-Content -LiteralPath $reportPath -Encoding utf8 -Value $summary
            Write-Output $summary
            exit 1
        }
        throw "Sonar analysis failed (exit $($result.ExitCode)). See $reportPath"
    }
    Write-Output "sonar: analysis and quality gate passed -> $reportPath"
    exit 0
} catch {
    $message = $_.Exception.Message.Replace($env:SONAR_TOKEN, "[REDACTED_SECRET]")
    Add-Content -LiteralPath $reportPath -Encoding utf8 -Value "ERROR: $message"
    Write-Error $message -ErrorAction Continue
    exit 2
}
