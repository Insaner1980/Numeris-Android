$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = Split-Path -Parent $PSScriptRoot
$modulePath = Join-Path $repo 'tools\SignRelease.psm1'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message Expected=<$Expected> Actual=<$Actual>"
    }
}

function Assert-BytesEqual {
    param([byte[]]$Expected, [byte[]]$Actual, [string]$Message)
    Assert-Equal $Expected.Length $Actual.Length "$Message (length)"
    for ($index = 0; $index -lt $Expected.Length; $index++) {
        if ($Expected[$index] -ne $Actual[$index]) {
            throw "$Message (byte $index)"
        }
    }
}

function Assert-ThrowsSafely {
    param(
        [scriptblock]$Action,
        [string]$FixtureSecret,
        [string]$ExpectedMessage
    )
    $caught = $null
    try {
        & $Action
    } catch {
        $caught = $_
    }
    Assert-True ($null -ne $caught) $ExpectedMessage
    Assert-True ($caught.ToString().IndexOf($FixtureSecret, [StringComparison]::Ordinal) -lt 0) 'Virhe paljasti fixture-salasanan.'
}

function New-TestFixture {
    param([string]$Name)

    $root = Join-Path ([IO.Path]::GetTempPath()) ("numeris-sign-{0}-{1}" -f $Name, [guid]::NewGuid().ToString('N'))
    $sdk = Join-Path $root 'sdk'
    $output = Join-Path $root 'repo\app\build\outputs\apk\release'
    $signing = Join-Path $root 'signing'
    New-Item -ItemType Directory -Path $sdk, $output, $signing -Force | Out-Null

    $fakeScript = @'
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$ToolArguments)
$ErrorActionPreference = 'Stop'
$commandLineArguments = [Environment]::GetCommandLineArgs()
$scriptArgumentIndex = [Array]::FindIndex(
    $commandLineArguments,
    [Predicate[string]]{ param($value) $value.EndsWith('fake-apksigner.ps1', [StringComparison]::OrdinalIgnoreCase) }
)
if ($scriptArgumentIndex -ge 0 -and $scriptArgumentIndex + 1 -lt $commandLineArguments.Count) {
    $ToolArguments = @($commandLineArguments[($scriptArgumentIndex + 1)..($commandLineArguments.Count - 1)])
}
$record = [ordered]@{ arguments = @($ToolArguments); stdinLineCount = 0; phase = $ToolArguments[0] }
if ($env:NUMERIS_FAKE_HANG_PHASE -eq $ToolArguments[0]) {
    Start-Process `
        -FilePath (Get-Process -Id $PID).Path `
        -ArgumentList @('-NoProfile', '-File', $env:NUMERIS_FAKE_DELAYED_SCRIPT, $env:NUMERIS_FAKE_DELAYED_MARKER) `
        -WindowStyle Hidden | Out-Null
    [IO.File]::WriteAllText($env:NUMERIS_FAKE_CHILD_STARTED, 'started')
    while ($true) { Start-Sleep -Seconds 1 }
}
if ($ToolArguments[0] -eq 'sign') {
    $first = [Console]::In.ReadLine()
    $second = [Console]::In.ReadLine()
    $record.stdinLineCount = @($first, $second).Count
    if ($env:NUMERIS_FAKE_SIGN_FAIL -eq '1') {
        [Console]::Error.WriteLine('safe sign failure')
        exit 31
    }
    [IO.File]::AppendAllText($ToolArguments[-1], '-signed')
} elseif ($ToolArguments[0] -eq 'verify') {
    if ($env:NUMERIS_FAKE_VERIFY_FAIL -eq '1') {
        [Console]::Error.WriteLine('safe verify failure')
        exit 32
    }
}
[IO.File]::AppendAllText(
    $env:NUMERIS_FAKE_LOG,
    (($record | ConvertTo-Json -Compress) + [Environment]::NewLine)
)
[Console]::Out.WriteLine('safe fake output')
'@

    foreach ($version in @('33.0.2', '9.0.0', '34.0.0-rc1', '34.0.0', '35.0.0')) {
        $toolDir = Join-Path $sdk "build-tools\$version"
        New-Item -ItemType Directory -Path $toolDir -Force | Out-Null
        if ($version -ne '35.0.0') {
            Set-Content -LiteralPath (Join-Path $toolDir 'fake-apksigner.ps1') -Value $fakeScript -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $toolDir 'apksigner.bat') -Value '@pwsh.exe -NoProfile -File "%~dp0fake-apksigner.ps1" %*' -Encoding Ascii
        }
    }

    $secret = "fixture-secret-$([guid]::NewGuid().ToString('N'))"
    $secure = ConvertTo-SecureString -String $secret -AsPlainText -Force
    $encrypted = ConvertFrom-SecureString -SecureString $secure
    Set-Content -LiteralPath (Join-Path $signing 'numeris-android-release.password.dpapi') -Value $encrypted -NoNewline
    Set-Content -LiteralPath (Join-Path $signing 'numeris-android-release.jks') -Value 'fixture-keystore'
    Set-Content -LiteralPath (Join-Path $output 'app-release-unsigned.apk') -Value 'unsigned' -NoNewline
    Set-Content -LiteralPath (Join-Path $output 'app-release.apk') -Value 'previous' -NoNewline
    Set-Content -LiteralPath (Join-Path $root 'delayed-marker.ps1') -Value @'
param([string]$MarkerPath)
Start-Sleep -Milliseconds 1500
[IO.File]::WriteAllText($MarkerPath, 'background-survived')
'@
    Set-Content -LiteralPath (Join-Path $root 'repo\fake-gradle.ps1') -Value @'
param(
    [Alias('p')][string]$ProjectRoot,
    [Alias('no-configuration-cache')][switch]$NoConfigurationCache,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$RemainingArguments
)
if ($env:NUMERIS_FAKE_HANG_GRADLE -eq '1') {
    Start-Process `
        -FilePath (Get-Process -Id $PID).Path `
        -ArgumentList @('-NoProfile', '-File', $env:NUMERIS_FAKE_DELAYED_SCRIPT, $env:NUMERIS_FAKE_DELAYED_MARKER) `
        -WindowStyle Hidden | Out-Null
    [IO.File]::WriteAllText($env:NUMERIS_FAKE_CHILD_STARTED, 'started')
    while ($true) { Start-Sleep -Seconds 1 }
}
$gradleArguments = @()
if ($PSBoundParameters.ContainsKey('ProjectRoot')) {
    $gradleArguments += @('-p', $ProjectRoot)
}
$gradleArguments += @($RemainingArguments)
if ($NoConfigurationCache) {
    $gradleArguments += '--no-configuration-cache'
}
[IO.File]::WriteAllText($env:NUMERIS_FAKE_GRADLE_LOG, ($gradleArguments | ConvertTo-Json -Compress))
exit 0
'@
    Set-Content -LiteralPath (Join-Path $root 'repo\gradlew.bat') -Value '@pwsh.exe -NoProfile -File "%~dp0fake-gradle.ps1" %*' -Encoding Ascii

    [pscustomobject]@{
        Root = $root
        Repo = Join-Path $root 'repo'
        Sdk = $sdk
        Signing = $signing
        Output = $output
        Secret = $secret
        Log = Join-Path $root 'apksigner.jsonl'
        GradleLog = Join-Path $root 'gradle.json'
        Gradle = Join-Path $root 'repo\gradlew.bat'
        ChildStarted = Join-Path $root 'child-started.marker'
        DelayedMarker = Join-Path $root 'delayed.marker'
        DelayedScript = Join-Path $root 'delayed-marker.ps1'
    }
}

