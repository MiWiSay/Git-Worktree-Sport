param([string]$AndroidStudioPath = 'C:\Program Files\Android\Android Studio Panda 3')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java = Join-Path $AndroidStudioPath 'jbr\bin'
$classes = Join-Path $root 'build\branch-popup-tests'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sdk = "$AndroidStudioPath\lib\*;$AndroidStudioPath\plugins\vcs-git\lib\*;$AndroidStudioPath\plugins\vcs-git\lib\modules\*"
$production = Join-Path $root 'build\classes\java\main'
$fixtures = Join-Path $root 'build\regression-tests\classes'
$resources = Join-Path $root 'src\main\resources'
$config = Join-Path $root 'build\branch-popup-test-config'
$classpath = "$fixtures;$production;$resources;$sdk"
& (Join-Path $java 'javac.exe') -encoding UTF-8 -cp $classpath -d $classes (Join-Path $root 'tests\com\miwi\gitworktree\ui\BranchPopupChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Branch popup test compilation failed. Run compileJava and run-regression.ps1 first.' }
& (Join-Path $java 'java.exe') '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' "-Dgit.worktree.sport.testConfigDir=$config" -cp "$classes;$classpath" com.miwi.gitworktree.ui.BranchPopupChecks
if ($LASTEXITCODE -ne 0) { throw 'Branch popup model assertions failed.' }
