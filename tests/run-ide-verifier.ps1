param(
    [string[]]$IdePaths,
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio Panda 3\jbr',
    [string]$VerifierJar,
    [string]$PluginZip
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $PluginZip) { $PluginZip = Join-Path $root 'outputs\Git Worktree Sport-1.0.4.zip' }
if (-not $IdePaths) {
    $IdePaths = @(Get-ChildItem (Join-Path $root 'build\ide-api-audit') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'audit-source.json') } | ForEach-Object FullName)
}
if (-not $IdePaths) { throw 'Supply -IdePaths or fetch official audit libraries first.' }
if (-not $VerifierJar) {
    $VerifierJar = Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.jetbrains.intellij.plugins\verifier-cli\1.411') `
        -Filter '*-all.jar' -Recurse | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $VerifierJar) { throw 'Run Gradle verifyPlugin to resolve Plugin Verifier, or supply -VerifierJar.' }
$reports = Join-Path $root 'build\reports\multiIdeVerifierFinal'
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$failed = @()
$before = (Get-FileHash -LiteralPath $PluginZip -Algorithm SHA256).Hash
foreach ($ide in $IdePaths) {
    $product = Get-Content -LiteralPath (Join-Path $ide 'product-info.json') -Raw | ConvertFrom-Json
    $id = "$($product.productCode)-$($product.buildNumber)"
    $log = Join-Path $reports "$id.log"
    # Verifier clears its report directory and extraction cache at startup. Isolate each invocation.
    $productReports = Join-Path $reports $id
    $verifierHome = Join-Path $root ("build\verifier-cache-" + $id)
    & (Join-Path $JavaHome 'bin\java.exe') -Xmx2g "-Dplugin.verifier.home.dir=$verifierHome" `
        -jar $VerifierJar check-plugin $PluginZip $ide `
        -offline -verification-reports-dir $productReports -runtime-dir $JavaHome *> $log
    $exit = $LASTEXITCODE
    $verdict = Get-ChildItem -LiteralPath $productReports -Filter verification-verdict.txt -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1
    $text = if ($verdict) { (Get-Content -LiteralPath $verdict.FullName -Raw).Trim() } else { 'No verdict' }
    Write-Output "$id : $text (exit $exit)"
    if ($exit -ne 0 -or $text -ne 'Compatible') { $failed += $id }
}
if ((Get-FileHash -LiteralPath $PluginZip -Algorithm SHA256).Hash -ne $before) { throw 'Plugin ZIP changed during verification.' }
Write-Output "Verified artifact SHA256: $before"
if ($failed) { throw "IDE verification requires attention: $($failed -join ', ')" }