function Get-FakeRecords {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    @((Get-Content -LiteralPath $Path) | ForEach-Object { $_ | ConvertFrom-Json })
}

function Assert-NoSecretPersistence {
    param($Fixture, [string]$CapturedText = '')
    Assert-True ($CapturedText.IndexOf($Fixture.Secret, [StringComparison]::Ordinal) -lt 0) 'Salasana näkyi kerätyssä stdout/stderrissä.'
    foreach ($file in Get-ChildItem -LiteralPath $Fixture.Root -File -Recurse) {
        $content = [IO.File]::ReadAllText($file.FullName)
        Assert-True ($content.IndexOf($Fixture.Secret, [StringComparison]::Ordinal) -lt 0) "Salasana tallentui tiedostoon $($file.FullName)."
    }
}

function Set-HangingFixtureEnvironment {
    param($Fixture)
    $env:NUMERIS_FAKE_CHILD_STARTED = $Fixture.ChildStarted
    $env:NUMERIS_FAKE_DELAYED_MARKER = $Fixture.DelayedMarker
    $env:NUMERIS_FAKE_DELAYED_SCRIPT = $Fixture.DelayedScript
}

if (-not (Test-Path -LiteralPath $modulePath)) {
    throw "Tuotantomoduuli puuttuu: $modulePath"
}
Import-Module $modulePath -Force

