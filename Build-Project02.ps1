param([switch]$GameTests, [switch]$Install)
$ErrorActionPreference = 'Stop'
$instanceRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
. (Join-Path $instanceRoot '.work/devtools\env.ps1')
$gradleTasks = @('build')
if ($GameTests) { $gradleTasks += 'runGameTestServer' }
Push-Location -LiteralPath $PSScriptRoot
try {
    & '.\gradlew.bat' @gradleTasks '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
if ($Install) {
    $runningGame = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('java.exe', 'javaw.exe') -and
        $_.CommandLine -and $_.CommandLine.Contains($instanceRoot) -and
        ($_.CommandLine.Contains('--gameDir') -or $_.CommandLine.Contains('win_args.txt'))
    }
    if ($runningGame) { throw 'Save and quit the project02 clients and verification server before replacing their mod JARs.' }
    $modVersion = ((Get-Content -LiteralPath (Join-Path $PSScriptRoot 'gradle.properties') | Where-Object { $_.StartsWith('mod_version=') }) -split '=',2)[1]
    $jarName = "SlashBlade-26.1.2-$modVersion.jar"
    $builtJar = Join-Path $PSScriptRoot "build\libs\$jarName"
    $installedJar = Join-Path $instanceRoot "mods\$jarName"
    Copy-Item -LiteralPath $builtJar -Destination $installedJar -Force
    Get-FileHash -LiteralPath $installedJar -Algorithm SHA256
}
