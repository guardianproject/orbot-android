package org.torproject.android.service.circumvention

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

@Serializable
data class BuiltInBridges(
    @SerialName("meek-azure")
    var meekAzure: List<Bridge>? = null,
    var obfs4: List<Bridge>? = null,
    var snowflake: List<Bridge>? = null,
    var webtunnel: List<Bridge>? = null,
) {

    val empty: Boolean
        get() {
            return meekAzure.isNullOrEmpty() && obfs4.isNullOrEmpty() &&
                    snowflake.isNullOrEmpty() && webtunnel.isNullOrEmpty()
        }

    fun store(context: Context) {
        getUpdateFile(context).writeText(Json.Default.encodeToString(this))
    }


    companion object {

        const val FILE_NAME = "builtin-bridges.json"

        const val UPDATE_FILE_NAME = "updated-bridges.json"

        private var instance: BuiltInBridges? = null


        fun getInstance(context: Context? = null): BuiltInBridges? {
            if (instance == null && context != null) {
                try {
                    instance = read(getUpdateFile(context).readText())
                }
                catch (_: Throwable) {}
            }

            if (instance == null && context != null) {
                try {
                    instance =
                        read(context.assets.open(FILE_NAME).bufferedReader().use { it.readText() })
                }
                catch (_: Throwable) {}
            }

            return instance
        }

        /**
         * We consider stored updated-bridges.json file to be outdated after 2 days.
         */
        fun isOutdated(context: Context): Boolean {
            val lastModified = try {
                getUpdateFile(context).lastModified()
            }
            catch (_: Throwable) {
                0
            }

            return Calendar.getInstance().timeInMillis - lastModified > 2 * 24 * 60 * 60 * 1000
        }

        fun invalidate() {
            instance = null
        }

        fun getUpdateFile(context: Context): File {
            return File(context.cacheDir, UPDATE_FILE_NAME)
        }

        @Suppress("JSON_FORMAT_REDUNDANT")
        private fun read(json: String): BuiltInBridges? {
            return try {
                Json {
                    ignoreUnknownKeys = true
                }.decodeFromString(json)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
