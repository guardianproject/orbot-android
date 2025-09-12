package org.torproject.android.service.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter

object DiskUtils {

    @JvmStatic
    @Throws(IOException::class)
    fun readFileFromAssets(assetFilename: String, context: Context): String {
        val reader = BufferedReader(InputStreamReader(context.assets.open(assetFilename)))
        val sb = StringBuilder()
        var mLine = reader.readLine()
        while (mLine != null) {
            sb.append(mLine).append('\n') // process line
            mLine = reader.readLine()
        }
        reader.close()
        return sb.toString()
    }

    @JvmStatic
    fun createWriteFileIntent(filename: String, mimeType: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, filename)
        }

    @JvmStatic
    fun createReadFileIntent(mimeType: String): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = mimeType
    }

    @JvmStatic
    fun readFileFromInputStream(contentResolver: ContentResolver, file: Uri): String {
        val input = contentResolver.openInputStream(file)
        val reader = BufferedReader(input!!.reader())
        return reader.use(BufferedReader::readText)
    }

    @JvmStatic
    fun recursivelyDeleteDirectory(directory: File): Boolean {
        val contents = directory.listFiles()
        contents?.forEach { recursivelyDeleteDirectory(it) }
        return directory.delete()
    }

    @JvmStatic
    fun readInputStreamAsString(stream: InputStream?): String {
        var line: String?
        val out = StringBuilder()

        try {
            val reader = BufferedReader(InputStreamReader(stream))

            while ((reader.readLine().also { line = it }) != null) {
                out.append(line)
                out.append('\n')
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return out.toString()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun flushTextToFile(file: File, text: String, append: Boolean)  {
        val ps = PrintWriter(FileWriter(file, append))
        ps.print(text)
        ps.flush()
        ps.close()
    }

}