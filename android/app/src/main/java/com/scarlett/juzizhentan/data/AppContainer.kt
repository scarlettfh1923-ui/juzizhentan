package com.scarlett.juzizhentan.data

import android.content.Context
import com.scarlett.juzizhentan.data.local.AppDatabase
import com.scarlett.juzizhentan.data.remote.NetworkModule
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    private val db = AppDatabase.get(context)
    val repository: Repository

    init {
        val initial = runBlocking {
            db.settingsDao().get(Repository.KEY_BASE_URL) ?: Repository.DEFAULT_BASE_URL
        }
        repository = Repository(db, NetworkModule.createApi(initial), initial)
    }
}
