param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$Username = "admin",
    [string]$Password = "123456",
    [string]$DocPath = "D:\code\doc-fusion\test\docs\sample.pdf",
    [string]$TemplatePath = "D:\code\doc-fusion\test\templates\sample.xlsx",
    [string]$UserRequirement = "完成填表工作，按模板要求提取文档信息",
    [int]$PollMax = 120,
    [int]$PollIntervalSec = 2,
    [string]$OutputFile = "D:\code\doc-fusion\test\out\result.xlsx"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost($url, $body, $token = $null) {
    $headers = @{}
    if ($token) {
        $headers["Authorization"] = "Bearer $token"
    }
    return Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType "application/json" -Body ($body | ConvertTo-Json -Depth 10)
}

Write-Host "[1/6] login..."
$loginResp = Invoke-JsonPost "$BaseUrl/api/auth/login" @{ username = $Username; password = $Password }
$token = $loginResp.data.token
if (-not $token) { throw "login failed: token not found" }

Write-Host "[2/6] upload document..."
$docUpload = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/documents/upload" -Headers @{ Authorization = "Bearer $token" } -Form @{ files = Get-Item $DocPath }
$docSetId = $docUpload.data.id
if (-not $docSetId) { throw "document upload failed: documentSetId not found" }

Write-Host "[3/6] upload template..."
$tplUpload = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/templates/upload" -Headers @{ Authorization = "Bearer $token" } -Form @{ file = Get-Item $TemplatePath }
$templateId = $tplUpload.data.id
if (-not $templateId) { throw "template upload failed: templateId not found" }

Write-Host "[4/6] submit fill task..."
$submitResp = Invoke-JsonPost "$BaseUrl/api/fill/submit" @{
    documentSetId = $docSetId
    templateId = $templateId
    userRequirement = $UserRequirement
} $token
$taskId = $submitResp.data.id
if (-not $taskId) { throw "submit failed: taskId not found" }
Write-Host "taskId=$taskId"

Write-Host "[5/6] polling task status..."
$status = ""
for ($i = 0; $i -lt $PollMax; $i++) {
    Start-Sleep -Seconds $PollIntervalSec
    $taskResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/fill/tasks/$taskId" -Headers @{ Authorization = "Bearer $token" }
    $status = $taskResp.data.status
    Write-Host ("poll[{0}] status={1}" -f $i, $status)
    if ($status -eq "SUCCESS" -or $status -eq "FAILED" -or $status -eq "TIMEOUT") { break }
}
if ($status -ne "SUCCESS") {
    throw "task finished with status=$status"
}

Write-Host "[6/6] download result..."
$outDir = Split-Path -Path $OutputFile -Parent
if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}
Invoke-WebRequest -Method Get -Uri "$BaseUrl/api/fill/download/$taskId" -Headers @{ Authorization = "Bearer $token" } -OutFile $OutputFile
Write-Host "done => $OutputFile"
