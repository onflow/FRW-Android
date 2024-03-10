package com.flowfoundation.wallet.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flowfoundation.wallet.utils.Env

@Database(entities = [WebviewRecord::class, Bookmark::class], version = 1, exportSchema = false)
abstract class AppDataBase : RoomDatabase() {
    abstract fun webviewRecordDao(): WebviewRecordDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private val dataBase by lazy {
            Room.databaseBuilder(
                Env.getApp(),
                AppDataBase::class.java, "database"
            ).build()
        }

        fun database(): AppDataBase = dataBase
    }
}