$fixtures = [Collections.Generic.List[object]]::new()
try {
    # Semanttinen stable-valinta ohittaa previewn ja apksignerittoman uusimman hakemiston.
    $fixture = New-TestFixture 'resolver with spaces'
    $fixtures.Add($fixture)
    $resolved = Resolve-NumerisApkSigner -AndroidSdkRoot $fixture.Sdk
    Assert-True $resolved.EndsWith('34.0.0\apksigner.bat', [StringComparison]::OrdinalIgnoreCase) 'Resolveri ei valinnut semanttisesti uusinta vakaata työkalua.'

    $env:NUMERIS_FAKE_LOG = $fixture.Log
    $captured = & {
        Invoke-NumerisReleaseSigning -RepositoryRoot $fixture.Repo -SigningDirectory $fixture.Signing -AndroidSdkRoot $fixture.Sdk -NoBuild
    } *>&1 | Out-String
    $records = Get-FakeRecords $fixture.Log
    Assert-Equal 2 $records.Count 'Sign ja verify eivät molemmat käynnistyneet.'
    Assert-Equal 'sign' $records[0].phase 'Ensimmäinen vaihe ei ollut sign.'
    Assert-Equal 2 $records[0].stdinLineCount 'Sign ei lukenut tarkalleen kahta stdin-riviä.'
    Assert-True ($records[0].arguments -contains '--ks-pass') 'Signiltä puuttui --ks-pass.'
    Assert-True ($records[0].arguments -contains '--key-pass') 'Signiltä puuttui --key-pass.'
    Assert-Equal 'stdin' $records[0].arguments[([array]::IndexOf($records[0].arguments, '--ks-pass') + 1)] '--ks-pass ei käyttänyt stdin-kanavaa.'
    Assert-Equal 'stdin' $records[0].arguments[([array]::IndexOf($records[0].arguments, '--key-pass') + 1)] '--key-pass ei käyttänyt stdin-kanavaa.'
    Assert-True (($records | ConvertTo-Json -Depth 5).IndexOf($fixture.Secret, [StringComparison]::Ordinal) -lt 0) 'Salasana näkyi argumenttilistassa.'
    Assert-Equal 'verify' $records[1].phase 'Toinen vaihe ei ollut verify.'
    foreach ($argument in @('verify', '--verbose', '--print-certs')) {
        Assert-True ($records[1].arguments -contains $argument) "Verify-argumentti puuttui: $argument"
    }
    Assert-Equal 'unsigned-signed' ([IO.File]::ReadAllText((Join-Path $fixture.Output 'app-release.apk'))) 'Onnistunut julkaisu ei korvannut lopullista APK:ta.'
    Assert-Equal 0 @(Get-ChildItem -LiteralPath $fixture.Output -Filter '.app-release.*.tmp.apk').Count 'Ajokohtainen temp jäi onnistumisen jälkeen.'
    Assert-NoSecretPersistence $fixture $captured

    foreach ($phase in @('sign', 'verify')) {
        $failureFixture = New-TestFixture "$phase-failure"
        $fixtures.Add($failureFixture)
        $env:NUMERIS_FAKE_LOG = $failureFixture.Log
        $env:NUMERIS_FAKE_SIGN_FAIL = if ($phase -eq 'sign') { '1' } else { '0' }
        $env:NUMERIS_FAKE_VERIFY_FAIL = if ($phase -eq 'verify') { '1' } else { '0' }
        $before = [IO.File]::ReadAllBytes((Join-Path $failureFixture.Output 'app-release.apk'))
        Assert-ThrowsSafely {
            Invoke-NumerisReleaseSigning -RepositoryRoot $failureFixture.Repo -SigningDirectory $failureFixture.Signing -AndroidSdkRoot $failureFixture.Sdk -NoBuild
        } $failureFixture.Secret "$phase-virheen piti epäonnistua."
        $after = [IO.File]::ReadAllBytes((Join-Path $failureFixture.Output 'app-release.apk'))
        Assert-BytesEqual $before $after "$phase-virhe muutti aiempaa APK:ta."
        Assert-Equal 0 @(Get-ChildItem -LiteralPath $failureFixture.Output -Filter '.app-release.*.tmp.apk').Count "$phase-virhe jätti tempin."
        Assert-NoSecretPersistence $failureFixture
    }
    Remove-Item Env:NUMERIS_FAKE_SIGN_FAIL, Env:NUMERIS_FAKE_VERIFY_FAIL -ErrorAction SilentlyContinue

    $interruptFixture = New-TestFixture 'interrupt'
    $fixtures.Add($interruptFixture)
    $beforeInterrupt = [IO.File]::ReadAllBytes((Join-Path $interruptFixture.Output 'app-release.apk'))
    Assert-ThrowsSafely {
        Invoke-NumerisReleaseSigning -RepositoryRoot $interruptFixture.Repo -SigningDirectory $interruptFixture.Signing -AndroidSdkRoot $interruptFixture.Sdk -NoBuild -AfterTemporaryApkCreated { throw 'safe injected interruption' }
    } $interruptFixture.Secret 'Tempin jälkeisen poikkeuksen piti epäonnistua.'
    Assert-BytesEqual $beforeInterrupt ([IO.File]::ReadAllBytes((Join-Path $interruptFixture.Output 'app-release.apk'))) 'Keskeytys muutti aiempaa APK:ta.'
    Assert-Equal 0 @(Get-ChildItem -LiteralPath $interruptFixture.Output -Filter '.app-release.*.tmp.apk').Count 'Keskeytys jätti tempin.'

    # Olemassa oleva kohde ei saa hetkeksikään kadota atomisen replace-operaation aikana.
    $atomicFixture = New-TestFixture 'atomic-replace'
    $fixtures.Add($atomicFixture)
    $missingMarker = Join-Path $atomicFixture.Root 'target-was-missing'
    $target = Join-Path $atomicFixture.Output 'app-release.apk'
    $watcherReady = Join-Path $atomicFixture.Root 'watcher-ready'
    $watcherStop = Join-Path $atomicFixture.Root 'watcher-stop'
    $watcher = Start-Job -ScriptBlock {
        param($Target, $Marker, $Ready, $Stop)
        $readyWritten = $false
        while (-not [IO.File]::Exists($Stop)) {
            try {
                $stream = [IO.File]::Open($Target, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)
                $stream.Dispose()
                if (-not $readyWritten) {
                    [IO.File]::WriteAllText($Ready, 'ready')
                    $readyWritten = $true
                }
            } catch [IO.FileNotFoundException] {
                [IO.File]::WriteAllText($Marker, 'missing')
                break
            } catch [IO.DirectoryNotFoundException] {
                [IO.File]::WriteAllText($Marker, 'missing')
                break
            }
        }
    } -ArgumentList $target, $missingMarker, $watcherReady, $watcherStop
    $watcherReadyWait = [Diagnostics.Stopwatch]::StartNew()
    while (-not (Test-Path -LiteralPath $watcherReady)) {
        if ($watcherReadyWait.Elapsed -gt [TimeSpan]::FromSeconds(5)) {
            $watcherErrors = @()
            Receive-Job $watcher -ErrorVariable +watcherErrors -ErrorAction SilentlyContinue | Out-Null
            throw "Atomisuusvahti ei valmistunut: $($watcherErrors -join '; ')"
        }
        Start-Sleep -Milliseconds 10
    }
    $env:NUMERIS_FAKE_LOG = $atomicFixture.Log
    Invoke-NumerisReleaseSigning -RepositoryRoot $atomicFixture.Repo -SigningDirectory $atomicFixture.Signing -AndroidSdkRoot $atomicFixture.Sdk -NoBuild
    Set-Content -LiteralPath $watcherStop -Value 'stop'
    Wait-Job $watcher | Out-Null
    $watcherErrors = @()
    Receive-Job $watcher -ErrorVariable +watcherErrors -ErrorAction SilentlyContinue | Out-Null
    Assert-Equal 'Completed' $watcher.State 'Atomisuusvahdin job ei valmistunut.'
    Assert-Equal 0 $watcherErrors.Count 'Atomisuusvahdin job kirjoitti virheen.'
    Assert-True ($null -eq $watcher.ChildJobs[0].JobStateInfo.Reason) 'Atomisuusvahdin job päättyi poikkeukseen.'
    Remove-Job $watcher
    Assert-True (-not (Test-Path -LiteralPath $missingMarker)) 'Atominen replace jätti kohteen hetkeksi puuttumaan.'

    # Kun kohdetta ei ole, saman hakemiston move julkaisee APK:n.
    $moveFixture = New-TestFixture 'atomic-move'
    $fixtures.Add($moveFixture)
    Remove-Item -LiteralPath (Join-Path $moveFixture.Output 'app-release.apk')
    $env:NUMERIS_FAKE_LOG = $moveFixture.Log
    Invoke-NumerisReleaseSigning -RepositoryRoot $moveFixture.Repo -SigningDirectory $moveFixture.Signing -AndroidSdkRoot $moveFixture.Sdk -NoBuild
    Assert-True (Test-Path -LiteralPath (Join-Path $moveFixture.Output 'app-release.apk')) 'Uuden kohteen atominen move epäonnistui.'

    # Temp-nimet ovat yksilöllisiä eikä väärän ajon tempiä poisteta.
    $tempFixture = New-TestFixture 'temp-identity'
    $fixtures.Add($tempFixture)
    $pathA = New-NumerisSigningTemporaryPath -OutputDirectory $tempFixture.Output
    $pathB = New-NumerisSigningTemporaryPath -OutputDirectory $tempFixture.Output
    Assert-True ($pathA -ne $pathB) 'Rinnakkaisille ajoille muodostui sama temp-nimi.'
    Set-Content -LiteralPath $pathB -Value 'other-run'
    Assert-ThrowsSafely {
        Invoke-NumerisReleaseSigning -RepositoryRoot $tempFixture.Repo -SigningDirectory $tempFixture.Signing -AndroidSdkRoot $tempFixture.Sdk -NoBuild -AfterTemporaryApkCreated { throw 'safe stop' }
    } $tempFixture.Secret 'Ohjatun poikkeuksen piti epäonnistua.'
    Assert-True (Test-Path -LiteralPath $pathB) 'Ajo poisti toisen ajon temp-tiedoston.'

    # Kaksi oikeasti rinnakkaista ajoa käyttää eri temp-tiedostoja ja julkaisee vain omansa.
    $parallelFixture = New-TestFixture 'parallel'
    $fixtures.Add($parallelFixture)
    $parallelLogs = @(
        (Join-Path $parallelFixture.Root 'parallel-a.jsonl'),
        (Join-Path $parallelFixture.Root 'parallel-b.jsonl')
    )
    $parallelJobs = foreach ($parallelLog in $parallelLogs) {
        Start-Job -ScriptBlock {
            param($ModulePath, $RepositoryRoot, $SigningDirectory, $SdkRoot, $LogPath)
            $env:NUMERIS_FAKE_LOG = $LogPath
            Import-Module $ModulePath -Force
            Invoke-NumerisReleaseSigning -RepositoryRoot $RepositoryRoot -SigningDirectory $SigningDirectory -AndroidSdkRoot $SdkRoot -NoBuild
        } -ArgumentList $modulePath, $parallelFixture.Repo, $parallelFixture.Signing, $parallelFixture.Sdk, $parallelLog
    }
    $parallelJobs | Wait-Job | Out-Null
    foreach ($parallelJob in $parallelJobs) {
        Assert-Equal 'Completed' $parallelJob.State 'Rinnakkainen signing-ajo ei valmistunut.'
        Receive-Job $parallelJob | Out-Null
        Remove-Job $parallelJob
    }
    $parallelTempPaths = foreach ($parallelLog in $parallelLogs) {
        $signRecord = @(Get-FakeRecords $parallelLog | Where-Object phase -eq 'sign')[0]
        $signRecord.arguments[-1]
    }
    Assert-Equal 2 @($parallelTempPaths | Select-Object -Unique).Count 'Rinnakkaiset ajot käyttivät samaa temp-polkuja.'
    Assert-Equal 0 @(Get-ChildItem -LiteralPath $parallelFixture.Output -Filter '.app-release.*.tmp.apk').Count 'Rinnakkaisajo jätti temp-tiedoston.'
    Assert-Equal 'unsigned-signed' ([IO.File]::ReadAllText((Join-Path $parallelFixture.Output 'app-release.apk'))) 'Rinnakkaisajo julkaisi vieraan tai puutteellisen APK:n.'
    Assert-NoSecretPersistence $parallelFixture

    # SDK-fallbackit ja turvalliset puutevirheet.
    $fallbackFixture = New-TestFixture 'fallbacks'
    $fixtures.Add($fallbackFixture)
    $oldHome = $env:ANDROID_HOME
    $oldRoot = $env:ANDROID_SDK_ROOT
    try {
        $env:ANDROID_HOME = $fallbackFixture.Sdk
        $env:ANDROID_SDK_ROOT = $null
        Assert-True ((Resolve-NumerisApkSigner).Contains('34.0.0')) 'ANDROID_HOME-fallback ei toiminut.'
        $env:ANDROID_HOME = $null
        $env:ANDROID_SDK_ROOT = $fallbackFixture.Sdk
        Assert-True ((Resolve-NumerisApkSigner).Contains('34.0.0')) 'ANDROID_SDK_ROOT-fallback ei toiminut.'
        $env:ANDROID_HOME = $null
        $env:ANDROID_SDK_ROOT = $null
        Assert-ThrowsSafely { Resolve-NumerisApkSigner } $fallbackFixture.Secret 'Puuttuvan SDK:n piti epäonnistua.'
    } finally {
        $env:ANDROID_HOME = $oldHome
        $env:ANDROID_SDK_ROOT = $oldRoot
    }
    $missingToolSdk = Join-Path $fallbackFixture.Root 'missing-tool-sdk'
    New-Item -ItemType Directory -Path (Join-Path $missingToolSdk 'build-tools\99.0.0') -Force | Out-Null
    Assert-ThrowsSafely { Resolve-NumerisApkSigner -AndroidSdkRoot $missingToolSdk } $fallbackFixture.Secret 'Puuttuvan apksignerin piti epäonnistua.'
    $fallbackBefore = [IO.File]::ReadAllBytes((Join-Path $fallbackFixture.Output 'app-release.apk'))
    Assert-ThrowsSafely {
        Invoke-NumerisReleaseSigning -RepositoryRoot $fallbackFixture.Repo -SigningDirectory $fallbackFixture.Signing -AndroidSdkRoot $missingToolSdk -NoBuild
    } $fallbackFixture.Secret 'Virheellisen SDK:n signing-ajon piti epäonnistua.'
    Assert-BytesEqual $fallbackBefore ([IO.File]::ReadAllBytes((Join-Path $fallbackFixture.Output 'app-release.apk'))) 'SDK-virhe muutti lopullista APK:ta.'

    # DPAPI- ja process-start -virheet ovat salaisuudettomia.
    $dpapiFixture = New-TestFixture 'dpapi-failure'
    $fixtures.Add($dpapiFixture)
    Set-Content -LiteralPath (Join-Path $dpapiFixture.Signing 'numeris-android-release.password.dpapi') -Value 'not-valid-dpapi-data'
    Assert-ThrowsSafely {
        Invoke-NumerisReleaseSigning -RepositoryRoot $dpapiFixture.Repo -SigningDirectory $dpapiFixture.Signing -AndroidSdkRoot $dpapiFixture.Sdk -NoBuild
    } $dpapiFixture.Secret 'Virheellisen DPAPI-tiedoston piti epäonnistua.'
    $processFixture = New-TestFixture 'process-failure'
    $fixtures.Add($processFixture)
    $shellProbe = & {
        try {
            Invoke-NumerisSigningProcess `
                -FilePath (Join-Path $processFixture.Root 'does-not-exist.bat') `
                -ArgumentList @('safe&ver') `
                -Operation 'Shell-meta-probe'
            'UNSAFE_SUCCESS'
        } catch {
            $_.Exception.Message
        }
    } *>&1 | Out-String
    Assert-True (-not $shellProbe.Contains('UNSAFE_SUCCESS')) 'Batch-metamerkki pääsi cmd.exe-tulkintaan onnistuneena komentona.'
    Assert-True ($shellProbe.IndexOf('Microsoft Windows', [StringComparison]::OrdinalIgnoreCase) -lt 0) 'Batch-metamerkki käynnisti ver-komennon.'
    foreach ($unsafeValue in @('a&b', 'a|b', 'a<b', 'a>b', 'a%b', 'a^b', 'a!b', "a`r`nb", "a`tb")) {
        Assert-ThrowsSafely {
            Invoke-NumerisSigningProcess -FilePath (Join-Path $processFixture.Root 'tool.bat') -ArgumentList @($unsafeValue)
        } $processFixture.Secret "Turvaton batch-arvo hyväksyttiin: $unsafeValue"
    }
    Remove-Item -LiteralPath (Join-Path $processFixture.Sdk 'build-tools\34.0.0\apksigner.bat')
    Set-Content -LiteralPath (Join-Path $processFixture.Sdk 'build-tools\34.0.0\apksigner.bat') -Value '@exit /b 9' -Encoding Ascii
    Assert-ThrowsSafely {
        Invoke-NumerisReleaseSigning -RepositoryRoot $processFixture.Repo -SigningDirectory $processFixture.Signing -AndroidSdkRoot $processFixture.Sdk -NoBuild
    } $processFixture.Secret 'Process-virheen piti epäonnistua.'
    Assert-ThrowsSafely {
        Invoke-NumerisSigningProcess -FilePath (Join-Path $processFixture.Root 'missing-process.exe') -ArgumentList @('safe') -StandardInputLines @($processFixture.Secret) -SensitiveValues @($processFixture.Secret)
    } $processFixture.Secret 'Process-start -virheen piti epäonnistua.'
    $echoScript = Join-Path $processFixture.Root 'echo-stdin.ps1'
    Set-Content -LiteralPath $echoScript -Value '$value = [Console]::In.ReadLine(); [Console]::Out.WriteLine($value); [Console]::Error.WriteLine($value)'
    $redactedResult = Invoke-NumerisSigningProcess -FilePath $echoScript -ArgumentList @() -StandardInputLines @($processFixture.Secret) -SensitiveValues @($processFixture.Secret)
    Assert-True ($redactedResult.StandardOutput.Contains('[REDACTED]')) 'Stdout-salaisuutta ei redaktoitu.'
    Assert-True ($redactedResult.StandardError.Contains('[REDACTED]')) 'Stderr-salaisuutta ei redaktoitu.'
    Assert-True (($redactedResult | Out-String).IndexOf($processFixture.Secret, [StringComparison]::Ordinal) -lt 0) 'Prosessitulos paljasti salaisuuden.'

    $publishFixture = New-TestFixture 'publish-failure'
    $fixtures.Add($publishFixture)
    $crossDirectoryTemp = Join-Path $publishFixture.Root 'foreign.tmp.apk'
    Set-Content -LiteralPath $crossDirectoryTemp -Value 'candidate'
    Assert-ThrowsSafely {
        Publish-NumerisSignedApk -TemporaryApk $crossDirectoryTemp -DestinationApk (Join-Path $publishFixture.Output 'app-release.apk')
    } $publishFixture.Secret 'Julkaisun hakemistorajauksen piti epäonnistua.'
    Assert-Equal 'previous' ([IO.File]::ReadAllText((Join-Path $publishFixture.Output 'app-release.apk'))) 'Julkaisuvirhe muutti lopullista APK:ta.'
    Assert-NoSecretPersistence $dpapiFixture
    Assert-NoSecretPersistence $processFixture
    Assert-NoSecretPersistence $publishFixture

    # Timeout, cancellation ja Ctrl+C:tä vastaava pipeline-poikkeus tappavat koko prosessipuun.
    foreach ($hangCase in @('sign-timeout', 'verify-timeout', 'build-timeout', 'cancel-after')) {
        $hangFixture = New-TestFixture $hangCase
        $fixtures.Add($hangFixture)
        Set-HangingFixtureEnvironment $hangFixture
        $env:NUMERIS_FAKE_LOG = $hangFixture.Log
        $env:NUMERIS_FAKE_HANG_PHASE = if ($hangCase -eq 'verify-timeout') { 'verify' } else { 'sign' }
        $env:NUMERIS_FAKE_HANG_GRADLE = if ($hangCase -eq 'build-timeout') { '1' } else { '0' }
        $beforeHang = [IO.File]::ReadAllBytes((Join-Path $hangFixture.Output 'app-release.apk'))
        $cancellationSource = [Threading.CancellationTokenSource]::new()
        if ($hangCase -eq 'cancel-after') {
            $cancellationSource.CancelAfter(500)
        }
        $hangWatch = [Diagnostics.Stopwatch]::StartNew()
        Assert-ThrowsSafely {
            $invokeParameters = @{
                RepositoryRoot = $hangFixture.Repo
                SigningDirectory = $hangFixture.Signing
                AndroidSdkRoot = $hangFixture.Sdk
                BuildTimeout = [TimeSpan]::FromMilliseconds(500)
                SignTimeout = [TimeSpan]::FromMilliseconds(500)
                VerifyTimeout = [TimeSpan]::FromMilliseconds(500)
                CancellationToken = $cancellationSource.Token
            }
            if ($hangCase -ne 'build-timeout') {
                $invokeParameters.NoBuild = $true
            }
            Invoke-NumerisReleaseSigning @invokeParameters
        } $hangFixture.Secret "$hangCase ei keskeytynyt."
        $cancellationSource.Dispose()
        Assert-True ($hangWatch.Elapsed -lt [TimeSpan]::FromSeconds(5)) "$hangCase ei palautunut rajatussa ajassa."
        Start-Sleep -Seconds 2
        Assert-True (-not (Test-Path -LiteralPath $hangFixture.DelayedMarker)) "$hangCase jätti lapsiprosessin eloon."
        Assert-BytesEqual $beforeHang ([IO.File]::ReadAllBytes((Join-Path $hangFixture.Output 'app-release.apk'))) "$hangCase muutti aiemman APK:n."
        Assert-Equal 0 @(Get-ChildItem -LiteralPath $hangFixture.Output -Filter '.app-release.*.tmp.apk').Count "$hangCase jätti tempin."
        Assert-NoSecretPersistence $hangFixture
        Remove-Item Env:NUMERIS_FAKE_HANG_PHASE, Env:NUMERIS_FAKE_HANG_GRADLE -ErrorAction SilentlyContinue
    }

    $pipelineFixture = New-TestFixture 'pipeline-stop'
    $fixtures.Add($pipelineFixture)
    $pipelineBefore = [IO.File]::ReadAllBytes((Join-Path $pipelineFixture.Output 'app-release.apk'))
    $pipelineJob = Start-Job -ScriptBlock {
        param($ModulePath, $Fixture)
        $env:NUMERIS_FAKE_LOG = $Fixture.Log
        $env:NUMERIS_FAKE_HANG_PHASE = 'sign'
        $env:NUMERIS_FAKE_CHILD_STARTED = $Fixture.ChildStarted
        $env:NUMERIS_FAKE_DELAYED_MARKER = $Fixture.DelayedMarker
        $env:NUMERIS_FAKE_DELAYED_SCRIPT = $Fixture.DelayedScript
        Import-Module $ModulePath -Force
        Invoke-NumerisReleaseSigning `
            -RepositoryRoot $Fixture.Repo `
            -SigningDirectory $Fixture.Signing `
            -AndroidSdkRoot $Fixture.Sdk `
            -NoBuild `
            -SignTimeout ([TimeSpan]::FromSeconds(10)) `
            -SigningProcessStartedHook {
                $wait = [Diagnostics.Stopwatch]::StartNew()
                while (-not (Test-Path -LiteralPath $Fixture.ChildStarted)) {
                    if ($wait.Elapsed -gt [TimeSpan]::FromSeconds(2)) { throw 'Fake child did not start.' }
                    Start-Sleep -Milliseconds 10
                }
                throw [Management.Automation.PipelineStoppedException]::new()
            }
    } -ArgumentList $modulePath, $pipelineFixture
    $pipelineStartupWait = [Diagnostics.Stopwatch]::StartNew()
    while (-not (Test-Path -LiteralPath $pipelineFixture.ChildStarted)) {
        if ($pipelineStartupWait.Elapsed -gt [TimeSpan]::FromSeconds(5)) {
            throw 'PipelineStopped-fixturen lapsiprosessi ei käynnistynyt.'
        }
        Start-Sleep -Milliseconds 10
    }
    $pipelineWatch = [Diagnostics.Stopwatch]::StartNew()
    $pipelineJob | Wait-Job -Timeout 3 | Out-Null
    Assert-True ($pipelineJob.State -ne 'Running') 'PipelineStopped-simulaatio ei palautunut rajatussa ajassa.'
    Receive-Job $pipelineJob -ErrorAction SilentlyContinue | Out-Null
    Remove-Job $pipelineJob -Force
    Assert-True ($pipelineWatch.Elapsed -lt [TimeSpan]::FromSeconds(3)) 'PipelineStopped-simulaatio ylitti aikarajan.'
    Start-Sleep -Seconds 2
    Assert-True (-not (Test-Path -LiteralPath $pipelineFixture.DelayedMarker)) 'PipelineStopped jätti lapsiprosessin eloon.'
    Assert-BytesEqual $pipelineBefore ([IO.File]::ReadAllBytes((Join-Path $pipelineFixture.Output 'app-release.apk'))) 'PipelineStopped muutti aiemman APK:n.'
    Assert-Equal 0 @(Get-ChildItem -LiteralPath $pipelineFixture.Output -Filter '.app-release.*.tmp.apk').Count 'PipelineStopped jätti tempin.'
    Assert-NoSecretPersistence $pipelineFixture

    # -NoBuild ohittaa Gradlen; oletuspolku käyttää valetyökalua oikeilla argumenteilla.
    $buildFixture = New-TestFixture 'build with spaces'
    $fixtures.Add($buildFixture)
    $env:NUMERIS_FAKE_LOG = $buildFixture.Log
    $env:NUMERIS_FAKE_GRADLE_LOG = $buildFixture.GradleLog
    Invoke-NumerisReleaseSigning -RepositoryRoot $buildFixture.Repo -SigningDirectory $buildFixture.Signing -AndroidSdkRoot $buildFixture.Sdk -NoBuild -GradleExecutable $buildFixture.Gradle
    Assert-True (-not (Test-Path -LiteralPath $buildFixture.GradleLog)) '-NoBuild käynnisti Gradlen.'
    Set-Content -LiteralPath (Join-Path $buildFixture.Output 'app-release.apk') -Value 'previous'
    Invoke-NumerisReleaseSigning -RepositoryRoot $buildFixture.Repo -SigningDirectory $buildFixture.Signing -AndroidSdkRoot $buildFixture.Sdk
    $gradleArgumentsData = Get-Content -Raw -LiteralPath $buildFixture.GradleLog | ConvertFrom-Json
    $gradleArguments = @($gradleArgumentsData)
    foreach ($argument in @('-p', $buildFixture.Repo, 'assembleRelease', '--no-configuration-cache')) {
        Assert-True ($gradleArguments -contains $argument) "Gradle-argumentti puuttui: $argument"
    }

    foreach ($completedFixture in $fixtures) {
        Assert-NoSecretPersistence $completedFixture
    }

    'SignRelease.Tests.ps1: OK'
} finally {
    Remove-Item Env:NUMERIS_FAKE_LOG, Env:NUMERIS_FAKE_GRADLE_LOG, Env:NUMERIS_FAKE_SIGN_FAIL, Env:NUMERIS_FAKE_VERIFY_FAIL, Env:NUMERIS_FAKE_HANG_PHASE, Env:NUMERIS_FAKE_HANG_GRADLE, Env:NUMERIS_FAKE_CHILD_STARTED, Env:NUMERIS_FAKE_DELAYED_MARKER, Env:NUMERIS_FAKE_DELAYED_SCRIPT -ErrorAction SilentlyContinue
    foreach ($fixtureToRemove in $fixtures) {
        if (Test-Path -LiteralPath $fixtureToRemove.Root) {
            Remove-Item -LiteralPath $fixtureToRemove.Root -Recurse -Force
        }
    }
}
