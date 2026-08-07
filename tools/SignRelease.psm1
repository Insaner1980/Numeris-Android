Set-StrictMode -Version Latest

function Resolve-NumerisApkSigner {
    [CmdletBinding()]
    param(
        [string]$AndroidSdkRoot
    )

    $sdkCandidates = [Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($AndroidSdkRoot)) {
        $sdkCandidates.Add($AndroidSdkRoot)
    } else {
        foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
            if (-not [string]::IsNullOrWhiteSpace($candidate) -and -not $sdkCandidates.Contains($candidate)) {
                $sdkCandidates.Add($candidate)
            }
        }
    }

    foreach ($sdkCandidate in $sdkCandidates) {
        $buildToolsRoot = Join-Path $sdkCandidate 'build-tools'
        if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) {
            continue
        }

        $stableTools = foreach ($directory in Get-ChildItem -LiteralPath $buildToolsRoot -Directory) {
            if ($directory.Name -notmatch '^\d+\.\d+\.\d+$') {
                continue
            }
            $toolPath = Join-Path $directory.FullName 'apksigner.bat'
            if (Test-Path -LiteralPath $toolPath -PathType Leaf) {
                [pscustomobject]@{
                    Version = [version]$directory.Name
                    Path = $toolPath
                }
            }
        }

        $selected = $stableTools | Sort-Object Version -Descending | Select-Object -First 1
        if ($null -ne $selected) {
            return $selected.Path
        }
    }

    throw 'Android SDK Build Tools -hakemistosta ei löytynyt vakaata apksigner-työkalua.'
}

function New-NumerisSigningTemporaryPath {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$OutputDirectory
    )

    Join-Path $OutputDirectory ('.app-release.{0}.tmp.apk' -f [guid]::NewGuid().ToString('N'))
}

function Assert-NumerisBatchArgumentSafety {
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string[]]$Value
    )

    foreach ($candidate in $Value) {
        if ($candidate -match '[\x00-\x1F\x7F&|<>%^!()"]') {
            throw 'Batch-prosessin polku tai argumentti sisältää kielletyn shell- tai kontrollimerkin.'
        }
    }
}

function ConvertTo-NumerisProcessArgumentText {
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string]$Argument
    )

    if ($Argument -notmatch '[\s"]') {
        return $Argument
    }
    return '"' + ($Argument -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
}

function Stop-NumerisSigningProcessTree {
    param(
        [Parameter(Mandatory)]
        [Diagnostics.Process]$Process
    )

    if ($Process.HasExited) {
        return $true
    }
    try {
        if ($env:OS -eq 'Windows_NT') {
            $taskKillInfo = [Diagnostics.ProcessStartInfo]::new()
            $taskKillInfo.FileName = [IO.Path]::Combine($env:SystemRoot, 'System32', 'taskkill.exe')
            $taskKillInfo.Arguments = "/PID $($Process.Id) /T /F"
            $taskKillInfo.UseShellExecute = $false
            $taskKillInfo.CreateNoWindow = $true
            $taskKillInfo.RedirectStandardOutput = $true
            $taskKillInfo.RedirectStandardError = $true
            $taskKill = [Diagnostics.Process]::new()
            $taskKill.StartInfo = $taskKillInfo
            try {
                [void]$taskKill.Start()
                [void]$taskKill.StandardOutput.ReadToEnd()
                [void]$taskKill.StandardError.ReadToEnd()
                [void]$taskKill.WaitForExit(5000)
            }
            finally {
                $taskKill.Dispose()
            }
        }
        else {
            $Process.Kill()
        }
        [void]$Process.WaitForExit(5000)
    }
    catch {
        return $Process.HasExited
    }
    return $Process.HasExited
}

