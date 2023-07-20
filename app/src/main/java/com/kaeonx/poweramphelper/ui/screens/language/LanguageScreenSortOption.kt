package com.kaeonx.poweramphelper.ui.screens.language

internal enum class LanguageScreenSortOption(internal val display: String) {
    NAME("Name"),
    EN_COUNT("EN Count"),
    CN_COUNT("CN Count"),
    JP_COUNT("JP Count"),
    KR_COUNT("KR Count"),
    O_COUNT("O Count"),
    CH_COUNT("Ch Count"),
    MINUS_COUNT("- Count"),
    SIGMA_COUNT("Σ Count");

    internal companion object {
        internal fun fromString(str: String): LanguageScreenSortOption {
            return when (str) {
                "Name" -> NAME
                "EN Count" -> EN_COUNT
                "CN Count" -> CN_COUNT
                "JP Count" -> JP_COUNT
                "KR Count" -> KR_COUNT
                "O Count" -> O_COUNT
                "Ch Count" -> CH_COUNT
                "- Count" -> MINUS_COUNT
                "Σ Count" -> SIGMA_COUNT
                else -> throw IllegalArgumentException("Unknown string received: $str")
            }
        }
    }
}