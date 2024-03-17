package com.flowfoundation.wallet.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WebviewRecordDao {
    @Insert
    fun save(record: WebviewRecord): Long

    @Query("select * from WebviewRecord where url=:url limit 1")
    fun findByUrl(url: String): WebviewRecord?

    @Query("select count(*) from WebviewRecord")
    fun count(): Int

    @Query("select * from WebviewRecord where 1=1 order by updateTime desc limit :limit")
    fun findAll(limit: Int = 1000): List<WebviewRecord>

    @Query("select * from WebviewRecord where 1=1 order by updateTime desc limit :limit")
    fun findAllLive(limit: Int = 1000): LiveData<List<WebviewRecord>>

    @Query("delete from WebviewRecord where url=:url")
    fun deleteByUrl(url: String)

    @Delete
    fun delete(record: WebviewRecord)

    @Update
    fun update(record: WebviewRecord)
}