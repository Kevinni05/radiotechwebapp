param([Parameter(Mandatory)][string]$ServiceAccountPath, [string]$StorageBucket = '', [string]$ProjectId = 'gestionale-radio')
$ErrorActionPreference = 'Stop'
$backupRoot = Split-Path -Parent $PSScriptRoot
$backupDirectory = Join-Path $backupRoot '.dist\backups'
New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
$backupAcl = Get-Acl -LiteralPath $backupDirectory
$backupAcl.SetAccessRuleProtection($true, $false)
$backupIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$backupRule = [Security.AccessControl.FileSystemAccessRule]::new($backupIdentity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
$backupAcl.AddAccessRule($backupRule)
Set-Acl -LiteralPath $backupDirectory -AclObject $backupAcl
$backupKey = Join-Path $backupDirectory 'backup.key'
if (!(Test-Path -LiteralPath $backupKey)) { $backupBytes = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($backupBytes); [IO.File]::WriteAllBytes($backupKey, $backupBytes) }
$backupConfigPath = Join-Path $backupDirectory 'config.json'
@{ projectId = $ProjectId; serviceAccountPath = (Resolve-Path -LiteralPath $ServiceAccountPath).Path; storageBucket = $StorageBucket; destination = $backupDirectory; keyPath = $backupKey } | ConvertTo-Json | Set-Content -LiteralPath $backupConfigPath
$backupShell = (Get-Command powershell.exe).Source
$backupScript = Join-Path $PSScriptRoot 'run-backup.ps1'
$backupAction = New-ScheduledTaskAction -Execute $backupShell -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $backupScript + '" -ConfigPath "' + $backupConfigPath + '"')
$backupTrigger = New-ScheduledTaskTrigger -Daily -At '03:00'
$backupSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 2)
$backupPrincipal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName 'RadioTech-FreeTest-EncryptedBackup' -Action $backupAction -Trigger $backupTrigger -Settings $backupSettings -Principal $backupPrincipal -Description 'RadioTech test daily encrypted backup; PC and user session required.' -Force | Select-Object TaskName, State
Write-Output 'Daily encrypted backup scheduled. Copy archives and the key separately to protected offsite storage before production use.'
