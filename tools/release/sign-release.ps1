param(
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$OutputApk,
    [string]$SigningSettings = (Join-Path $env:USERPROFILE '.pleos-stay-plus-signing/signing-credentials.json'),
    [string]$AndroidSdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }),
    [string]$BuildToolsVersion = '36.0.0'
)
$ErrorActionPreference = 'Stop'
$inputPath = (Resolve-Path -LiteralPath $UnsignedApk).Path
$outputPath = [IO.Path]::GetFullPath($OutputApk)
if ($inputPath -eq $outputPath) { throw 'Input and output APK paths must differ.' }
if (Test-Path -LiteralPath $outputPath) { throw 'Output already exists; preserve the previous APK.' }
$settingsPath = (Resolve-Path -LiteralPath $SigningSettings).Path
$settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
if (-not (Test-Path -LiteralPath $settings.keystore) -or -not $settings.alias -or -not $settings.storePassword -or -not $settings.keyPassword) { throw 'Private signing settings are incomplete.' }
$tools = Join-Path $AndroidSdk "build-tools/$BuildToolsVersion"
$zipalign = Join-Path $tools 'zipalign.exe'
$apksigner = Join-Path $tools 'apksigner.bat'
if (-not (Test-Path -LiteralPath $zipalign) -or -not (Test-Path -LiteralPath $apksigner)) { throw 'Required existing Android Build Tools are missing.' }
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($outputPath)) | Out-Null
$alignedPath = Join-Path ([IO.Path]::GetDirectoryName($outputPath)) ('aligned-' + [Guid]::NewGuid().ToString('N') + '.apk')
$oldStorePassword = $env:STAY_RELEASE_STORE_PASSWORD
$oldKeyPassword = $env:STAY_RELEASE_KEY_PASSWORD
try {
    & $zipalign -P 16 4 $inputPath $alignedPath
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
    $env:STAY_RELEASE_STORE_PASSWORD = $settings.storePassword
    $env:STAY_RELEASE_KEY_PASSWORD = $settings.keyPassword
    & $apksigner sign --ks $settings.keystore --ks-key-alias $settings.alias --ks-pass env:STAY_RELEASE_STORE_PASSWORD --key-pass env:STAY_RELEASE_KEY_PASSWORD --v4-signing-enabled false --out $outputPath $alignedPath
    if ($LASTEXITCODE -ne 0) { throw 'Release APK signing failed.' }
    & $apksigner verify --verbose --print-certs $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }
    & $zipalign -c -P 16 4 $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed.' }
    $hash = (Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText($outputPath + '.sha256', "$hash  $([IO.Path]::GetFileName($outputPath))" + [Environment]::NewLine)
    Write-Output "Verified signed Release APK: $outputPath"
} finally {
    $env:STAY_RELEASE_STORE_PASSWORD = $oldStorePassword
    $env:STAY_RELEASE_KEY_PASSWORD = $oldKeyPassword
    $settings = $null
    if (Test-Path -LiteralPath $alignedPath) { Remove-Item -LiteralPath $alignedPath }
}
