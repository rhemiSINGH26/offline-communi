"""
Indic STT & TTS Local Validation Script for 10 Indic Languages
"""
import sys

LANGUAGES = {
    "hi": {"name": "Hindi", "native": "हिन्दी", "sample": "नमस्ते, आप कैसे हैं?"},
    "gu": {"name": "Gujarati", "native": "ગુજરાતી", "sample": "નમસ્તે, તમે કેમ છો?"},
    "mr": {"name": "Marathi", "native": "मराठी", "sample": "नमस्कार, तुम्ही कसे आहात?"},
    "kn": {"name": "Kannada", "native": "ಕನ್ನಡ", "sample": "ನಮಸ್ಕಾರ, ನೀವು ಹೇಗಿದ್ದೀರಿ?"},
    "ml": {"name": "Malayalam", "native": "മലയാളം", "sample": "നമസ്കാരം, സുഖമാണോ?"},
    "ta": {"name": "Tamil", "native": "தமிழ்", "sample": "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?"},
    "te": {"name": "Telugu", "native": "తెలుగు", "sample": "నమస్కారం, మీరు ఎలా ఉన్నారు?"},
    "or": {"name": "Odia", "native": "ଓଡ଼ିଆ", "sample": "ନମସ୍କାର, ଆପଣ କେମିତି ଅଛନ୍ତି?"},
    "bn": {"name": "Bengali", "native": "বাংলা", "sample": "নমস্কার, আপনি কেমন আছেন?"},
    "en": {"name": "English", "native": "English", "sample": "Hello, this is a local offline test."}
}

def print_language_matrix():
    print("=" * 60)
    print("      INDIC STT & TTS 10-LANGUAGE LOCAL TEST MATRIX")
    print("=" * 60)
    for code, info in LANGUAGES.items():
        print(f"[{code.upper()}] {info['name']} ({info['native']}): {info['sample']}")
    print("=" * 60)

if __name__ == "__main__":
    print_language_matrix()
    print("\n[INFO] Open 'test_indic_speech_studio.html' in your browser to test live mic recording (STT) and voice synthesis (TTS) in real time!")
