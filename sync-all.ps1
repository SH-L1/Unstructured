# PostgreSQL 데이터베이스로 모든 원천 데이터 동기화
$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
$AiEngineDir = Join-Path $ProjectRoot "ai-rag-engine"
$PythonExe = Join-Path $AiEngineDir ".venv\Scripts\python.exe"

if (-not (Test-Path $PythonExe)) {
    throw "Python virtualenv not found at: $PythonExe"
}

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Starting Data Sync for PostgreSQL (Real Service Database)" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

# 1. 로컬 파일 데이터 마트 적재 (민원편람, 자치법규 목록, 새올 상담민원 이력 등)
Write-Host "[1/5] Ingesting local file data mart (Manuals, Saeol history, etc.)..." -ForegroundColor Yellow
& $PythonExe (Join-Path $AiEngineDir "sync_local_file_data_mart.py")
Write-Host "Local file data mart sync completed successfully." -ForegroundColor Green

# 2. 조직도 및 부서 배정 규칙 적재 (조직도 DOCX 파일로부터 부서 및 추천 룰 생성)
Write-Host "[2/5] Ingesting Asan city organization chart and building rules..." -ForegroundColor Yellow
& $PythonExe (Join-Path $AiEngineDir "sync_organization_chart.py")
Write-Host "Organization chart and routing rules sync completed successfully." -ForegroundColor Green

# 3. 법제처 API 공식 국가법령 수집 및 적재 (도로교통법, 도로법, 폐기물관리법 등 실법령 전체)
Write-Host "[3/5] Syncing official national laws from Law API..." -ForegroundColor Yellow
try {
    & $PythonExe (Join-Path $AiEngineDir "sync_official_sources.py")
    Write-Host "Official national laws sync completed successfully." -ForegroundColor Green
} catch {
    Write-Host "WARNING: Official laws sync failed or partially completed (Check API key / Internet connection)." -ForegroundColor Red
}

# 4. 법제처 API 아산시 자치법규(조례) 수집 및 적재
Write-Host "[4/5] Syncing Asan local ordinances from Law API..." -ForegroundColor Yellow
try {
    & $PythonExe (Join-Path $AiEngineDir "sync_local_ordinances.py")
    Write-Host "Local ordinances sync completed successfully." -ForegroundColor Green
} catch {
    Write-Host "WARNING: Local ordinances sync failed or partially completed (Check API key / Internet connection)." -ForegroundColor Red
}

# 5. 충청남도 민원서식 및 절차 API 적재
Write-Host "[5/5] Syncing Chungnam civil complaint forms/procedures..." -ForegroundColor Yellow
try {
    & $PythonExe (Join-Path $AiEngineDir "sync_minwon_forms.py")
    Write-Host "Minwon forms sync completed successfully." -ForegroundColor Green
} catch {
    Write-Host "WARNING: Minwon forms sync failed (Check API / Internet connection)." -ForegroundColor Red
}

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Data synchronization process complete! Ready for PostgreSQL mode." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
