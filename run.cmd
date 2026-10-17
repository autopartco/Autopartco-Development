@echo off
start "Autopartco Backend" cmd /k "cd /d backend && mvn spring-boot:run"
start "Autopartco Frontend" cmd /k "cd /d frontend && npm run dev -- --host"
exit
