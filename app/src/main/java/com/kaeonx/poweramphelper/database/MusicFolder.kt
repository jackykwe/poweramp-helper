package com.kaeonx.poweramphelper.database

import androidx.room.DatabaseView
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

internal data class MusicFolderWithLangStats(
    internal val encodedUri: String,
    internal val dirName: String,
    internal val doneMillis: Long?,  // null if not done
    internal val resetMillis: Long?,  // null if never reset
    internal val fileCount: Int,
    internal val minusCount: Int,
    internal val langChSum: Int,
    internal val langCNSum: Int,
    internal val langENSum: Int,
    internal val langJPSum: Int,
    internal val langKRSum: Int,
    internal val langOSum: Int,
    internal val pendingFirstSort: Int
) {
    internal val state: MusicFolderState
        get() = when {
            this.doneMillis == null -> MusicFolderState.NOT_DONE
            this.resetMillis == null -> MusicFolderState.DONE
            else -> MusicFolderState.DONE_AUTO_RESET
        }
}

@DatabaseView(
    "SELECT * FROM musicfolder " +
    "INNER JOIN (SELECT `parentDirEncodedUri`, count(`fileName`) AS fileCount, " +
    "            sum(`rating` = 0) AS `rating0Sum`, sum(`rating` = 1) AS `rating1Sum`, " +
    "            sum(`rating` = 2) AS `rating2Sum`, sum(`rating` = 3) AS `rating3Sum`, " +
    "            sum(`rating` = 4) AS `rating4Sum`, sum(`rating` = 5) AS `rating5Sum` " +
    "            FROM musicfile GROUP BY `parentDirEncodedUri`) AS subquery " +
    "ON musicfolder.encodedUri = subquery.parentDirEncodedUri " +
    "ORDER BY dirName;"
)
internal data class MusicFolderWithRatingStats(
    internal val encodedUri: String,
    internal val dirName: String,
    internal val doneMillis: Long?,  // null if not done
    internal val resetMillis: Long?,  // null if never reset
    internal val fileCount: Int,
    internal val rating0Sum: Int,
    internal val rating1Sum: Int,
    internal val rating2Sum: Int,
    internal val rating3Sum: Int,
    internal val rating4Sum: Int,
    internal val rating5Sum: Int
)