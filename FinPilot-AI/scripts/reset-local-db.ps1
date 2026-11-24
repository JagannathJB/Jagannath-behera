# Deletes ALL local FinPilot database data (the Docker volume) and recreates PostgreSQL.
# Run from anywhere:  .\scripts\reset-local-db.ps1
Set-Location (Split-Path $PSScriptRoot -Parent)
$answer = Read-Host "This permanently deletes the local FinPilot database volume. Type YES to continue"
if ($answer -ne "YES") { Write-Host "Cancelled. Nothing was changed."; exit 1 }
docker compose down -v
docker compose up -d
Write-Host "PostgreSQL recreated. Restart the backend: Flyway recreates the schema and, with SEED_ENABLED=true in .env, the demo data."
