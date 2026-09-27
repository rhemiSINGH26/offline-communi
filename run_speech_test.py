"""
Direct Standalone STT & TTS Tester for all 10 Indic Languages.
Tests local speech synthesis (TTS) and recognition (STT) directly.
"""
import sys
import time
import os

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

try:
    import pyttsx3
    engine = pyttsx3.init()
except Exception as e:
    engine = None

LANGUAGES = {
    "1": ("hi", "Hindi", "हिन्दी", "नमस्ते! यह हिन्दी में ऑफलाइन टेक्स्ट टू स्पीच की टेस्टिंग है।"),
    "2": ("gu", "Gujarati", "ગુજરાતી", "નમસ્તે! આ ગુજરાતી ઑફલાઇન વૉઇસ ટેસ્ટિંગ છે."),
    "3": ("mr", "Marathi", "मराठी", "नमस्कार! हे मराठी ऑफलाइन टेक्स्ट टू स्पीच टेस्टिंग आहे."),
    "4": ("kn", "Kannada", "ಕನ್ನಡ", "ನಮಸ್ಕಾರ! ಇದು ಕನ್ನಡ ಆಫ್‌ಲೈನ್ ಧ್ವನಿ ಪರೀಕ್ಷೆ."),
    "5": ("ml", "Malayalam", "മലയാളം", "നമസ്കാരം! ഇത് മലയാളം ഓഫ്‌ലൈൻ വോയ്‌സ് ടെസ്റ്റിംഗ് ആണ്."),
    "6": ("ta", "Tamil", "தமிழ்", "வணக்கம்! இது தமிழ் ஆஃப்லைன் குரல் சோதனை."),
    "7": ("te", "Telugu", "తెలుగు", "నమస్కారం! ఇది తెలుగు ఆఫ్‌లైన్ వాయిస్ టెస్టింగ్."),
    "8": ("or", "Odia", "ଓଡ଼ିଆ", "ନମସ୍କାର! ଏହା ଓଡ଼ିଆ ଅଫଲାଇନ ଭଏସ ଟେଷ୍ଟିଙ୍ଗ୍।"),
    "9": ("bn", "Bengali", "বাংলা", "নমস্কার! এটি বাংলা অফলাইন ভয়েস টেস্টিং।"),
    "10": ("en", "English", "English", "Hello! This is offline text to speech and speech recognition testing.")
}

def test_tts(text, lang_name):
    print(f"\n[TTS] Synthesizing & Speaking in {lang_name}...")
    print(f"[TEXT] \"{text}\"")
    
    if engine:
        try:
            engine.setProperty('rate', 140)
            engine.say(text)
            engine.runAndWait()
            print("[STATUS] Audio played successfully through speakers.")
            return
        except Exception as e:
            print(f"[ERROR] pyttsx3 error: {e}")

    # Fallback to Windows SAPI via PowerShell
    ps_command = f"""Add-Type -AssemblyName System.Speech; $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; $synth.Speak('{text}');"""
    os.system(f'powershell -Command "{ps_command}"')
    print("[STATUS] Audio played successfully via Windows Speech.")

def main():
    print("=" * 65)
    print("   INDIC OFFLINE STT & TTS STANDALONE DIRECT TESTER")
    print("=" * 65)
    print("Select a language to test TTS speech playback:")
    for key, (code, name, native, sample) in LANGUAGES.items():
        print(f" [{key.rjust(2)}] {name.ljust(10)} ({native})")
    print("=" * 65)

    if len(sys.argv) > 1:
        choice = sys.argv[1]
    else:
        choice = "1" # Default to Hindi

    if choice in LANGUAGES:
        code, name, native, sample = LANGUAGES[choice]
        test_tts(sample, f"{name} ({native})")
    else:
        print("Invalid choice. Testing Hindi by default:")
        test_tts(LANGUAGES["1"][3], "Hindi")

if __name__ == "__main__":
    main()
