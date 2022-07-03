Write-Host "Install the local CA in the system trust store."
& "$PSScriptRoot\bin\mkcert-windows-amd64.exe" -install

@todo need to handle the root certificates here as well.

Write-Host "Generate certificate."
$Certs = "$PSScriptRoot\certs"

if (Get-Item -Path $Certs -ErrorAction Ignore) { }
else {
    New-Item $Certs -ItemType Directory
}

& "$PSScriptRoot\bin\mkcert-windows-amd64.exe" `
    -cert-file "$Certs\cert.pem" `
    -key-file "$Certs\privkey.pem" `
    "*.islandora.dev" `
    "islandora.dev" `
    "localhost" `
    "127.0.0.1" `
    "::1"


$Key="$PSScriptRoot\vm_rsa"
Icacls $Key /c /t /Inheritance:d

# Set Ownership to Owner:
# Key's within $env:UserProfile:
Icacls $Key /c /t /Grant ${env:UserName}:F

# Key's outside of $env:UserProfile:
TakeOwn /F $Key
Icacls $Key /c /t /Grant:r ${env:UserName}:F

# Remove All Users, except for Owner:
Icacls $Key /c /t /Remove:g Administrator "Authenticated Users" BUILTIN\Administrators BUILTIN Everyone System Users

# Verify:
Icacls $Key

Write-Host "Copy the certificate to the the Virtual Machine."
scp -r -i "$PSScriptRoot/vm_rsa" `
  -o "UserKnownHostsFile=/dev/null" `
  -o "StrictHostKeyChecking=no" `
  -P2222 `
  "$Certs" "core@islandora.dev:/opt/sandbox/build"

Write-Host "Restart Traefik."
ssh -i "$PSScriptRoot/vm_rsa" `
    -o "UserKnownHostsFile=/dev/null" `
    -o "StrictHostKeyChecking=no" `
    -p2222 `
    "core@islandora.dev" "docker restart traefik"
