# Runs FestPass over HTTPS on port 8443 so a phone on the same Wi-Fi can use the camera
# (browsers only allow the camera on localhost or HTTPS).
#
#   1. docker compose up -d postgres mailpit
#   2. powershell -ExecutionPolicy Bypass -File scripts/run-https.ps1
#   3. On the phone, open the https://<laptop-ip>:8443 address printed below and accept the
#      certificate warning (it is self-signed, made on this laptop).
#
# Add -Profiles "demo,https" to run with the demo data instead of the dev data.
param([string]$Profiles = "dev,https")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$keystore = Join-Path $backend "dev-https.p12"

# The laptop's Wi-Fi / LAN address, so the certificate is valid for it.
$ip = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.PrefixOrigin -ne "WellKnown" } |
        Sort-Object InterfaceMetric | Select-Object -First 1).IPAddress
if (-not $ip) { $ip = "127.0.0.1" }

if (-not (Test-Path $keystore)) {
    # keytool ships with the JDK; find it next to the java that is on the PATH.
    # Java prints its settings on stderr; cmd merges the streams so PowerShell doesn't treat that as a failure.
    $javaHome = (cmd /c "java -XshowSettings:properties -version 2>&1" | Select-String "java.home" | Select-Object -First 1).ToString().Split("=")[1].Trim()
    $keytool = Join-Path $javaHome "bin\keytool.exe"
    Write-Host "Creating a self-signed certificate for localhost and $ip ..."
    $ErrorActionPreference = "Continue" # keytool reports progress on stderr, which is not a failure
    & $keytool -genkeypair -alias festpass -keyalg RSA -keysize 2048 -validity 365 `
        -storetype PKCS12 -keystore $keystore -storepass festpass-dev `
        -dname "CN=FestPass Dev" -ext "SAN=dns:localhost,ip:127.0.0.1,ip:$ip" 2>$null
    $ErrorActionPreference = "Stop"
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $keystore)) { throw "keytool failed" }
}

# Build the React app into the Spring Boot static folder, so one server serves both.
Push-Location (Join-Path $root "frontend")
npm install
npm run build
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "frontend build failed" }
Pop-Location
$static = Join-Path $backend "src\main\resources\static"
if (Test-Path $static) { Remove-Item $static -Recurse -Force }
Copy-Item (Join-Path $root "frontend\dist") $static -Recurse

Write-Host ""
Write-Host "On this laptop:  https://localhost:8443"
Write-Host "On your phone:   https://${ip}:8443   (same Wi-Fi; accept the certificate warning)"
Write-Host ""

Push-Location $backend
& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=$Profiles"
Pop-Location
