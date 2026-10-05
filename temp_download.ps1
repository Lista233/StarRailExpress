[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$manifest = Invoke-WebRequest -Uri "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json" -UseBasicParsing | ConvertFrom-Json
$v = $manifest.versions | Where-Object { $_.id -eq "1.21.1" }
Write-Host "Version URL: $($v.url)"

$versionJson = Invoke-WebRequest -Uri $v.url -UseBasicParsing | ConvertFrom-Json
$clientUrl = $versionJson.downloads.client.url
$serverUrl = $versionJson.downloads.server.url
Write-Host "Client URL: $clientUrl"
Write-Host "Server URL: $serverUrl"

# Download client jar
$clientDir = "D:\McServer\残月列车改版\libraries\net\minecraft\client\1.21.1"
New-Item -ItemType Directory -Path $clientDir -Force | Out-Null
Write-Host "Downloading client jar..."
Invoke-WebRequest -Uri $clientUrl -OutFile "$clientDir\client-1.21.1.jar" -UseBasicParsing
Write-Host "Client jar downloaded to $clientDir\client-1.21.1.jar"
Write-Host "Client jar size: $((Get-Item "$clientDir\client-1.21.1.jar").Length / 1MB) MB"
