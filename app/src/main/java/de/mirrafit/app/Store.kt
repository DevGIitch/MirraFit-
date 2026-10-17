package de.mirrafit.app

import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/** Stores everything as one JSON file in the private app folder. */
class Store(ctx: Context) {
    private val file = File(ctx.filesDir, "mirrafit.json")
    private val tmp = File(ctx.filesDir, "mirrafit.json.tmp")
    private val io = Executors.newSingleThreadExecutor()

    fun load(): AppData? {
        if (!file.exists()) return null
        return try {
            AppJson.decodeFromString(AppData.serializer(), file.readText())
        } catch (e: Exception) {
            // Do not overwrite a broken file, keep a copy instead.
            try {
                file.copyTo(File(file.parentFile, "mirrafit.json.defekt"), overwrite = true)
            } catch (e2: Exception) {
            }
            null
        }
    }

    fun saveAsync(data: AppData) {
        val text = AppJson.encodeToString(AppData.serializer(), data)
        io.execute {
            try {
                tmp.writeText(text)
                if (!tmp.renameTo(file)) file.writeText(text)
            } catch (e: Exception) {
            }
        }
    }
}
