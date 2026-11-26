package org.octopusden.octopus.vcsfacade.service

import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.InputStream

/**
 * One file or symlink of a checked zip.
 *
 * @property path the normalised path, `/` separated, relative to the repository root
 * @property size the uncompressed size; for a symlink the length of its target
 */
data class ZipContentEntry(
    val path: String,
    val type: Type,
    val size: Long,
) {
    enum class Type {
        REGULAR,
        EXECUTABLE,
        SYMLINK,
    }
}

/**
 * A zip that passed the checks of [ContentValidator]: every entry is safe to turn into a repository file.
 */
class ValidatedZip internal constructor(
    private val bytes: ByteArray,
    val entries: List<ZipContentEntry>,
    private val namesInZip: Map<String, String>,
) {
    /**
     * Reads every file and symlink in turn; for a symlink the stream holds its target.
     */
    fun forEachFile(action: (ZipContentEntry, InputStream) -> Unit) {
        ZipFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(bytes)).get().use { zip ->
            entries.forEach { entry ->
                val zipEntry = checkNotNull(zip.getEntry(namesInZip.getValue(entry.path))) { "Entry '${entry.path}' vanished" }
                zip.getInputStream(zipEntry).use { action(entry, it) }
            }
        }
    }
}
