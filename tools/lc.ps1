$ProjectCheckCommand = "lint-check"
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
& "C:\Dev\Android-check\tools\InvokeProjectCheck.ps1" -ProjectCheckCommand $ProjectCheckCommand -Root $ProjectRoot -ProjectId "numeris-android" @args
exit $LASTEXITCODE
