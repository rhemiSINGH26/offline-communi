param (
    [string]$Text = "Hello! This is offline text to speech playback.",
    [string]$VoiceName = ""
)

Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.Volume = 100
$synth.Rate = 0

Write-Host "Available Windows Voices on this PC:"
$voices = $synth.GetInstalledVoices()
foreach ($v in $voices) {
    Write-Host " • $($v.VoiceInfo.Name) ($($v.VoiceInfo.Culture))"
}

if ($VoiceName -ne "") {
    $synth.SelectVoice($VoiceName)
}

Write-Host "`n[SPEAKING]: $Text"
$synth.Speak($Text)
Write-Host "[DONE] Speech completed."
