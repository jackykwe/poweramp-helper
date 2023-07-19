package com.kaeonx.poweramphelper.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class MusicFolder(
    @PrimaryKey internal val encodedUri: String,
    internal val displayName: String,
//    internal val done: Boolean,  // with language and rating
    internal val doneMillis: Long?,  // null if not done
    internal val resetMillis: Long?  // null if never reset
) {
    internal val state: MusicFolderState
        get() = when {
            this.doneMillis == null -> MusicFolderState.NOT_DONE
            this.resetMillis == null -> MusicFolderState.DONE
            else -> MusicFolderState.DONE_AUTO_RESET
        }
}

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

