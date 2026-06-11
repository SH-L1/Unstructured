param(
    [int]$Port = 8081
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$JarPath = Join-Path $ProjectRoot "target\egovframe-project-1.0.0.jar"
$AiEngineDir = Join-Path $ProjectRoot "..\ai-rag-engine"
$resolved = Resolve-Path $AiEngineDir -ErrorAction SilentlyContinue
if ($resolved) { $AiEngineDir = $resolved.Path }

$DefaultJavaHome = "C:\Program Files\Java\jdk-26.0.1"

if (-not $env:JAVA_HOME -and (Test-Path $DefaultJavaHome)) {
    $env:JAVA_HOME = $DefaultJavaHome
}

$JavaExe = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    "java"
}

if ($env:JAVA_HOME -and -not (Test-Path $JavaExe)) {
    throw "JAVA_HOME does not contain bin\java.exe: $env:JAVA_HOME"
}

Write-Host "Packaging latest dashboard resources..." -ForegroundColor Yellow
Push-Location $ProjectRoot
try {
    & mvn -DskipTests package
} finally {
    Pop-Location
}

# Python worker (GPT-4o-mini) 백그라운드로 실행
$PythonExe = Join-Path $AiEngineDir ".venv\Scripts\python.exe"
if (Test-Path $PythonExe) {
    Write-Host "Starting Python AI Worker (GPT-4o-mini)..." -ForegroundColor Yellow
    $WorkerJob = Start-Job -ScriptBlock {
        param($dir, $exe)
        Set-Location $dir
        & $exe worker.py
    } -ArgumentList $AiEngineDir, $PythonExe
    Write-Host "Python worker started (Job ID: $($WorkerJob.Id))" -ForegroundColor Green
} else {
    Write-Host "WARNING: Python venv not found at $PythonExe" -ForegroundColor Red
}

Write-Host "Starting Civil Complaint Dashboard in PostgreSQL Mode." -ForegroundColor Green
Write-Host "URL: http://localhost:$Port/dashboard/" -ForegroundColor Cyan
Write-Host "Press Ctrl+C in this window to stop the server." -ForegroundColor DarkGray

Push-Location $ProjectRoot
try {
    # H2가 아닌 실제 PostgreSQL DB를 바인딩하여 실행합니다.
    # 로그인 세션 보안은 시연/개발 검토를 위해 옵션으로 비활성화 가능하게 하고 기본 구동합니다.
    & $JavaExe "-Dfile.encoding=UTF-8" -jar $JarPath "--server.port=$Port" "--app.security.session.enabled=false" "--SENSITIVE_DATA_KEY=some-local-development-sensitive-data-key-32-chars-long" "--app.worker.service-token=dashboard-worker-service-token-at-least-32-characters"
} finally {
    # 서버 종료 시 워커도 정리
    if ($WorkerJob) {
        Write-Host "Stopping Python worker..." -ForegroundColor Yellow
        Stop-Job -Job $WorkerJob
        Remove-Job -Job $WorkerJob
    }
    Pop-Location
}
