@echo off
title MTS Result Receiver
cd /d "%~dp0"
MTS_Result_Receiver.exe --data-root "\\192.168.16.211\Data\Production4\MTS_Result" --host 0.0.0.0 --port 8765
pause
