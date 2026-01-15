package com.liftley.vodrop.stt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual suspend fun writeModelFile(
    path: String,
    writeBlock: suspend ((ByteArray, Int, Int) -> Unit) -> Unit
) {
    withContext(Dispatchers.IO) {
        val file = File(path)
        file.parentFile?.mkdirs()

        FileOutputStream(file).use { output ->
            writeBlock { buffer, offset, length ->
                output.write(buffer, offset, length)
            }
        }
    }
}

actual fun getModelDirectory(): String {
    // This will be overridden in the actual engine which has Context access
    return ""
}