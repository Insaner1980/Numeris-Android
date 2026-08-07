$ProjectCheckCommand = "compose-rules"
& "C:\Dev\Android-check\tools\InvokeProjectCheck.ps1" -ProjectCheckCommand $ProjectCheckCommand @args
exit $LASTEXITCODE
