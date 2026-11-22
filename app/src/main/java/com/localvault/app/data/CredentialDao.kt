package com.localvault.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials ORDER BY updatedAt DESC")
    suspend fun getAll(): List<CredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CredentialEntity): Long

    @Update
    suspend fun update(entity: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: Long)
}
