param(
    [switch]$NoBuild,

    [TimeSpan]$BuildTimeout = ([TimeSpan]::FromMinutes(30)),

    [TimeSpan]$SignTimeout = ([TimeSpan]::FromMinutes(2)),

    [TimeSpan]$VerifyTimeout = ([TimeSpan]::FromMinutes(2)),

    [Threading.CancellationToken]$CancellationToken = ([Threading.CancellationToken]::None)
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'SignRelease.psm1') -Force

Invoke-NumerisReleaseSigning `
    -RepositoryRoot $repo `
    -NoBuild:$NoBuild `
    -BuildTimeout $BuildTimeout `
    -SignTimeout $SignTimeout `
    -VerifyTimeout $VerifyTimeout `
    -CancellationToken $CancellationToken
