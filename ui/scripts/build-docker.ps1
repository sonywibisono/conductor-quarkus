Param(
    [string]$ImageName = "new-conductor-ui",
    [string]$Tag = "latest",
    [string]$Dockerfile = "Dockerfile",
    [string]$Context = ".",
    [switch]$Push
)

function Show-Usage {
    Write-Host "Usage: .\scripts\build-docker.ps1 [-ImageName name] [-Tag tag] [-Dockerfile path] [-Context path] [-Push]"
    Write-Host "Example: .\scripts\build-docker.ps1 -ImageName myrepo/new-conductor-ui -Tag v1.0.0"
}

if ($PSBoundParameters.ContainsKey('Help')) { Show-Usage; exit 0 }

# Check docker exists
try {
    docker version > $null 2>&1
} catch {
    Write-Error "Docker does not appear to be installed or running."
    exit 1
}

$fullTag = "$ImageName:$Tag"
Write-Host "Building Docker image $fullTag using $Dockerfile and context $Context"

Write-Host "Running: docker build -f $Dockerfile -t $fullTag $Context"
docker build -f $Dockerfile -t $fullTag $Context

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker build failed."
    exit $LASTEXITCODE
}

if ($Push) {
    Write-Host "Pushing $fullTag"
    docker push $fullTag
    if ($LASTEXITCODE -ne 0) { Write-Error "Docker push failed."; exit $LASTEXITCODE }
}

Write-Host "Done: $fullTag"
