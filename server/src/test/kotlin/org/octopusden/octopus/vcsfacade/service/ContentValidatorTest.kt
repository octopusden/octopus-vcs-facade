package org.octopusden.octopus.vcsfacade.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException
import org.octopusden.octopus.vcsfacade.exception.ContentTooLargeException
import org.octopusden.octopus.vcsfacade.service.ZipContentEntry.Type
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContentValidatorTest {
    private val validator = ContentValidator(maxContentBytes = 1024 * 1024)

    private fun rejected(
        bytes: ByteArray,
        by: ContentValidator = validator,
    ) = assertThrows(InvalidContentException::class.java) { by.validate(bytes) }

    private fun rejectedNaming(
        name: String,
        bytes: ByteArray,
    ) = assertTrue(rejected(bytes).message!!.contains(name), "the message does not name $name")

    @Test
    fun `bytes that are not a zip, and an empty upload, are rejected`() {
        assertTrue(rejected("this is not a zip".toByteArray()).message!!.contains("zip", ignoreCase = true))
        rejected(ByteArray(0))
    }

    @ParameterizedTest
    @ValueSource(strings = [".git/config", "a/.git/HEAD", "a/b/.git/x", ".git", "a/.git", "a/../.git/config", ".GIT/config"])
    fun `an entry with a dot git segment is rejected and named`(name: String) =
        rejectedNaming(name, TestZips.zip { file("fine.txt").file(name) })

    @Test
    fun `files merely named like git files are fine`() {
        val zip = validator.validate(TestZips.zip { file(".gitignore").file(".github/workflows/ci.yml").file("a/.gitattributes") })
        assertEquals(listOf(".gitignore", ".github/workflows/ci.yml", "a/.gitattributes"), zip.entries.map { it.path })
    }

    @ParameterizedTest
    @ValueSource(strings = ["/x", "C:/x", "c:/dir/x", "/etc/passwd"])
    fun `an absolute path is rejected and named`(name: String) = rejectedNaming(name, TestZips.zip { file(name) })

    @ParameterizedTest
    @ValueSource(strings = ["../outside.txt", "a/../../outside.txt", "a/b/../../../x", "a/..\\..\\x", "x/y\\..\\..\\..\\z", ".."])
    fun `a path escaping the root is rejected and named`(name: String) = rejectedNaming(name, TestZips.zip { file(name) })

    @Test
    fun `dot dot that stays inside the root is normalised`() {
        val zip = validator.validate(TestZips.zip { file("a/../b.txt").file("c/./d.txt") })
        assertEquals(listOf("b.txt", "c/d.txt"), zip.entries.map { it.path })
    }

    @ParameterizedTest
    @ValueSource(strings = ["../x", "../../x", "/etc/passwd", "a/../../x", "C:/x", "..\\x"])
    fun `a symlink pointing outside the root is rejected and named`(target: String) =
        rejectedNaming("link", TestZips.zip { file("ok.txt").symlink("link", target) })

    @Test
    fun `a symlink pointing outside from a subdirectory is rejected`() {
        // a/b/link -> ../../../x climbs out of the root
        rejected(TestZips.zip { symlink("a/b/link", "../../../x") })
    }

    @Test
    fun `symlinks inside the root are kept as symlinks`() {
        val zip = validator.validate(
            TestZips.zip {
                file("target.txt")
                symlink("link", "target.txt")
                symlink("a/up", "../target.txt")
            },
        )
        val links = zip.entries.filter { it.type == Type.SYMLINK }
        assertEquals(listOf("link", "a/up"), links.map { it.path })
        assertEquals("target.txt".length.toLong(), links.first().size)
    }

    @Test
    fun `symlinks that climb out of the root through each other are rejected, in either order`() {
        val first = rejected(TestZips.zip { symlink("a", ".").symlink("b", "a/..") })
        assertTrue(first.message!!.contains("'b'"), first.message)
        rejected(TestZips.zip { symlink("b", "a/..").symlink("a", ".") })
        rejected(TestZips.zip { symlink("d/up", "..").symlink("x", "d/up/..") })
    }

    @Test
    fun `a legitimate chain of symlinks is accepted, a dangling target inside the root too`() {
        val zip = validator.validate(
            TestZips.zip {
                symlink("x", "lib/file")
                symlink("lib", "sub")
                symlink("sub", "real")
                file("real/file")
                symlink("dangling", "nowhere/at/all")
            },
        )
        assertEquals(listOf("x", "lib", "sub", "real/file", "dangling"), zip.entries.map { it.path })
    }

    @Test
    fun `symlinks that loop are rejected`() {
        val message = rejected(TestZips.zip { symlink("a", "b").symlink("b", "a") }).message!!
        assertTrue(message.contains("loop"), message)
        rejected(TestZips.zip { symlink("self", "self") })
    }

    @Test
    fun `a dangling target is followed by depth, so an escape behind it is still found`() {
        rejected(TestZips.zip { symlink("l", "missing/../../x") })
        rejected(TestZips.zip { symlink("d/l", "a/b/../../../../x") })
        validator.validate(TestZips.zip { symlink("l", "missing/../x").symlink("d/m", "a/b/../../../x") })
    }

    @Test
    fun `a very deep entry name validates quickly`() {
        val deep = List(20_000) { "a" }.joinToString("/") + "/f"
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            assertEquals(1, validator.validate(TestZips.zip { file(deep).directory("a/") }).entries.size)
        }
    }

    @Test
    fun `many symlinks with long targets are checked within the step budget, and fast`() {
        val target = List(1500) { "a" }.joinToString("/")
        val zip = TestZips.zip { (1..300).forEach { symlink("l$it", target) } }
        assertTimeoutPreemptively(Duration.ofSeconds(5)) { validator.validate(zip) }
        // The same links, chained through one another, cost hops times the target length each.
        val chained = TestZips.zip { (1..300).forEach { symlink("c$it", "c${it % 300 + 1}/$target") } }
        assertTimeoutPreemptively(Duration.ofSeconds(10)) { rejected(chained) }
    }

    @Test
    fun `symlinks that take too many steps to check are rejected`() {
        val zip = TestZips.zip { (1..10).forEach { symlink("l$it", "a/b/c/d") } }
        val small = ContentValidator(1024 * 1024, ContentValidator.MAX_ENTRIES, 20)
        assertTrue(rejected(zip, small).message!!.contains("too many steps"))
        ContentValidator(1024 * 1024, ContentValidator.MAX_ENTRIES, 1000).validate(zip)
    }

    @Test
    fun `a file then a directory of the same path is rejected`() {
        rejected(TestZips.zip { file("a").directory("a/") })
        rejected(TestZips.zip { symlink("a", "b").directory("a/") })
    }

    @Test
    fun `a directory named twice is rejected, also after normalisation`() {
        rejected(TestZips.zip { directory("a/").directory("a/./") })
        rejected(TestZips.zip { directory("a/").directory("a/") })
    }

    @Test
    fun `a directory entry with data is rejected`() {
        val message = rejected(TestZips.zip { directory("a/", "data") }).message!!
        assertTrue(message.contains("directory with content"), message)
    }

    @Test
    fun `a directory below a file is rejected`() {
        rejected(TestZips.zip { file("a").directory("a/b/") })
    }

    @Test
    fun `a directory entry before or after the files below it is fine, and it is no entry itself`() {
        assertEquals(listOf("a/x"), validator.validate(TestZips.zip { file("a/x").directory("a/") }).entries.map { it.path })
        assertEquals(listOf("a/x"), validator.validate(TestZips.zip { directory("a/").file("a/x") }).entries.map { it.path })
        assertEquals(emptyList<ZipContentEntry>(), validator.validate(TestZips.zip { directory("./") }).entries)
    }

    @Test
    fun `the bytes of a directory entry count towards the inflated total`() {
        rejected(TestZips.zip { directory("a/", "0".repeat(2500)) }, ContentValidator(maxContentBytes = 200))
    }

    @Test
    fun `a valid zip yields its entries with their modes, without directories`() {
        val zip = validator.validate(
            TestZips.zip {
                directory("scripts")
                executable("scripts/run.sh")
                file("README.md")
                symlink("docs-link", "README.md")
            },
        )
        assertEquals(
            listOf(
                ZipContentEntry("scripts/run.sh", Type.EXECUTABLE, "#!/bin/sh\n".length.toLong()),
                ZipContentEntry("README.md", Type.REGULAR, "content of README.md".length.toLong()),
                ZipContentEntry("docs-link", Type.SYMLINK, "README.md".length.toLong()),
            ),
            zip.entries,
        )
    }

    @Test
    fun `entries without Unix modes are regular files`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("plain.txt"))
            it.write("x".toByteArray())
            it.closeEntry()
        }
        assertEquals(
            Type.REGULAR,
            validator
                .validate(out.toByteArray())
                .entries
                .single()
                .type,
        )
    }

    @Test
    fun `the file contents can be read back`() {
        val zip = validator.validate(TestZips.zip { file("a.txt", "hello").symlink("l", "a.txt") })
        val read = mutableMapOf<String, String>()
        zip.forEachFile { entry, stream -> read[entry.path] = String(stream.readBytes()) }
        assertEquals(mapOf("a.txt" to "hello", "l" to "a.txt"), read)
    }

    @Test
    fun `an entry below a file or symlink is rejected`() {
        rejected(TestZips.zip { file("a").file("a/b") })
        rejected(TestZips.zip { symlink("dir", "other").file("dir/x") })
    }

    @Test
    fun `the same path twice is rejected, also after normalisation`() {
        rejected(TestZips.zip { file("a.txt").file("a.txt") })
        rejected(TestZips.zip { file("a.txt").file("b/../a.txt") })
        rejected(TestZips.zip { file("a/b.txt").file("a//b.txt") })
    }

    @Test
    fun `the first offending entry is the one named`() {
        val exception = rejected(TestZips.zip { file("ok.txt").file("../first").file(".git/second") })
        assertTrue(exception.message!!.contains("../first"), exception.message)
        assertFalse(exception.message!!.contains(".git/second"), exception.message)
    }

    @Test
    fun `control characters in an entry name are escaped in the message`() {
        val exception = rejected(TestZips.zip { file("evil\nname\r\u2028\u007f/../../x") })
        val message = exception.message!!
        assertTrue(message.none { it < ' ' || it == '\u007f' || it == '\u2028' }, message)
        assertTrue(message.contains("evil\\u000aname\\u000d\\u2028\\u007f"), message)
    }

    @Test
    fun `a very long entry name is cut in the message`() {
        val message = rejected(TestZips.zip { file("../" + "n".repeat(1000)) }).message!!
        assertTrue(message.length < 400, "${message.length}")
    }

    @Test
    fun `an upload over the size limit is too large, one at the limit is not`() {
        assertThrows(ContentTooLargeException::class.java) { ContentValidator(maxContentBytes = 100).validate(ByteArray(101)) }
        val bytes = TestZips.zip { file("a.txt", "x") }
        ContentValidator(maxContentBytes = bytes.size.toLong()).validate(bytes)
    }

    @Test
    fun `a zip that inflates past ten times the limit is rejected`() {
        val bomb = TestZips.zip { file("zeros.bin", "0".repeat(2500)) }
        assertTrue(bomb.size < 200, "the sample must be small enough to pass the upload limit: ${bomb.size}")
        val message = rejected(bomb, ContentValidator(maxContentBytes = 200)).message!!
        assertTrue(message.contains("zeros.bin") && message.contains("uncompressed"), message)
    }

    @Test
    fun `the inflated total is summed over all entries`() {
        val bomb = TestZips.zip { (1..30).forEach { file("f$it.bin", "0".repeat(5000)) } }
        rejected(bomb, ContentValidator(maxContentBytes = 10_000, maxEntries = 1000))
    }

    @Test
    fun `the bytes actually read count when the declared size lies`() {
        // The header declares 10 bytes, so the declared total stays far under the limit; the entry holds 5000.
        val bytes = TestZips.zip { file("liar.bin", "0".repeat(5000)) }
        val lying = declareSize(bytes, 10)
        val exception = rejected(lying, ContentValidator(maxContentBytes = 400))
        assertTrue(exception.message!!.contains("liar.bin"), exception.message)
    }

    @Test
    fun `more entries than the limit are rejected`() {
        val many = TestZips.zip { (1..4).forEach { file("f$it.txt", "x") } }
        val exception = rejected(many, ContentValidator(1024 * 1024, maxEntries = 3))
        assertTrue(exception.message!!.contains("entries"), exception.message)
    }

    @Test
    fun `the default limits are ten times the size and 100000 entries`() {
        assertEquals(10L, ContentValidator.UNCOMPRESSED_FACTOR)
        assertEquals(100_000, ContentValidator.MAX_ENTRIES)
    }

    /**
     * Rewrites the uncompressed size in every local and central header of the zip.
     */
    private fun declareSize(
        zip: ByteArray,
        size: Int,
    ): ByteArray {
        val copy = zip.copyOf()

        fun putInt(
            at: Int,
            value: Int,
        ) = (0..3).forEach { copy[at + it] = (value ushr (8 * it)).toByte() }

        fun intAt(at: Int) = (0..3).sumOf { (copy[at + it].toInt() and 0xff) shl (8 * it) }
        var i = 0
        while (i < copy.size - 4) {
            val signature = intAt(i)
            if (signature == LOCAL_HEADER) putInt(i + 22, size)
            if (signature == CENTRAL_HEADER) putInt(i + 24, size)
            i++
        }
        return copy
    }

    private companion object {
        const val LOCAL_HEADER = 0x04034b50
        const val CENTRAL_HEADER = 0x02014b50
    }
}
