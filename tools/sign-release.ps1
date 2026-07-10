param(
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$signingDir = Join-Path $env:USERPROFILE '.numeris\signing'
$keystore = Join-Path $signingDir 'numeris-android-release.jks'
$passwordFile = Join-Path $signingDir 'numeris-android-release.password.dpapi'
$unsignedApk = Join-Path $repo 'app\build\outputs\apk\release\app-release-unsigned.apk'
$signedApk = Join-Path $repo 'app\build\outputs\apk\release\app-release.apk'
$androidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
$apksigner = Get-ChildItem (Join-Path $androidSdk 'build-tools') -Recurse -Filter apksigner.bat |
    Sort-Object FullName -Descending |
    Select-Object -First 1

if (-not $NoBuild) {
    & (Join-Path $repo 'gradlew.bat') -p $repo assembleRelease --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
}

if (-not (Test-Path $keystore) -or -not (Test-Path $passwordFile)) {
    throw "Signing material is missing from $signingDir."
}
if (-not (Test-Path $unsignedApk)) { throw "Unsigned APK was not found: $unsignedApk" }
if (-not $apksigner) { throw 'apksigner was not found from the Android SDK.' }

$secure = (Get-Content -Raw $passwordFile).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    Copy-Item -LiteralPath $unsignedApk -Destination $signedApk -Force
    & $apksigner.FullName sign `
        --ks $keystore `
        --ks-key-alias numeris-release `
        --ks-pass "pass:$password" `
        --key-pass "pass:$password" `
        $signedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $apksigner.FullName verify --verbose --print-certs $signedApk
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK verification failed.' }
} finally {
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    $password = $null
}
