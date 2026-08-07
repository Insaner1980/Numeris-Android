$ProjectCheckCommand = "deep-sec"
& "C:\Dev\Android-check\tools\InvokeProjectCheck.ps1" -ProjectCheckCommand $ProjectCheckCommand @args
exit $LASTEXITCODE
