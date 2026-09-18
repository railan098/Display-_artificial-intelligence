$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectDir

mvn -q clean package
# A versão vem do jar recém-construído: fixá-la aqui quebrava o empacotamento
# a cada release, e ler o pom exigiria um plugin extra do Maven.
$JarPath = (Get-ChildItem target/agent-meter-*.jar | Select-Object -First 1)
$Jar = $JarPath.Name
$Version = $Jar -replace '^agent-meter-', '' -replace '\.jar$', ''
Remove-Item -Recurse -Force target/package-input, target/dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force target/package-input, target/dist | Out-Null
Copy-Item $JarPath.FullName target/package-input/
Copy-Item target/libs/*.jar target/package-input/

$Arguments = @(
  "--name", "Agent Meter", "--app-version", $Version, "--vendor", "Agent Meter",
  "--description", "Monitor local de recursos do Codex e Claude Code",
  "--input", "target/package-input", "--main-jar", $Jar,
  "--main-class", "dev.agentmeter.Launcher", "--dest", "target/dist",
  "--java-options", "-Dfile.encoding=UTF-8"
)
if (Test-Path assets/icon.ico) { $Arguments += @("--icon", "assets/icon.ico") }
if ($args.Count -gt 0 -and $args[0] -eq "msi") {
  & jpackage @Arguments --type msi --win-shortcut --win-menu --win-menu-group "Agent Meter"
} else {
  & jpackage @Arguments --type app-image
}
Write-Host "Pacote criado em $ProjectDir\target\dist"
