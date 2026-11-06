@echo off
cd /d "%~dp0\.."
title Aksharvel - download the Marathi speech model
echo ============================================================
echo   Downloading the Marathi speech model into assets
echo   A few hundred MB. Leave it to finish.
echo ============================================================
echo.
python --version >nul 2>&1 || (echo Python not found. Install it from python.org, tick "Add to PATH". & pause & exit /b 1)
python -m pip install --quiet huggingface_hub
python tools\fetch_model.py %*
echo.
pause
