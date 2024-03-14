package com.flowfoundation.wallet.database

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "Bookmark", indices = [Index("id", "url")])
@Parcelize
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    var url: String,
    var title: String,
    var isFavourite: Boolean = false,
    var createTime: Long,
    var updateTime: Long = 0,
) : Parcelable {

    init {
        if (updateTime == 0L) {
            updateTime = createTime
        }
    }
}
