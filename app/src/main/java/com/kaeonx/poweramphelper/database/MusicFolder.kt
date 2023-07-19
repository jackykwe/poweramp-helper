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
    [ ] NOT DONE     (doneMillis == null, resetMillis == null)
    [v] DONE         (doneMillis != null, resetMillis == null)
    [ ] DONE (RESET) (doneMillis != null, resetMillis != null)

    NOT DONE     -[User tick]->                                   DONE
    NOT DONE     <-[User untick]-                                 DONE
    DONE (RESET) -[User tick]->                                   DONE
    DONE (RESET) <-[automatic detect change millis > doneMillis]- DONE
    */
    NOT_DONE,
    DONE,
    DONE_AUTO_RESET
}

// Inspiration courtesy of https://stackoverflow.com/a/62460199
@DatabaseView(
    "SELECT * FROM musicfolder " +
    "INNER JOIN (SELECT `parentDirEncodedUri`, count(`fileName`) AS fileCount, " +
    "            sum(`langCh`) AS `langChSum`, sum(`langCN`) AS `langCNSum`, sum(`langEN`) AS `langENSum`, " +
    "            sum(`langJP`) AS `langJPSum`, sum(`langKR`) AS `langKRSum`, sum(`langO`) AS `langOSum`, " +
            "            sum(langSong) AS langSongSum FROM musicfile GROUP BY `parentDirEncodedUri`) AS subquery " +
    "ON musicfolder.encodedUri = subquery.parentDirEncodedUri " +
    "ORDER BY dirName;"
)
internal data class MusicFolderWithStatistics(
    internal val encodedUri: String,
    internal val dirName: String,
    internal val doneMillis: Long?,  // null if not done
    internal val resetMillis: Long?,  // null if never reset
    internal val fileCount: Int,
    internal val langChSum: Int,
    internal val langCNSum: Int,
    internal val langENSum: Int,
    internal val langJPSum: Int,
    internal val langKRSum: Int,
    internal val langOSum: Int,
    internal val langSongSum: Int
) {
    internal val state: MusicFolderState
        get() = when {
            this.doneMillis == null -> MusicFolderState.NOT_DONE
            this.resetMillis == null -> MusicFolderState.DONE
            else -> MusicFolderState.DONE_AUTO_RESET
        }
}