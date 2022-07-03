Write-Host "Install the local CA in the system trust store."
& "$PSScriptRoot\bin\mkcert-windows-amd64.exe" -install

$Certs = "$PSScriptRoot\certs"

if (Get-Item -Path $Certs -ErrorAction Ignore) { }
else {
    New-Item $Certs -ItemType Directory
}

$caroot = & "$PSScriptRoot\bin\mkcert-windows-amd64.exe" -CAROOT

Copy-Item "$caroot\rootCA.pem" -Destination $Certs

Write-Host "Generate certificate."

& "$PSScriptRoot\bin\mkcert-windows-amd64.exe" `
    -cert-file "$Certs\cert.pem" `
    -key-file "$Certs\privkey.pem" `
    "*.islandora.dev" `
    "islandora.dev" `
    "localhost" `
    "127.0.0.1" `
    "::1"

Write-Host "Creating docker-compose.yml"

Set-Location -Path  "$PSScriptRoot\assets"
$env:CERT_PUBLIC_KEY = (Get-Content "$Certs\cert.pem" -Raw)
$env:CERT_PRIVATE_KEY = (Get-Content "$Certs\privkey.pem" -Raw)
$env:CERT_AUTHORITY = (Get-Content "$Certs\rootCA.pem" -Raw)
$env:TAG = (Get-Content "$PSScriptRoot\assets\TAG" -Raw)

# String replacement is required for the bug https://github.com/docker/compose/issues/9306
$config = & docker compose -f docker-compose.desktop.yml config
$config.replace('"80"', '80').replace('"443"', '443') -replace '^name: sandbox$', 'version: "3.8"' -replace '^\s+bind:\s*$', '' -replace '^\s+create_host_path:.*$', '' | Set-Content "$PSScriptRoot\docker-compose.yml"

Write-Host "Finished"
