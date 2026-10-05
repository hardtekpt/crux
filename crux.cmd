@echo off
rem Thin wrapper so the dev script runs regardless of PowerShell's execution policy.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\crux.ps1" %*
