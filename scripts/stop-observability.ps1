$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$composeFile = Join-Path $projectRoot "observability/docker-compose.yml"

docker compose -f $composeFile down
