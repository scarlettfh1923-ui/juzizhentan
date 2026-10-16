package com.scarlett.juzizhentan.data

import android.content.Context
import com.scarlett.juzizhentan.data.local.AppDatabase

class AppContainer(context: Context) {
    private val db = AppDatabase.get(context)
    val repository: Repository = Repository(db)
}
