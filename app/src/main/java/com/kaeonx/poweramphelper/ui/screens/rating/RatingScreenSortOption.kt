package com.kaeonx.poweramphelper.ui.screens.rating

internal enum class RatingScreenSortOption(internal val display: String) {
    NAME("Name"),
    RATING_0S_SUM("☆☆☆☆☆"),
    RATING_1S_SUM("★☆☆☆☆"),
    RATING_2S_SUM("★★☆☆☆"),
    RATING_3S_SUM("★★★☆☆"),
    RATING_4S_SUM("★★★★☆"),
    RATING_5S_SUM("★★★★★");

    internal companion object {
        internal fun fromString(str: String): RatingScreenSortOption {
            return when (str) {
                "Name" -> NAME
                "☆☆☆☆☆" -> RATING_0S_SUM
                "★☆☆☆☆" -> RATING_1S_SUM
                "★★☆☆☆" -> RATING_2S_SUM
                "★★★☆☆" -> RATING_3S_SUM
                "★★★★☆" -> RATING_4S_SUM
                "★★★★★" -> RATING_5S_SUM
                else -> throw IllegalArgumentException("Unknown string received: $str")
            }
        }
    }
}