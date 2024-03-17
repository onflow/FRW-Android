package com.flowfoundation.wallet.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BookmarkDao {
    @Insert
    fun save(bookmark: Bookmark): Long

    @Query("select * from Bookmark where url=:url limit 1")
    fun findByUrl(url: String): Bookmark?

    @Query("select count(*) from Bookmark")
    fun count(): Int

    @Query("select * from Bookmark where 1=1 order by updateTime desc limit :limit")
    fun findAll(limit: Int = 1000): List<Bookmark>

    @Query("select * from Bookmark where 1=1 order by updateTime desc limit :limit")
    fun findAllLive(limit: Int = 1000): LiveData<List<Bookmark>>

    @Query("delete from Bookmark where url=:url")
    fun deleteByUrl(url: String)

    @Delete
    fun delete(bookmark: Bookmark)

    @Update
    fun update(bookmark: Bookmark)
}