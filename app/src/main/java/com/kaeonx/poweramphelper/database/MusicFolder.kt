package com.kaeonx.poweramphelper.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class MusicFolder(
    @PrimaryKey val encodedUri: String,
    val displayName: String,
//    val done: Boolean,  // with language and rating
    val doneMillis: Long?,  // null if not done
    val resetMillis: Long?  // null if never reset
)

/*
[ ] NOT DONE     (doneMillis == null, resetMillis == null)
[v] DONE         (doneMillis != null, resetMillis == null)
[ ] DONE (RESET) (doneMillis != null, resetMillis != null)

NOT DONE     -[User tick]->                                   DONE
NOT DONE     <-[User untick]-                                 DONE
DONE (RESET) -[User tick]->                                   DONE
DONE (RESET) <-[automatic detect change millis > doneMillis]- DONE
 */