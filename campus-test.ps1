[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ($env:CAMPUS_HELPER_BASE_URL) { $env:CAMPUS_HELPER_BASE_URL } else { 'http://localhost:8089' }),
    [string]$AdminUsername = $(if ($env:CAMPUS_HELPER_ADMIN_USERNAME) { $env:CAMPUS_HELPER_ADMIN_USERNAME } else { 'admin' }),
    [string]$AdminPassword = $env:CAMPUS_HELPER_ADMIN_PASSWORD
)
$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'test-api.ps1'
if (-not (Test-Path -LiteralPath $script)) { throw '缺少 test-api.ps1，请取得完整仓库后重试。' }
& $script -BaseUrl $BaseUrl -AdminUsername $AdminUsername -AdminPassword $AdminPassword
