param(
    [string]$JavaHome,
    [string]$AndroidStudioPath = 'C:\Program Files\Android\Android Studio Panda 3',
    [switch]$SkipIcons
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) {
    $JavaHome = Join-Path $AndroidStudioPath 'jbr'
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) { $JavaHome = $env:JAVA_HOME }
}
if (-not $JavaHome) { throw 'Provide -JavaHome pointing to a JDK compatible with the target IDE.' }
$root = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $root 'build\regression-tests\classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$testConfig = Join-Path $root 'build\regression-tests\config'
$resolvedRoot = [System.IO.Path]::GetFullPath($root).TrimEnd('\') + '\'
$resolvedTestConfig = [System.IO.Path]::GetFullPath($testConfig)
if (-not $resolvedTestConfig.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Regression config path must stay within the project build directory.'
}
if (Test-Path -LiteralPath $resolvedTestConfig) { Remove-Item -LiteralPath $resolvedTestConfig -Recurse -Force }
$sources = @(
    'src\main\java\com\miwi\gitworktree\model\WorktreeEntry.java',
    'src\main\java\com\miwi\gitworktree\ui\FlatJson.java',
    'src\main\java\com\miwi\gitworktree\ui\LanguageCatalog.java',
    'src\main\java\com\miwi\gitworktree\ui\I18n.java',
    'src\main\java\com\miwi\gitworktree\ui\WorktreeGroups.java',
    'src\main\java\com\miwi\gitworktree\ui\WorktreeHighlight.java',
    'src\main\java\com\miwi\gitworktree\ui\WorktreePreferences.java',
    'src\main\java\com\miwi\gitworktree\ui\WorktreeActionVisibility.java',
    'src\main\java\com\miwi\gitworktree\ui\WorktreeCreatedTime.java',
    'src\main\java\com\miwi\gitworktree\service\WorktreeRemovalPolicy.java',
    'src\main\java\com\miwi\gitworktree\service\OperationWaitFeedback.java',
    'src\main\java\com\miwi\gitworktree\service\WorktreePaths.java',
    'src\main\java\com\miwi\gitworktree\service\WorktreeListParser.java',
    'tests\fixtures\com\intellij\DynamicBundle.java',
    'tests\fixtures\com\intellij\ide\util\PropertiesComponent.java',
    'tests\fixtures\com\intellij\openapi\application\PathManager.java',
    'tests\fixtures\com\intellij\openapi\application\ApplicationNamesInfo.java',
    'tests\com\miwi\gitworktree\ui\RegressionChecks.java',
    'tests\com\miwi\gitworktree\service\OperationWaitFeedbackChecks.java',
    'tests\com\miwi\gitworktree\ui\HighlightChecks.java'
) | ForEach-Object { Join-Path $root $_ }
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Regression compilation failed.' }
$resources = Join-Path $root 'src\main\resources'
& (Join-Path $JavaHome 'bin\java.exe') '-Dfile.encoding=UTF-8' "-Dgit.worktree.sport.testConfigDir=$resolvedTestConfig" -cp "$classes;$resources" com.miwi.gitworktree.ui.RegressionChecks
if ($LASTEXITCODE -ne 0) { throw 'Regression assertions failed.' }
& (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' -cp "$classes;$resources" com.miwi.gitworktree.ui.HighlightChecks
if ($LASTEXITCODE -ne 0) { throw 'Highlight assertions failed.' }
& (Join-Path $JavaHome 'bin\java.exe') '-Dfile.encoding=UTF-8' -cp "$classes;$resources" com.miwi.gitworktree.service.OperationWaitFeedbackChecks
if ($LASTEXITCODE -ne 0) { throw 'Operation wait feedback assertions failed.' }
if (-not $SkipIcons) {
    $sdk = Join-Path $AndroidStudioPath 'lib\*'
    if (-not (Test-Path -LiteralPath (Join-Path $AndroidStudioPath 'lib\intellij.platform.util.ui.jar'))) {
        throw 'Target IDE libraries unavailable; provide -AndroidStudioPath or explicitly use -SkipIcons.'
    }
    $iconClasses = Join-Path $root 'build\icon-tests'
    New-Item -ItemType Directory -Path $iconClasses -Force | Out-Null
    & (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $sdk -d $iconClasses (Join-Path $root 'tests\IconSmokeCheck.java')
    if ($LASTEXITCODE -ne 0) { throw 'Icon test compilation failed.' }
    & (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' -cp "$iconClasses;$sdk" IconSmokeCheck $resources $iconClasses
    if ($LASTEXITCODE -ne 0) { throw 'Icon rendering assertions failed.' }
}
