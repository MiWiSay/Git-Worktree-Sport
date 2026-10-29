param(
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio Panda 3\jbr',
    [string[]]$IdePaths = @(
        'C:\Program Files\Android\Android Studio Panda 3',
        'C:\Program Files\Android\Android Studio',
        'C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.2'
    )
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sources = @(
    (Join-Path $root 'src\main\java\com\miwi\gitworktree\service\OperationWaitFeedback.java'),
    (Join-Path $root 'tests\com\miwi\gitworktree\service\IdeSchedulerChecks.java')
)
$index = 0
foreach ($idePath in $IdePaths) {
    $product = Get-Content -LiteralPath (Join-Path $idePath 'product-info.json') -Raw | ConvertFrom-Json
    $classes = Join-Path $root "build\ide-scheduler-tests\$index"
    $index++
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    $sdk = Join-Path $idePath 'lib\*'
    # Compile just the scheduler helper for older runtimes; this is not full-plugin compatibility verification.
    & (Join-Path $JavaHome 'bin\javac.exe') --release 17 -encoding UTF-8 -cp $sdk -d $classes $sources
    if ($LASTEXITCODE -ne 0) { throw "Scheduler smoke compilation failed: $idePath" }
    & (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' -cp "$classes;$sdk" `
        com.miwi.gitworktree.service.IdeSchedulerChecks "$($product.name) $($product.buildNumber)"
    if ($LASTEXITCODE -ne 0) { throw "Scheduler smoke failed: $idePath" }
}
