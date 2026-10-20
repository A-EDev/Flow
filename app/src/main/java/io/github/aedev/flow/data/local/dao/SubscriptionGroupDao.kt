package io.github.aedev.flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionGroupDao {
    @Query("SELECT * FROM subscription_groups ORDER BY sortOrder ASC")
    fun getAllGroups(): Flow<List<SubscriptionGroupEntity>>

    @Query("SELECT * FROM subscription_groups ORDER BY sortOrder ASC")
    suspend fun getAllGroupsOnce(): List<SubscriptionGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: SubscriptionGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<SubscriptionGroupEntity>)

    @Update
    suspend fun updateGroup(group: SubscriptionGroupEntity)

    @Query("UPDATE subscription_groups SET sortOrder = :sortOrder WHERE name = :name")
    suspend fun updateSortOrder(
        name: String,
        sortOrder: Int,
    )

    /** Puts the groups named in [names] in that order. */
    @Transaction
    suspend fun setOrder(names: List<String>) {
        names.forEachIndexed { index, name -> updateSortOrder(name, index) }
    }

    @Query("DELETE FROM subscription_groups WHERE name = :name")
    suspend fun deleteGroup(name: String)

    @Query("SELECT EXISTS(SELECT 1 FROM subscription_groups WHERE name = :name)")
    suspend fun exists(name: String): Boolean
}
