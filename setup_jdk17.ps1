[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$url = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
$zipPath = "jdk17.zip"
$destDir = ".jdk17"

Write-Host "Downloading Eclipse Temurin JDK 17 for local APK build..."
Invoke-WebRequest -Uri $url -OutFile $zipPath

Write-Host "Extracting JDK 17..."
Expand-Archive -Path $zipPath -DestinationPath $destDir -Force
Remove-Item $zipPath -Force

$jdkFolder = (Get-ChildItem $destDir)[0].FullName
Write-Host "JDK 17 Ready at: $jdkFolder"
