package com.indic.meshvoice.model

import java.util.Locale

data class IndicLanguage(
    val code: String,              // e.g. "hi", "gu", "mr"
    val displayName: String,       // e.g. "Hindi", "Gujarati"
    val nativeName: String,        // e.g. "हिन्दी", "ગુજરાતી"
    val locale: Locale,
    val sampleText: String
)

object SupportedLanguages {
    val ALL = listOf(
        IndicLanguage("hi", "Hindi", "हिन्दी", Locale("hi", "IN"), "नमस्ते, आप कैसे हैं?"),
        IndicLanguage("gu", "Gujarati", "ગુજરાતી", Locale("gu", "IN"), "નમસ્તે, તમે કેમ છો?"),
        IndicLanguage("mr", "Marathi", "मराठी", Locale("mr", "IN"), "नमस्कार, तुम्ही कसे आहात?"),
        IndicLanguage("kn", "Kannada", "ಕನ್ನಡ", Locale("kn", "IN"), "ನಮಸ್ಕಾರ, ನೀವು ಹೇಗಿದ್ದೀರಿ?"),
        IndicLanguage("ml", "Malayalam", "മലയാളം", Locale("ml", "IN"), "നമസ്കാരം, സുഖമാണോ?"),
        IndicLanguage("ta", "Tamil", "தமிழ்", Locale("ta", "IN"), "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?"),
        IndicLanguage("te", "Telugu", "తెలుగు", Locale("te", "IN"), "నమస్కారం, మీరు ఎలా ఉన్నారు?"),
        IndicLanguage("or", "Odia", "ଓଡ଼ିଆ", Locale("or", "IN"), "ନମସ୍କାର, ଆପଣ କେମିତି ଅଛନ୍ତି?"),
        IndicLanguage("bn", "Bengali", "বাংলা", Locale("bn", "IN"), "নমস্কার, আপনি কেমন আছেন?"),
        IndicLanguage("en", "English", "English", Locale.US, "Hello, can you hear me through the mesh?")
    )

    fun getByCode(code: String): IndicLanguage {
        return ALL.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ALL[0]
    }
}
