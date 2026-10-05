# Update Core.jar manifest - use temp dir to avoid Chinese path encoding issues
$tempDir = "D:\_temp_core_update"
$coreJarPath = "D:\McServer\残月列车改版\Core.jar"
$tempCoreJar = "$tempDir\Core.jar"

# Clean up
if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force }
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

# Copy Core.jar to temp location
Copy-Item $coreJarPath $tempCoreJar

# Extract
Push-Location $tempDir
& jar xf $tempCoreJar
Write-Host "Extracted files:"
Get-ChildItem -Recurse | Select-Object FullName

# Create new manifest
$manifestContent = "Manifest-Version: 1.0`r`nMain-Class: net.fabricmc.loader.impl.launch.server.FabricServerLauncher`r`nClass-Path: libraries/org/ow2/asm/asm/9.9/asm-9.9.jar libraries/org/ow2/ asm/asm-analysis/9.9/asm-analysis-9.9.jar libraries/org/ow2/asm/asm-com mons/9.9/asm-commons-9.9.jar libraries/org/ow2/asm/asm-tree/9.9/asm-tre e-9.9.jar libraries/org/ow2/asm/asm-util/9.9/asm-util-9.9.jar libraries /net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin .0.8.7.jar libraries/com/llamalad7/mixinextras/0.5.5/mixinextras-0.5.5 .jar libraries/net/minecraft/client/1.21.1/client-1.21.1.jar libraries /net/fabricmc/intermediary/1.21.1/intermediary-1.21.1.jar libraries/ne t/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar`r`n`r`n"

[System.IO.File]::WriteAllText("$tempDir\META-INF\MANIFEST.MF", $manifestContent, [System.Text.Encoding]::ASCII)

Write-Host "`nNew manifest content:"
Get-Content "META-INF\MANIFEST.MF"

# Create new jar
& jar cfm $tempCoreJar "META-INF\MANIFEST.MF" "fabric-server-launch.properties"

# Copy back
Copy-Item $tempCoreJar $coreJarPath -Force
Pop-Location

# Verify
Write-Host "`n=== Verification ==="
Push-Location $tempDir
& jar xf $tempCoreJar "META-INF/MANIFEST.MF"
Get-Content "META-INF\MANIFEST.MF"
Pop-Location

# Clean up
Remove-Item $tempDir -Recurse -Force
Write-Host "`nDone!"
