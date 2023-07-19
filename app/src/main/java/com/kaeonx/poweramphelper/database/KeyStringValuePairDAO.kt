package com.kaeonx.poweramphelper.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface KeyStringValuePairDAO {
    @Query("SELECT * FROM keystringvaluepair WHERE `key` = :key;")
    fun getFlow(key: String): Flow<KeyStringValuePair?>  // null returned if key isn't found in the table

    @Query("SELECT * FROM keystringvaluepair WHERE `key` = :key;")
    suspend fun getLatest(key: String): KeyStringValuePair?  // null returned if key isn't found in the table

    @Query("SELECT * FROM keystringvaluepair WHERE `key` in(:keys);")
    fun getFlow(keys: List<String>): Flow<List<KeyStringValuePair>>

    @Query("SELECT * FROM keystringvaluepair WHERE `key` in(:keys);")
    suspend fun getLatest(keys: List<String>): List<KeyStringValuePair>

    // Upsert operation, replace if present
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(keyStringValuePair: KeyStringValuePair)

    /**
     * This is always performed as a transaction, in the case that `keyStringValuePairs` contains
     * more than 1 item.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(keyStringValuePairs: List<KeyStringValuePair>)

    // Returns number of rows successfully deleted
    @Query("DELETE FROM keystringvaluepair WHERE `key` = :key;")
    suspend fun delete(key: String): Int

    // Returns number of rows successfully deleted
    @Query("DELETE FROM keystringvaluepair WHERE `key` IN(:keys);")
    suspend fun delete(keys: List<String>): Int
}