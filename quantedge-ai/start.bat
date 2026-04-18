@echo off
REM QuantEdge AI - one-command launcher for Windows.
REM Opens a backend window on :8000 and a frontend window on :3000.

setlocal ENABLEDELAYEDEXPANSION
cd /d "%~dp0"

echo ==^> QuantEdge AI launcher
echo     backend  = http://localhost:8000
echo     frontend = http://localhost:3000
echo.

REM --- Backend ---
cd backend

if not exist .venv (
  echo ==^> Creating Python virtualenv (.venv)
  python -m venv .venv
)

call .venv\Scripts\activate.bat
python -m pip install --quiet --upgrade pip
python -m pip install --quiet -r requirements.txt

if not exist .env (
  echo ==^> Creating backend\.env from .env.example (fill in your keys!)
  copy /Y .env.example .env >nul
)

echo ==^> Starting FastAPI on :8000 in a new window
start "QuantEdge Backend" cmd /k "call .venv\Scripts\activate.bat && python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload"

cd ..

REM --- Frontend ---
cd frontend

if not exist node_modules (
  echo ==^> Installing frontend dependencies (npm install)
  call npm install --silent
)

echo ==^> Starting Vite dev server on :3000 in a new window
start "QuantEdge Frontend" cmd /k "npm run dev"

cd ..
echo.
echo Both services are starting in separate windows. Close them to stop.
endlocal
