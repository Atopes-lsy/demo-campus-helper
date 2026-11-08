[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8089',
    [string]$AdminUsername = 'admin',
    [string]$AdminPassword = $env:CAMPUS_HELPER_ADMIN_PASSWORD
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw '请设置 CAMPUS_HELPER_ADMIN_PASSWORD，或传入 -AdminPassword；脚本不使用内置管理员密码。'
}
$baseUrl = $BaseUrl.TrimEnd('/')

$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/admin/login" `
    -ContentType 'application/json' `
    -TimeoutSec 30 -Body (@{ username = $AdminUsername; password = $AdminPassword } | ConvertTo-Json)
if (-not $login.data.token) { throw '登录接口未返回 token' }
$headers = @{ Authorization = "Bearer $($login.data.token)" }

try {
    Invoke-RestMethod "$baseUrl/api/payments?page=1&size=1" -TimeoutSec 30 | Out-Null
    throw '匿名管理接口本应返回 401'
} catch {
    if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 401) { throw }
}

$overview = (Invoke-RestMethod "$baseUrl/api/bi/overview" -Headers $headers -TimeoutSec 30).data
$search = (Invoke-RestMethod "$baseUrl/api/search/orders?page=1&size=1" -Headers $headers -TimeoutSec 30).data
if ($null -eq $overview -or $null -eq $search -or $null -eq $search.total) {
    throw '看板或检索未返回预期数据结构'
}
$rawCount = & docker compose --project-directory $PSScriptRoot exec -T elasticsearch `
    curl --fail --silent --show-error http://localhost:9200/campus-orders/_count
if ($LASTEXITCODE -ne 0) { throw '无法读取 Elasticsearch 原生计数，请检查 Compose 服务与索引。' }
$esCount = ($rawCount | ConvertFrom-Json).count
if ($null -eq $esCount -or [long]$search.total -ne [long]$esCount) {
    throw "ES 数量不一致: index=$esCount, api=$($search.total)"
}

[pscustomobject]@{
    Auth = 'PASS'
    AnonymousAdminApi = 'PASS'
    TodayOrders = $overview.todayOrderCount
    ElasticsearchDocuments = $esCount
    SearchDocuments = $search.total
} | Format-List
