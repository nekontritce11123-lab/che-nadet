# Source-only bootstrap, SHA-256 from gradle.org/release-checksums.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$version = '8.13'
$sha = '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
$bin = Join-Path $root ".tools\gradle-$version\bin\gradle.bat"
if (-not (Test-Path $bin)) {
    Get-Command java -ErrorAction Stop | Out-Null
    $tools = Join-Path $root '.tools'
    New-Item -ItemType Directory -Force $tools | Out-Null
    $zip = Join-Path $tools "gradle-$version.zip"
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $zip
        if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -ne $sha) { throw 'Gradle checksum mismatch; refusing to run.' }
        Expand-Archive -Path $zip -DestinationPath $tools -Force
    } finally { if (Test-Path $zip) { Remove-Item $zip } }
}
& $bin '-p' $root @args
exit $LASTEXITCODE