function Invoke-NumerisSigningProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$FilePath,

        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [string[]]$ArgumentList,

        [AllowEmptyCollection()]
        [string[]]$StandardInputLines = @(),

        [AllowEmptyCollection()]
        [string[]]$SensitiveValues = @(),

        [string]$Operation = 'Ulkoinen työkalu',

        [TimeSpan]$Timeout = ([TimeSpan]::FromMinutes(10)),

        [Threading.CancellationToken]$CancellationToken = ([Threading.CancellationToken]::None),

        [scriptblock]$ProcessStartedHook
    )

    if ($Timeout -ne [Threading.Timeout]::InfiniteTimeSpan -and $Timeout -le [TimeSpan]::Zero) {
        throw 'Prosessin määräajan on oltava positiivinen.'
    }

    $processInfo = [Diagnostics.ProcessStartInfo]::new()
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.RedirectStandardInput = $true
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true

    $effectiveArguments = [System.Collections.Generic.List[string]]::new()
    $extension = [IO.Path]::GetExtension($FilePath)
    $isBatch = $extension.Equals('.bat', [StringComparison]::OrdinalIgnoreCase) -or
        $extension.Equals('.cmd', [StringComparison]::OrdinalIgnoreCase)
    if ($isBatch) {
        Assert-NumerisBatchArgumentSafety -Value (@($FilePath) + $ArgumentList)
        $processInfo.FileName = if ([string]::IsNullOrWhiteSpace($env:ComSpec)) { 'cmd.exe' } else { $env:ComSpec }
        $batchCommandText = @(
            @($FilePath) + @($ArgumentList) |
                ForEach-Object { ConvertTo-NumerisProcessArgumentText -Argument $_ }
        ) -join ' '
        foreach ($shellArgument in @('/d', '/s', '/c', $batchCommandText)) {
            [void]$effectiveArguments.Add($shellArgument)
        }
    } elseif ($extension.Equals('.ps1', [StringComparison]::OrdinalIgnoreCase)) {
        $processInfo.FileName = (Get-Process -Id $PID).Path
        foreach ($shellArgument in @('-NoProfile', '-File', $FilePath)) {
            [void]$effectiveArguments.Add($shellArgument)
        }
    } else {
        $processInfo.FileName = $FilePath
    }
    if (-not $isBatch) {
        foreach ($argument in $ArgumentList) {
            [void]$effectiveArguments.Add($argument)
        }
    }
    $argumentListProperty = $processInfo.PSObject.Properties['ArgumentList']
    if ($null -ne $argumentListProperty) {
        $nativeArguments = if ($isBatch) {
            @('/d', '/s', '/c', 'call', $FilePath) + @($ArgumentList)
        }
        else {
            @($effectiveArguments)
        }
        foreach ($argument in $nativeArguments) {
            [void]$processInfo.ArgumentList.Add($argument)
        }
    }
    elseif ($isBatch) {
        # cmd.exe käyttää omaa /c-lainausmalliaan; koko valmiiksi validoitu
        # batch-komento annetaan yhtenä lainattuna komentona.
        $processInfo.Arguments = '/d /s /c "' + $batchCommandText + '"'
    }
    else {
        $processInfo.Arguments = @(
            $effectiveArguments |
                ForEach-Object { ConvertTo-NumerisProcessArgumentText -Argument $_ }
        ) -join ' '
    }

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $processInfo
    $processStarted = $false
    try {
        if ($CancellationToken.IsCancellationRequested) {
            throw "$Operation peruutettiin ennen käynnistystä."
        }
        try {
            if (-not $process.Start()) {
                throw 'Process did not start.'
            }
            $processStarted = $true
        } catch {
            throw "$Operation ei käynnistynyt turvallisesti."
        }

        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        try {
            foreach ($inputLine in $StandardInputLines) {
                $process.StandardInput.WriteLine($inputLine)
            }
        } finally {
            $process.StandardInput.Close()
        }

        if ($null -ne $ProcessStartedHook) {
            & $ProcessStartedHook
        }
        $waitStopwatch = [Diagnostics.Stopwatch]::StartNew()
        while (-not $process.WaitForExit(100)) {
            if ($CancellationToken.IsCancellationRequested) {
                throw "$Operation peruutettiin."
            }
            if (
                $Timeout -ne [Threading.Timeout]::InfiniteTimeSpan -and
                $waitStopwatch.Elapsed -ge $Timeout
            ) {
                throw "$Operation ylitti määräajan."
            }
        }
        $waitStopwatch.Stop()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        foreach ($sensitiveValue in $SensitiveValues) {
            if (-not [string]::IsNullOrEmpty($sensitiveValue)) {
                $stdout = $stdout.Replace($sensitiveValue, '[REDACTED]')
                $stderr = $stderr.Replace($sensitiveValue, '[REDACTED]')
            }
        }

        if ($process.ExitCode -ne 0) {
            throw "$Operation epäonnistui (exit $($process.ExitCode))."
        }

        [pscustomobject]@{
            ExitCode = $process.ExitCode
            StandardOutput = $stdout
            StandardError = $stderr
        }
    } finally {
        $processTreeStopped = $true
        if ($processStarted -and -not $process.HasExited) {
            $processTreeStopped = Stop-NumerisSigningProcessTree -Process $process
        }
        $process.Dispose()
        if (-not $processTreeStopped) {
            throw "$Operation-prosessipuuta ei saatu pysäytettyä varmennetusti."
        }
    }
}

