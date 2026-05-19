package com.schadenfreude.text2speech.domain
import com.schadenfreude.text2speech.BuildKonfig

enum class Language(
    val displayName: String,
    val languageCode: String,
    val recognizerName: String,
    val phraseSetName: String
) {
    BULGARIAN(
        displayName = "🇧🇬Bulgarian",
        languageCode = "bg-BG",
        recognizerName = "bg-recognizer",
        phraseSetName = "bulgarian-safety-terms-ps"
    ),
    ENGLISH(
        displayName = "🇺🇸English",
        languageCode = "en-US",
        recognizerName = "en-recognizer",
        phraseSetName = "english-safety-terms-ps"
    ),
    GERMAN(
        displayName = "🇩🇪German",
        languageCode = "de-DE",
        recognizerName = "de-recognizer",
        phraseSetName = "german-safety-terms-ps"
    ),
    ALBANIAN(
        displayName = "🇦🇱Albanian",
        languageCode = "sq-AL",
        recognizerName = "sq-recognizer",
        phraseSetName = "albanian-safety-terms-ps"
    );

    val recognizerId: String get() = "projects/${BuildKonfig.PROJECT_ID}/locations/${BuildKonfig.GCP_REGION}/recognizers/$recognizerName"
    val phraseSetId: String get() = "projects/${BuildKonfig.PROJECT_ID}/locations/${BuildKonfig.GCP_REGION}/phraseSets/$phraseSetName"
}

data class STTConfig(
    val language: Language,
    val isLiveStream: Boolean
)

sealed class TranscriptionResult {
    data class Interim(val text: String) : TranscriptionResult()
    data class Final(val text: String) : TranscriptionResult()
    data class Error(val message: String) : TranscriptionResult()
}
