param(
    [string]$Python = 'python',
    [ValidateSet('serve', 'instructor', 'test')][string]$Mode = 'serve',
    [string]$Username,
    [string]$Name,
    [int]$Port = 8080
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (!(Test-Path -LiteralPath '.venv/Scripts/python.exe')) {
        & $Python -m venv .venv
        if ($LASTEXITCODE -ne 0) { throw 'Python 3.12 이상을 설치하거나 -Python으로 실행 파일을 지정하세요.' }
    }
    $taskRuntime = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
    & $taskRuntime -m pip install -r requirements.txt
    if ($LASTEXITCODE -ne 0) { throw '서버 패키지 설치 실패' }
    if ($Mode -eq 'instructor') {
        if (!$Username -or !$Name) { throw '-Username 아이디 -Name 이름을 입력하세요.' }
        & $taskRuntime app.py create-instructor --username $Username --name $Name
    } elseif ($Mode -eq 'test') {
        & $taskRuntime -m unittest test_platform -v
    } else {
        & $taskRuntime -m uvicorn app:app --host 0.0.0.0 --port $Port
    }
    if ($LASTEXITCODE -ne 0) { throw '서버 명령 실행 실패' }
} finally { Pop-Location }
