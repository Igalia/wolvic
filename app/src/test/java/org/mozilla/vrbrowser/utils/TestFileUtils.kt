package org.mozilla.vrbrowser.utils

import java.io.ByteArrayOutputStream
import java.io.IOException

object TestFileUtils {

    fun readTextFile(classLoader: ClassLoader, file: String): String? {
        val byteArrayOutputStream = ByteArrayOutputStream()
        var i: Int
        try {
            val inputStream = classLoader.getResourceAsStream(file)!!
            i = inputStream.read()
            while (i != -1) {
                byteArrayOutputStream.write(i)
                i = inputStream.read()
            }
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return byteArrayOutputStream.toString()
    }

}