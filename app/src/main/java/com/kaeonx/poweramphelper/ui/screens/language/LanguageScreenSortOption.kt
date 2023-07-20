package com.kaeonx.poweramphelper.ui.screens.language

internal enum class LanguageScreenSortOption(internal val display: String) {
    NAME("Name"),
    LANG_EN_SUM("EN Count"),
    LANG_CN_SUM("CN Count"),
    LANG_JP_SUM("JP Count"),
    LANG_KR_SUM("KR Count"),
    LANG_O_SUM("O Count"),
    LANG_CH_SUM("Ch Count"),
    MUSIC_SUM("- Count"),
    FILE_COUNT("Σ Count");

    internal companion object {
        internal fun fromString(str: String): LanguageScreenSortOption {
            return when (str) {
                "Name" -> NAME
                "EN Count" -> LANG_EN_SUM
                "CN Count" -> LANG_CN_SUM
                "JP Count" -> LANG_JP_SUM
                "KR Count" -> LANG_KR_SUM
                "O Count" -> LANG_O_SUM
                "Ch Count" -> LANG_CH_SUM
                "- Count" -> MUSIC_SUM
                "Σ Count" -> FILE_COUNT
                else -> throw IllegalArgumentException("Unknown string received: $str")
            }
        }
    }
}