package org.octopusden.octopus.vcsfacade.service

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.ByteArrayOutputStream

/**
 * Builds zips with exact entry names and Unix modes, which `java.util.zip` cannot write.
 */
class TestZips {
    private val out = ByteArrayOutputStream()
    private val zip = ZipArchiveOutputStream(out)

    fun file(
        name: String,
        content: String = "content of $name",
        mode: Int = REGULAR,
    ) = apply {
        val entry = ZipArchiveEntry(name)
        entry.unixMode = mode
        zip.putArchiveEntry(entry)
        zip.write(content.toByteArray())
        zip.closeArchiveEntry()
    }

    fun executable(name: String) = file(name, "#!/bin/sh\n", EXECUTABLE)

    fun symlink(
        name: String,
        target: String,
    ) = file(name, target, SYMLINK)

    /** A symlink whose target is given as raw bytes, which need not be valid UTF-8. */
    fun symlinkBytes(
        name: String,
        target: ByteArray,
    ) = apply {
        val entry = ZipArchiveEntry(name)
        entry.unixMode = SYMLINK
        zip.putArchiveEntry(entry)
        zip.write(target)
        zip.closeArchiveEntry()
    }

    /** The name is used as given when it ends with a slash, so `a/./` and `./` can be written. */
    fun directory(
        name: String,
        content: String = "",
    ) = apply {
        val entry = ZipArchiveEntry(if (name.endsWith("/")) name else "$name/")
        entry.unixMode = DIRECTORY
        zip.putArchiveEntry(entry)
        zip.write(content.toByteArray())
        zip.closeArchiveEntry()
    }

    fun bytes(): ByteArray {
        zip.finish()
        zip.close()
        return out.toByteArray()
    }

    companion object {
        const val REGULAR = 0x81A4 // 0100644
        const val EXECUTABLE = 0x81ED // 0100755
        const val SYMLINK = 0xA1FF // 0120777
        const val DIRECTORY = 0x41ED // 040755

        fun zip(block: TestZips.() -> Unit) = TestZips().apply(block).bytes()
    }
}
