package org.octopusden.octopus.vcsfacade.service

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.exception.ContentTooLargeException
import org.octopusden.octopus.vcsfacade.service.ZipContentEntry.Type
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Checks an uploaded zip before anything is created (design D6), reading it once, in memory:
 *
 * - it opens as a zip;
 * - no entry has a `.git` path segment, is absolute, or leaves the root once `..` and `\` are resolved;
 * - no symlink points outside the root;
 * - nothing is written below a file or a symlink, and no path is used twice;
 * - the zip does not inflate past [UNCOMPRESSED_FACTOR] times [maxContentBytes], or hold more than [maxEntries] entries.
 *
 * The first offending entry is named.
 */
@Suppress("ThrowsCount") // a validator rejects for many reasons, each with its own message
class ContentValidator(
    private val maxContentBytes: Long,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    /**
     * @throws ContentTooLargeException when [content] is over the size limit
     * @throws InvalidContentException naming the first offending entry
     */
    fun validate(content: ByteArray): ValidatedZip {
        if (content.size > maxContentBytes) {
            throw ContentTooLargeException("The content is ${content.size} bytes, the limit is $maxContentBytes")
        }
        val zip = try {
            ZipFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(content)).get()
        } catch (expected: IOException) {
            throw InvalidContentException("The content is not a zip file that can be opened")
        }
        return zip.use { collect(content, it) }
    }

    private fun collect(
        content: ByteArray,
        zip: ZipFile,
    ): ValidatedZip {
        val state = Collected()
        var count = 0
        for (entry in zip.entries) {
            if (++count > maxEntries) throw InvalidContentException("The zip has more than $maxEntries entries")
            try {
                check(zip, entry, state)
            } catch (expected: IOException) {
                throw InvalidContentException("Entry '${entry.name.escaped()}' cannot be read")
            }
        }
        return ValidatedZip(content, state.entries, state.namesInZip)
    }

    /** What [read] found: the number of bytes the entry holds, and its content up to the `keep` limit. */
    private class EntryRead(
        val size: Long,
        val kept: ByteArray,
    )

    private class Collected {
        val entries = mutableListOf<ZipContentEntry>()
        val namesInZip = HashMap<String, String>()
        val files = HashSet<String>()
        val directories = HashSet<String>()
        var declaredTotal = 0L
        var actualTotal = 0L
    }

    private fun check(
        zip: ZipFile,
        entry: ZipArchiveEntry,
        state: Collected,
    ) {
        val name = entry.name
        val path = resolveOrReject(name, name) { it }
        val kind = entry.unixMode and FILE_TYPE_MASK
        if (kind == DIRECTORY || entry.isDirectory) return
        if (kind != 0 && kind != REGULAR_FILE && kind != SYMLINK) throw reject(name, "is neither a file, a directory nor a symlink")
        if (path.isEmpty()) throw reject(name, "has no file name")
        state.declaredTotal += maxOf(entry.size, 0)
        if (state.declaredTotal > maxContentBytes * UNCOMPRESSED_FACTOR) throw tooBig(name)
        val type = entryType(entry.unixMode)
        val read = read(zip, entry, state, if (type == Type.SYMLINK) MAX_SYMLINK_TARGET else 0)
        if (entry.size >= 0 && entry.size != read.size) {
            throw reject(name, "holds ${read.size} bytes, not the ${entry.size} its header says")
        }
        if (type == Type.SYMLINK) checkSymlink(name, path, read)
        claim(name, path, state)
        state.entries += ZipContentEntry(path.joinToString("/"), type, read.size)
        state.namesInZip[path.joinToString("/")] = name
    }

    /**
     * Reads the entry through to its end, counting what it really holds.
     *
     * @param keep the content of an entry is kept up to this many bytes
     */
    private fun read(
        zip: ZipFile,
        entry: ZipArchiveEntry,
        state: Collected,
        keep: Long,
    ): EntryRead {
        val kept = ByteArrayOutputStream()
        var total = 0L
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                state.actualTotal += read
                if (state.actualTotal > maxContentBytes * UNCOMPRESSED_FACTOR) throw tooBig(entry.name)
                if (total <= keep) kept.write(buffer, 0, read)
            }
        }
        return EntryRead(total, kept.toByteArray())
    }

    private fun checkSymlink(
        name: String,
        path: List<String>,
        read: EntryRead,
    ) {
        if (read.size > MAX_SYMLINK_TARGET) throw reject(name, "is a symlink with a target of more than $MAX_SYMLINK_TARGET bytes")
        val target = String(read.kept, Charsets.UTF_8)
        if (target.isBlank()) throw reject(name, "is a symlink with no target")
        resolveOrReject(target, name, path.dropLast(1)) { "is a symlink whose target '${target.escaped()}' $it" }
    }

    private fun resolveOrReject(
        raw: String,
        entry: String,
        base: List<String> = emptyList(),
        describe: (String) -> String,
    ) = try {
        resolve(raw, base)
    } catch (expected: PathProblem) {
        throw reject(entry, describe(expected.problem))
    }

    /**
     * The segments of [raw], resolved against [base].
     *
     * @throws PathProblem when the path is absolute, has a `.git` segment, or cannot stay inside the root
     */
    private fun resolve(
        raw: String,
        base: List<String>,
    ): List<String> {
        val separated = raw.replace('\\', '/')
        if (separated.startsWith("/") || DRIVE.containsMatchIn(separated)) throw PathProblem("is an absolute path")
        val segments = separated.split('/')
        if (segments.any { it.equals(".git", ignoreCase = true) }) throw PathProblem("has a `.git` path segment")
        val resolved = base.toMutableList()
        for (segment in segments) {
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    if (resolved.isEmpty()) throw PathProblem("escapes the repository root")
                    resolved.removeAt(resolved.size - 1)
                }
                else -> resolved += segment
            }
        }
        return resolved
    }

    private class PathProblem(
        val problem: String,
    ) : Exception(problem)

    private fun claim(
        name: String,
        path: List<String>,
        state: Collected,
    ) {
        val joined = path.joinToString("/")
        if (joined in state.files) throw reject(name, "repeats the path '${joined.escaped()}'")
        if (joined in state.directories) throw reject(name, "is a file where an earlier entry made a directory '${joined.escaped()}'")
        for (end in 1 until path.size) {
            if (path.take(end).joinToString("/") in state.files) throw reject(name, "is below an earlier file or symlink")
        }
        state.files += joined
        for (end in 1 until path.size) state.directories += path.take(end).joinToString("/")
    }

    private fun reject(
        entry: String,
        problem: String,
    ) = InvalidContentException("Entry '${entry.escaped()}' $problem")

    private fun tooBig(entry: String) =
        InvalidContentException(
            "Entry '${entry.escaped()}' makes the uncompressed size exceed ${maxContentBytes * UNCOMPRESSED_FACTOR} bytes",
        )

    companion object {
        const val MAX_ENTRIES = 100_000
        const val UNCOMPRESSED_FACTOR = 10L

        private const val FILE_TYPE_MASK = 0xF000
        private const val REGULAR_FILE = 0x8000
        private const val DIRECTORY = 0x4000
        private const val SYMLINK = 0xA000
        private const val OWNER_EXECUTE = 0x40
        private const val MAX_SYMLINK_TARGET = 4096L
        private const val BUFFER_SIZE = 8192
        private val DRIVE = Regex("^[A-Za-z]:")

        private fun entryType(unixMode: Int) =
            when {
                unixMode and FILE_TYPE_MASK == SYMLINK -> Type.SYMLINK
                unixMode and OWNER_EXECUTE != 0 -> Type.EXECUTABLE
                else -> Type.REGULAR
            }
    }
}

private const val MAX_NAME_IN_MESSAGE = 200
private const val HEX = 16
private const val HEX_DIGITS = 4
private val LINE_BREAKS = setOf('\u007f', '\u0085', '\u2028', '\u2029')

/**
 * The name as it may appear in a message: entry names come from the caller, so control characters and line or
 * paragraph separators are written out, and a very long name is cut.
 */
private fun String.escaped(): String {
    val builder = StringBuilder()
    for (char in take(MAX_NAME_IN_MESSAGE)) {
        if (char < ' ' || char in LINE_BREAKS) {
            builder.append("\\u").append(char.code.toString(HEX).padStart(HEX_DIGITS, '0'))
        } else {
            builder.append(char)
        }
    }
    if (length > MAX_NAME_IN_MESSAGE) builder.append("...")
    return builder.toString()
}
