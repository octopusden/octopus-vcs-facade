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
 * - a symlink is followed through the other symlinks of the zip before it counts as inside the root;
 * - nothing is written below a file or a symlink, no path is used twice, and a directory entry carries no data;
 * - the zip does not inflate past [UNCOMPRESSED_FACTOR] times [maxContentBytes], or hold more than [maxEntries] entries.
 *
 * The first offending entry is named as the zip is read; symlink chains are checked after every entry is read.
 */
@Suppress("ThrowsCount") // a validator rejects for many reasons, each with its own message
class ContentValidator internal constructor(
    private val maxContentBytes: Long,
    private val maxEntries: Int,
    private val maxSymlinkSteps: Long,
) {
    constructor(maxContentBytes: Long, maxEntries: Int = MAX_ENTRIES) : this(maxContentBytes, maxEntries, SymlinkChains.MAX_STEPS)

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
        SymlinkChains(state.paths, maxSymlinkSteps).firstViolation()?.let {
            throw reject(it.entry, "is a symlink whose target '${it.target.escaped()}' ${it.problem}")
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
        val paths = ArchivePaths()
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
        val isDirectory = entry.unixMode and FILE_TYPE_MASK == DIRECTORY || entry.isDirectory
        oddKind(entry, isDirectory)?.let { throw reject(name, it) }
        if (path.isEmpty() && !isDirectory) throw reject(name, "has no file name")
        val type = entryType(entry.unixMode)
        val read = measure(zip, entry, state, if (type == Type.SYMLINK) MAX_SYMLINK_TARGET else 0)
        if (isDirectory) {
            if (read.size > 0) throw reject(name, "is a directory with content")
            state.paths.claimDirectory(path)?.let { throw reject(name, it) }
            return
        }
        val target = if (type == Type.SYMLINK) checkSymlink(name, path, read) else null
        val claimed = if (target == null) state.paths.claimFile(path) else state.paths.claimSymlink(path, name, target)
        claimed?.let { throw reject(name, it) }
        state.entries += ZipContentEntry(path.joinToString("/"), type, read.size)
        state.namesInZip[path.joinToString("/")] = name
    }

    /**
     * Counts the entry's declared size, then reads it through to count what it really holds; a directory is read too.
     */
    private fun measure(
        zip: ZipFile,
        entry: ZipArchiveEntry,
        state: Collected,
        keep: Long,
    ): EntryRead {
        state.declaredTotal += maxOf(entry.size, 0)
        if (state.declaredTotal > maxContentBytes * UNCOMPRESSED_FACTOR) throw tooBig(entry.name)
        val read = read(zip, entry, state, keep)
        if (entry.size >= 0 && entry.size != read.size) {
            throw reject(entry.name, "holds ${read.size} bytes, not the ${entry.size} its header says")
        }
        return read
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
    ): String {
        if (read.size > MAX_SYMLINK_TARGET) throw reject(name, "is a symlink with a target of more than $MAX_SYMLINK_TARGET bytes")
        val target = String(read.kept, Charsets.UTF_8)
        if (target.isBlank()) throw reject(name, "is a symlink with no target")
        // Only the shape here; whether `..` leaves the root is decided by SymlinkChains, which follows other symlinks
        // (`a -> dir/sub`, `b -> a/../..` stays inside, though a plain reading of `b` climbs out).
        resolveOrReject(target, name, path.dropLast(1), containParents = false) { "is a symlink whose target '${target.escaped()}' $it" }
        return target
    }

    private fun resolveOrReject(
        raw: String,
        entry: String,
        base: List<String> = emptyList(),
        containParents: Boolean = true,
        describe: (String) -> String,
    ) = try {
        resolve(raw, base, containParents)
    } catch (expected: PathProblem) {
        throw reject(entry, describe(expected.problem))
    }

    /**
     * The segments of [raw], resolved against [base].
     *
     * @param containParents whether a `..` above the root is a problem; when not, it is left for a later check
     * @throws PathProblem when the path is absolute, has a `.git` segment, or cannot stay inside the root
     */
    private fun resolve(
        raw: String,
        base: List<String>,
        containParents: Boolean,
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
                    if (resolved.isNotEmpty()) {
                        resolved.removeAt(resolved.size - 1)
                    } else if (containParents) {
                        throw PathProblem("escapes the repository root")
                    }
                }
                else -> resolved += segment
            }
        }
        return resolved
    }

    private class PathProblem(
        val problem: String,
    ) : Exception(problem)

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

        private fun oddKind(
            entry: ZipArchiveEntry,
            isDirectory: Boolean,
        ): String? {
            val kind = entry.unixMode and FILE_TYPE_MASK
            val known = isDirectory || kind == 0 || kind == REGULAR_FILE || kind == SYMLINK
            return if (known) null else "is neither a file, a directory nor a symlink"
        }

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
internal fun String.escaped(): String {
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

private fun reject(
    entry: String,
    problem: String,
) = InvalidContentException("Entry '${entry.escaped()}' $problem")
