param()
$ErrorActionPreference = 'Stop'
$instanceRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
. (Join-Path $instanceRoot '.work/devtools\env.ps1')
$serverRoot = Join-Path $instanceRoot '.work/test-server'
if (Get-NetTCPConnection -State Listen -LocalPort 25582 -ErrorAction SilentlyContinue) {
    throw 'Verification port 25582 is already in use. Stop the existing server before starting another.'
}
$modVersion = ((Get-Content -LiteralPath (Join-Path $PSScriptRoot 'gradle.properties') | Where-Object { $_.StartsWith('mod_version=') }) -split '=',2)[1]
$jarName = "SlashBlade-26.1.2-$modVersion.jar"
$sourceJar = Join-Path $instanceRoot "mods\$jarName"
if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) { throw "Build and install $jarName first." }
$runningServer = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -in @('java.exe', 'javaw.exe') -and $_.CommandLine -and $_.CommandLine.Contains($serverRoot)
}
if ($runningServer) { throw 'Wait for the existing verification server to exit normally before updating its mod.' }
# This exact file was installed by the previous project02 verification setup.
# Do not discover or remove other mods by a filename prefix or Mod ID.
$previousName = 'SlashBlade-26.1.2-0.1.2-26.1.2-port.6.jar'
$previousJar = Join-Path $serverRoot "mods\$previousName"
if ($jarName -ne $previousName -and (Test-Path -LiteralPath $previousJar -PathType Leaf)) {
    $previousHash = 'fbb73d374fc119d2952ea27f6ebacb366d4b3331cb617f014d3b2fd0e76db4a3'
    if ((Get-FileHash -LiteralPath $previousJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $previousHash) {
        throw 'The previous verification mod changed; preserve it and resolve the conflict before updating.'
    }
    $archiveRoot = Join-Path $instanceRoot '.work/verification\previous-builds\test-server'
    New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
    $archiveJar = Join-Path $archiveRoot $previousName
    if (Test-Path -LiteralPath $archiveJar) { throw "An archive already exists: $archiveJar. Preserve both files and resolve the conflict." }
    Move-Item -LiteralPath $previousJar -Destination $archiveJar
}
Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $serverRoot "mods\$jarName") -Force
Push-Location -LiteralPath $serverRoot
try {
    $argumentFile = Join-Path $serverRoot 'libraries\net\neoforged\neoforge\26.1.2.114\win_args.txt'
    & (Join-Path $env:JAVA_HOME 'bin\java.exe') '@user_jvm_args.txt' "@$argumentFile" '--nogui'
    exit $LASTEXITCODE
} finally { Pop-Location }
