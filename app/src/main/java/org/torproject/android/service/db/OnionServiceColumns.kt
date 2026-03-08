package org.torproject.android.service.db

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.BaseColumns
import android.text.TextUtils
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import androidx.core.net.toUri
import org.torproject.android.service.OrbotConstants
import org.torproject.android.util.DiskUtils

object OnionServiceColumns : BaseColumns {
    const val NAME: String = "name"
    const val PORT: String = "port"
    const val ONION_PORT: String = "onion_port"
    const val DOMAIN: String = "domain"
    const val CREATED_BY_USER: String = "created_by_user"
    const val ENABLED: String = "enabled"
    const val PATH: String = "filepath"

    @JvmStatic
    val V3_ONION_SERVICE_PROJECTION: Array<String> =
        arrayOf(BaseColumns._ID, NAME, DOMAIN, PORT, ONION_PORT, CREATED_BY_USER, ENABLED, PATH)

    fun addV3OnionServicesToTorrc(
        torrc: StringBuffer,
        context: Context,
        v3OnionBasePath: File
    ) {
        try {
            val contentResolver = context.applicationContext.contentResolver
            val uri = getContentUri(context)
            val onionServices = contentResolver.query(
                uri, V3_ONION_SERVICE_PROJECTION,
                "$ENABLED=1", null, null
            ) ?: return
            while (onionServices.moveToNext()) {
                val idIndex = onionServices.getColumnIndex(BaseColumns._ID)
                val portIndex = onionServices.getColumnIndex(PORT)
                val onionPortIndex = onionServices.getColumnIndex(ONION_PORT)
                val pathIndex = onionServices.getColumnIndex(PATH)
                val domainIndex = onionServices.getColumnIndex(DOMAIN)
                // Ensure that we have all the indexes before trying to use them
                if (idIndex < 0 || portIndex < 0 || onionPortIndex < 0 || pathIndex < 0 || domainIndex < 0) continue

                val id = onionServices.getInt(idIndex)
                val localPort = onionServices.getInt(portIndex)
                val onionPort = onionServices.getInt(onionPortIndex)
                var path = onionServices.getString(pathIndex)
                val domain = onionServices.getString(domainIndex)
                if (path == null) {
                    path = "v3"
                    path += if (domain == null) UUID.randomUUID().toString()
                    else localPort
                    val cv = ContentValues()
                    cv.put(PATH, path)
                    contentResolver.update(
                        uri,
                        cv,
                        BaseColumns._ID + "=" + id,
                        null
                    )
                }
                val v3DirPath: String =
                    File(v3OnionBasePath.absolutePath, path).canonicalPath
                torrc.append("HiddenServiceDir ").append(v3DirPath).append("\n")
                    .append("HiddenServiceVersion 3\n")
                    .append("HiddenServicePort ").append(onionPort).append(" 127.0.0.1:")
                    .append(localPort).append("\n")
            }
            onionServices.close()
        } catch (e: Exception) {
            Log.e("OnionServiceColumns", "$e")
        }
    }

    @JvmStatic
    fun updateV3OnionNames(context: Context, v3OnionBasePath: File) {
        val uri = getContentUri(context)
        val contentResolver = context.applicationContext.contentResolver
        val onionServices = contentResolver.query(uri, null, null, null, null) ?: return
        if (onionServices.count == 0) return
        try {
            while (onionServices.moveToNext()) {
                val domainIndex = onionServices.getColumnIndex(DOMAIN)
                val pathIndex = onionServices.getColumnIndex(PATH)
                val idIndex = onionServices.getColumnIndex(BaseColumns._ID)
                if (domainIndex < 0 || pathIndex < 0 || idIndex < 0) continue
                var domain = onionServices.getString(domainIndex)
                if (domain == null || TextUtils.isEmpty(domain)) {
                    val path = onionServices.getString(pathIndex)
                    val v3OnionDirPath: String =
                        File(v3OnionBasePath.absolutePath, path).canonicalPath
                    val hostname = File(v3OnionDirPath, "hostname")
                    if (hostname.exists()) {
                        val id = onionServices.getInt(idIndex)
                        domain = DiskUtils.readInputStreamAsString(FileInputStream(hostname)).trim()
                        val fields = ContentValues()
                        fields.put(DOMAIN, domain)
                        contentResolver.update(uri, fields, BaseColumns._ID + "=" + id, null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OnionServiceColumns", "$e")
        } finally {
            onionServices.close()
        }
    }

    @JvmStatic
    fun createV3OnionDir(contextWrapper: ContextWrapper): File {
        val basePath = File(contextWrapper.filesDir.absolutePath, OrbotConstants.ONION_SERVICES_DIR)
        if (!basePath.isDirectory) basePath.mkdirs()
        return basePath
    }

    private fun getContentUri(context: Context): Uri {
        return "content://${context.applicationContext.packageName}.ui.v3onionservice/v3".toUri()
    }
}