function Publish-NumerisSignedApk {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$TemporaryApk,

        [Parameter(Mandatory)]
        [string]$DestinationApk
    )

    $temporaryDirectory = [IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($TemporaryApk))
    $destinationDirectory = [IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($DestinationApk))
    if (-not $temporaryDirectory.Equals($destinationDirectory, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Väliaikainen ja lopullinen APK eivät ole samassa hakemistossa.'
    }

    if ([IO.File]::Exists($DestinationApk)) {
        if ($env:OS -eq 'Windows_NT') {
            $nativeFileType = 'NumerisSigning.NativeFile' -as [type]
            if ($null -eq $nativeFileType) {
                $nativeFileType = Add-Type -PassThru -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace NumerisSigning {
    public static class NativeFile {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern bool MoveFileEx(string existingFileName, string newFileName, int flags);
    }
}
'@
            }
            $moveFileReplaceExisting = 0x1
            $moveFileWriteThrough = 0x8
            $moveSucceeded = $nativeFileType.GetMethod('MoveFileEx').Invoke(
                $null,
                @($TemporaryApk, $DestinationApk, ($moveFileReplaceExisting -bor $moveFileWriteThrough))
            )
            if (-not $moveSucceeded) {
                throw [ComponentModel.Win32Exception]::new(
                    [Runtime.InteropServices.Marshal]::GetLastWin32Error(),
                    'Allekirjoitetun APK:n atominen julkaisu epäonnistui.'
                )
            }
        }
        else {
            $backupApk = "$DestinationApk.replace-backup-$([guid]::NewGuid().ToString('N'))"
            try {
                [IO.File]::Replace($TemporaryApk, $DestinationApk, $backupApk, $true)
            }
            finally {
                if ([IO.File]::Exists($backupApk)) {
                    [IO.File]::Delete($backupApk)
                }
            }
        }
    } else {
        [IO.File]::Move($TemporaryApk, $DestinationApk)
    }
}

