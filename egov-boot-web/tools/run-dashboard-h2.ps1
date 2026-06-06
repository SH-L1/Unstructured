param(
    [int]$Port = 8081
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$JarPath = Join-Path $ProjectRoot "target\egovframe-project-1.0.0.jar"

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME must point to a Java 17 JDK."
}

$JavaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path $JavaExe)) {
    throw "JAVA_HOME does not contain bin\java.exe: $env:JAVA_HOME"
}
$JavaVersion = (& $JavaExe -version 2>&1 | Select-Object -First 1)
if ($JavaVersion -notmatch '"17(\.|")') {
    throw "Java 17 is required. Detected: $JavaVersion"
}

Write-Host "Packaging latest dashboard resources..." -ForegroundColor Yellow
Push-Location $ProjectRoot
try {
    & mvn -DskipTests package
} finally {
    Pop-Location
}

Write-Host "Starting Civil Complaint Dashboard." -ForegroundColor Green
Write-Host "URL: http://localhost:$Port/dashboard/" -ForegroundColor Cyan
Write-Host "Press Ctrl+C in this window to stop the server." -ForegroundColor DarkGray

Push-Location $ProjectRoot
try {
    & $JavaExe -jar $JarPath "--spring.profiles.active=dashboard-h2" "--server.port=$Port"
} finally {
    Pop-Location
}
