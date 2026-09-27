package com.indic.meshvoice.speech

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import com.indic.meshvoice.AppLogger
import com.indic.meshvoice.model.IndicLanguage
import com.indic.meshvoice.model.SupportedLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TtsTier(val title: String, val badge: String, val description: String) {
    TIER_1_SYSTEM_HD("Tier 1: System HD", "HD NATIVE (MANUAL)", "External device voice (Google / Samsung) - Manual Enable Only"),
    TIER_2_BUNDLED_NEURAL("Tier 2: Our Embedded Neural", "OUR MODEL (ACTIVE)", "Our optimized on-device self-contained offline neural engine - DEFAULT"),
    TIER_3_FAILSAFE("Tier 3: Formant Fail-Safe", "FAIL-SAFE", "Embedded guaranteed acoustic synthesizer (<10ms)")
}

data class LanguageDiagnostics(
    val language: IndicLanguage,
    val isTier1Available: Boolean,
    val isTier2Available: Boolean = true,
    val isTier3Available: Boolean = true,
    val selectedTier: TtsTier
)

class LanguageReadinessManager(private val context: Context) {

    private val tag = "LanguageReadiness"
    private val prefs: SharedPreferences = context.getSharedPreferences("indic_tts_readiness", Context.MODE_PRIVATE)

    private val _languageStatus = MutableStateFlow<Map<String, LanguageDiagnostics>>(emptyMap())
    val languageStatus: StateFlow<Map<String, LanguageDiagnostics>> = _languageStatus.asStateFlow()

    private val _isScanCompleted = MutableStateFlow(false)
    val isScanCompleted: StateFlow<Boolean> = _isScanCompleted.asStateFlow()

    init {
        loadSavedDiagnostics()
    }

    private fun loadSavedDiagnostics() {
        val initialMap = mutableMapOf<String, LanguageDiagnostics>()
        SupportedLanguages.ALL.forEach { lang ->
            val tier1Saved = prefs.getBoolean("tier1_${lang.code}", false)
            val selectedTierName = prefs.getString("selected_tier_${lang.code}", null)
            val selectedTier = if (selectedTierName != null) {
                try {
                    TtsTier.valueOf(selectedTierName)
                } catch (e: Exception) {
                    TtsTier.TIER_2_BUNDLED_NEURAL
                }
            } else {
                // Default to OUR OWN EMBEDDED MODEL by default
                TtsTier.TIER_2_BUNDLED_NEURAL
            }

            initialMap[lang.code] = LanguageDiagnostics(
                language = lang,
                isTier1Available = tier1Saved,
                isTier2Available = true,
                isTier3Available = true,
                selectedTier = selectedTier
            )
        }
        _languageStatus.value = initialMap
    }

    fun runDiagnosticScan(tts: TextToSpeech?, onComplete: (() -> Unit)? = null) {
        AppLogger.log(tag, "Running 10-Language Diagnostic Scan (Our Model is Primary Default)...")

        val updatedMap = mutableMapOf<String, LanguageDiagnostics>()
        val editor = prefs.edit()

        SupportedLanguages.ALL.forEach { lang ->
            val availability = tts?.isLanguageAvailable(lang.locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            val isTier1Ready = (availability == TextToSpeech.LANG_AVAILABLE ||
                    availability == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    availability == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)

            editor.putBoolean("tier1_${lang.code}", isTier1Ready)

            val existingSelected = prefs.getString("selected_tier_${lang.code}", null)
            val selectedTier = if (existingSelected != null) {
                try {
                    TtsTier.valueOf(existingSelected)
                } catch (e: Exception) {
                    TtsTier.TIER_2_BUNDLED_NEURAL
                }
            } else {
                // Always set OUR OWN EMBEDDED MODEL as default
                TtsTier.TIER_2_BUNDLED_NEURAL
            }
            editor.putString("selected_tier_${lang.code}", selectedTier.name)

            val diag = LanguageDiagnostics(
                language = lang,
                isTier1Available = isTier1Ready,
                isTier2Available = true,
                isTier3Available = true,
                selectedTier = selectedTier
            )
            updatedMap[lang.code] = diag
            AppLogger.log(tag, "[${lang.code.uppercase()}] ${lang.displayName} -> Active Model: ${selectedTier.title}")
        }

        editor.putBoolean("initial_scan_done", true)
        editor.apply()

        _languageStatus.value = updatedMap
        _isScanCompleted.value = true
        onComplete?.invoke()
    }

    fun setPreferredTier(langCode: String, tier: TtsTier) {
        prefs.edit().putString("selected_tier_$langCode", tier.name).apply()
        val current = _languageStatus.value.toMutableMap()
        current[langCode]?.let { existing ->
            current[langCode] = existing.copy(selectedTier = tier)
            _languageStatus.value = current
            AppLogger.log(tag, "Updated preferred tier for $langCode to ${tier.name}")
        }
    }

    fun getPreferredTier(langCode: String): TtsTier {
        return _languageStatus.value[langCode]?.selectedTier ?: TtsTier.TIER_2_BUNDLED_NEURAL
    }
}
