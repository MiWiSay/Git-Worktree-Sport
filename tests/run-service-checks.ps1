param([string]$IdePath = 'C:\Program Files\Android\Android Studio Panda 3',
      [string]$JavaHome = 'C:\Program Files\Android\Android Studio Panda 3\jbr')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $root 'build\service-checks\classes'
$fixtures = Join-Path $root ('build\service-checks\repo-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sdk = "$IdePath\lib\*;$IdePath\plugins\vcs-git\lib\*;$IdePath\plugins\vcs-git\lib\modules\*"
$production = Join-Path $root 'build\classes\java\main'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'service-fixtures') -Filter '*.java' -Recurse | ForEach-Object FullName)
$sources += Join-Path $PSScriptRoot 'com\miwi\gitworktree\service\WorktreeServiceChecks.java'
$sources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures') -Filter '*.java' -Recurse | ForEach-Object FullName)
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp "$production;$sdk" -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Service test compilation failed.' }
$resources = Join-Path $root 'src\main\resources'
$config = Join-Path $root 'build\service-checks\config'
& (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' `
    "-Dgit.worktree.sport.testConfigDir=$config" -cp "$classes;$production;$resources;$sdk" `
    com.miwi.gitworktree.service.WorktreeServiceChecks $fixtures
if ($LASTEXITCODE -ne 0) { throw 'Production service integration checks failed.' }
