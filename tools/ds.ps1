param(
    [switch]$AllowExternalAI = $true,
    [string]$Provider = "OpenAI ChatGPT subscription via Codex CLI (gpt-6.1-sol, medium)",
    [string]$ExternalAIDataScope = "entire-project-working-tree",
    [string]$ExternalAICostEstimate = "Uses the existing ChatGPT subscription allowance and account usage limits; no API key billing is configured by this project.",
    [string]$ExternalAIRetentionPolicy = "OpenAI policies and the signed-in ChatGPT account's data controls apply; retention duration is account-dependent."
)

$ProjectCheckCommand = "deep-sec"
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$approval = @{
    AllowExternalAI = $AllowExternalAI
    Provider = $Provider
    ExternalAIDataScope = $ExternalAIDataScope
    ExternalAICostEstimate = $ExternalAICostEstimate
    ExternalAIRetentionPolicy = $ExternalAIRetentionPolicy
}
& "C:\Dev\Android-check\tools\InvokeProjectCheck.ps1" -ProjectCheckCommand $ProjectCheckCommand -Root $ProjectRoot @approval @args
exit $LASTEXITCODE
