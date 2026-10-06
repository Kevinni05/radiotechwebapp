$ErrorActionPreference = 'Stop'
$testRoot = Split-Path -Parent $PSScriptRoot
$testPidPath = Join-Path $testRoot '.dist\tools\tunnel.pid'
if (!(Test-Path -LiteralPath $testPidPath)) { return }
$testPid = [int](Get-Content -LiteralPath $testPidPath)
$testProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$testPid"
$testExpectedDirectory = [IO.Path]::GetFullPath((Join-Path $testRoot '.dist\tools')) + [IO.Path]::DirectorySeparatorChar
if ($testProcess -and $testProcess.ExecutablePath -and $testProcess.ExecutablePath.StartsWith($testExpectedDirectory, [StringComparison]::OrdinalIgnoreCase) -and $testProcess.Name -like 'cloudflared-*.exe') { Stop-Process -Id $testPid }
elseif ($testProcess) { throw 'PID no longer belongs to this RadioTech tunnel; no process was stopped.' }
