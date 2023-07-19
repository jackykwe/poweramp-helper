package com.kaeonx.poweramphelper.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

private const val TAG = "appDatabase"

@Database(
    entities = [
        KeyStringValuePair::class,
        MusicFolder::class,
    ],
    version = 1
)
internal abstract class AppDatabase : RoomDatabase() {
    internal abstract fun keyStringValuePairDao(): KeyStringValuePairDAO
    internal abstract fun musicFolderDao(): MusicFolderDAO

    // Handling singleton within an abstract class instead of object (more Kotlin-like)
    // <https://developer.android.com/codelabs/android-room-with-a-view-kotlin#7>
    // Clarified: Is this thread safe? Yes. Note the need to do double null check. Also,
    // optimisation has been done: double-checked locking.
    internal companion object {
        @Volatile
        private var instance: AppDatabase? = null

        internal fun getInstance(applicationContext: Context): AppDatabase {
            return instance ?: synchronized(this) {
                if (instance == null) {  // double null check necessary!
                    Log.w(TAG, "Requesting AppDatabase instance")
                    instance = Room.databaseBuilder(
                        applicationContext,
                        AppDatabase::class.java,
                        "ph-db"
                    )//.enableMultiInstanceInvalidation()
                        .build()
                }
                instance!!
            }
        }
    }
}