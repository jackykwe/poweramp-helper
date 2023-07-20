package com.kaeonx.poweramphelper.database

import androidx.compose.ui.text.AnnotatedString
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class MusicFolder(
    @PrimaryKey internal val encodedUri: String,
    internal val dirName: String,
    internal val doneMillis: Long?,  // null if not done
    internal val resetMillis: Long?  // null if never reset
)

internal enum class MusicFolderState {
    /*
    [ ] NOT DONE     (doneMillis == null, resetMillis == null)  #2
    [v] DONE         (doneMillis != null, resetMillis == null)  #3
    [ ] DONE (RESET) (doneMillis != null, resetMillis != null)  #1

    NOT DONE     -[User tick]->                                   DONE
    NOT DONE     <-[User untick]-                                 DONE
    DONE (RESET) -[User tick]->                                   DONE
    DONE (RESET) <-[automatic detect change millis > doneMillis]- DONE
    */
    NOT_DONE,
    DONE,
    DONE_AUTO_RESET
}

internal data class MusicFolderWithLangStatsDB(
    internal val encodedUri: String,
    internal val dirName: String,
    internal val doneMillis: Long?,  // null if not done
    internal val resetMillis: Long?,  // null if never reset
    internal val fileCount: Int,
    internal val minusSum: Int,
    internal val langChSum: Int,
    internal val langCNSum: Int,
    internal val langENSum: Int,
    internal val langJPSum: Int,
    internal val langKRSum: Int,
    internal val langOSum: Int,
    internal val pendingFirstSort: Int
)

internal data class MusicFolderWithLangStatsUI(
    internal val encodedUri: String,
    internal val dirName: String,
//    internal val doneMillis: Long?,  // null if not done
//    internal val resetMillis: Long?,  // null if never reset
//    internal val fileCount: Int,
//    internal val minusSum: Int,
//    internal val langChSum: Int,
//    internal val langCNSum: Int,
//    internal val langENSum: Int,
//    internal val langJPSum: Int,
//    internal val langKRSum: Int,
//    internal val langOSum: Int,
//    internal val pendingFirstSort: Int,
    internal val state: MusicFolderState,  // added for UI
    internal val countReport: AnnotatedString,  // added for UI
    internal val timestampsReport: AnnotatedString  // added for UI
)

internal data class MusicFolderWithRatingStatsDB(
    internal val encodedUri: String,
    internal val dirName: String,
    internal val fileCount: Int,
    internal val rating0SSum: Int,
    internal val rating1SSum: Int,
    internal val rating2SSum: Int,
    internal val rating3SSum: Int,
    internal val rating4SSum: Int,
    internal val rating5SSum: Int
)

internal data class MusicFolderWithRatingStatsUI(
    internal val encodedUri: String,
    internal val dirName: String,
//    internal val fileCount: Int,
//    internal val rating0SSum: Int,
//    internal val rating1SSum: Int,
//    internal val rating2SSum: Int,
//    internal val rating3SSum: Int,
//    internal val rating4SSum: Int,
//    internal val rating5SSum: Int
    internal val countReport: AnnotatedString,  // added for UI
    internal val progressReport: AnnotatedString  // added for UI
)