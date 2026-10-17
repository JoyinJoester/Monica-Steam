param(
    [Parameter(Mandatory = $true)][string]$SdkPath,
    [Parameter(Mandatory = $true)][string]$JdkPath,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$DebugKeyStore = "$env:USERPROFILE/.android/debug.keystore"
)
$ErrorActionPreference = 'Stop'
# Only builds. Installation must first verify no real Steam package exists on the emulator.
$buildTools = Join-Path $SdkPath 'build-tools/35.0.0'
$platform = Join-Path $SdkPath 'platforms/android-35/android.jar'
$fixtureOutput = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $fixtureOutput | Out-Null
$classes = Join-Path $fixtureOutput 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& "$JdkPath/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $platform -d $classes "$PSScriptRoot/HostActivity.java"
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$env:JAVA_HOME = $JdkPath
& "$buildTools/d8.bat" --lib $platform --output $fixtureOutput "$classes/com/monica/handofftest/HostActivity.class"
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
$unsigned = Join-Path $fixtureOutput 'unsigned.apk'
& "$buildTools/aapt2.exe" link -I $platform --manifest "$PSScriptRoot/AndroidManifest.xml" -o $unsigned
if ($LASTEXITCODE -ne 0) { throw 'aapt2 failed' }
& "$JdkPath/bin/jar.exe" uf $unsigned -C $fixtureOutput classes.dex
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
$aligned = Join-Path $fixtureOutput 'aligned.apk'
& "$buildTools/zipalign.exe" -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
$apk = Join-Path $fixtureOutput 'mafile-test-host.apk'
& "$buildTools/apksigner.bat" sign --ks $DebugKeyStore --ks-pass pass:android --key-pass pass:android --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }
Write-Output $apk