function Invoke-NumerisReleaseSigning {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$RepositoryRoot,

        [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.numeris\signing'),

        [string]$AndroidSdkRoot,

        [switch]$NoBuild,

        [string]$GradleExecutable = (Join-Path $RepositoryRoot 'gradlew.bat'),

        [scriptblock]$AfterTemporaryApkCreated,

        [TimeSpan]$BuildTimeout = ([TimeSpan]::FromMinutes(30)),

        [TimeSpan]$SignTimeout = ([TimeSpan]::FromMinutes(2)),

        [TimeSpan]$VerifyTimeout = ([TimeSpan]::FromMinutes(2)),

        [Threading.CancellationToken]$CancellationToken = ([Threading.CancellationToken]::None),

        [scriptblock]$SigningProcessStartedHook
    )

    $keystore = Join-Path $SigningDirectory 'numeris-android-release.jks'
    $passwordFile = Join-Path $SigningDirectory 'numeris-android-release.password.dpapi'
    $outputDirectory = Join-Path $RepositoryRoot 'app\build\outputs\apk\release'
    $unsignedApk = Join-Path $outputDirectory 'app-release-unsigned.apk'
    $destinationApk = Join-Path $outputDirectory 'app-release.apk'
    $temporaryApk = $null
    $securePassword = $null
    $passwordPointer = [IntPtr]::Zero
    $plainTextPassword = $null

    try {
        if (-not $NoBuild) {
            if (-not (Test-Path -LiteralPath $GradleExecutable -PathType Leaf)) {
                throw 'Gradle-wrapperia ei löytynyt.'
            }
            [void](Invoke-NumerisSigningProcess `
                -FilePath $GradleExecutable `
                -ArgumentList @('-p', $RepositoryRoot, 'assembleRelease', '--no-configuration-cache') `
                -Timeout $BuildTimeout `
                -CancellationToken $CancellationToken `
                -Operation 'Release-build')
        }

        if (-not (Test-Path -LiteralPath $keystore -PathType Leaf) -or
            -not (Test-Path -LiteralPath $passwordFile -PathType Leaf)) {
            throw 'Allekirjoitusmateriaali puuttuu suojatusta hakemistosta.'
        }
        if (-not (Test-Path -LiteralPath $unsignedApk -PathType Leaf)) {
            throw 'Allekirjoittamatonta APK:ta ei löytynyt.'
        }
        $apksigner = Resolve-NumerisApkSigner -AndroidSdkRoot $AndroidSdkRoot

        try {
            $securePassword = (Get-Content -Raw -LiteralPath $passwordFile).Trim() | ConvertTo-SecureString
            $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
            $plainTextPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
        } catch {
            throw 'DPAPI-salasanan avaaminen epäonnistui.'
        }

        $temporaryApk = New-NumerisSigningTemporaryPath -OutputDirectory $outputDirectory
        [IO.File]::Copy($unsignedApk, $temporaryApk, $false)
        if ($null -ne $AfterTemporaryApkCreated) {
            & $AfterTemporaryApkCreated
        }

        [void](Invoke-NumerisSigningProcess `
            -FilePath $apksigner `
            -ArgumentList @(
                'sign',
                '--ks', $keystore,
                '--ks-key-alias', 'numeris-release',
                '--ks-pass', 'stdin',
                '--key-pass', 'stdin',
                $temporaryApk
            ) `
            -StandardInputLines @($plainTextPassword, $plainTextPassword) `
            -SensitiveValues @($plainTextPassword) `
            -Timeout $SignTimeout `
            -CancellationToken $CancellationToken `
            -ProcessStartedHook $SigningProcessStartedHook `
            -Operation 'APK:n allekirjoitus')

        [void](Invoke-NumerisSigningProcess `
            -FilePath $apksigner `
            -ArgumentList @('verify', '--verbose', '--print-certs', $temporaryApk) `
            -SensitiveValues @($plainTextPassword) `
            -Timeout $VerifyTimeout `
            -CancellationToken $CancellationToken `
            -Operation 'Allekirjoitetun APK:n verifiointi')

        Publish-NumerisSignedApk -TemporaryApk $temporaryApk -DestinationApk $destinationApk
        $temporaryApk = $null
        $destinationApk
    } finally {
        if (-not [string]::IsNullOrWhiteSpace($temporaryApk) -and [IO.File]::Exists($temporaryApk)) {
            [IO.File]::Delete($temporaryApk)
        }
        if ($passwordPointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
        }
        $plainTextPassword = $null
        $securePassword = $null
    }
}

Export-ModuleMember -Function @(
    'Invoke-NumerisReleaseSigning',
    'Invoke-NumerisSigningProcess',
    'New-NumerisSigningTemporaryPath',
    'Publish-NumerisSignedApk',
    'Resolve-NumerisApkSigner'
)
