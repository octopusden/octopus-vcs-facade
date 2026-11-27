package org.octopusden.octopus.vcsfacade.service

import org.octopusden.octopus.vcsfacade.client.common.exception.InvalidContentException

/**
 * One path segment of an uploaded zip: a file, a symlink (with its target), or a directory that an entry named
 * ([EXPLICIT_DIR]) or that only files below it implied ([IMPLIED_DIR]).
 */
internal class PathNode(
    var kind: Kind,
    val parent: PathNode?,
) {
    enum class Kind { IMPLIED_DIR, EXPLICIT_DIR, FILE, SYMLINK }

    var children: HashMap<String, PathNode>? = null

    /** For a symlink: the entry as named in the zip, the target as written, and its segments. */
    var entry: String? = null
    var target: String? = null
    var targetSegments: List<String> = emptyList()

    val isLeaf get() = kind == Kind.FILE || kind == Kind.SYMLINK

    fun child(name: String) = children?.get(name)

    fun add(
        name: String,
        kind: Kind,
    ) = PathNode(kind, this).also { (children ?: HashMap<String, PathNode>().also { map -> children = map })[name] = it }
}

/**
 * The paths an uploaded zip has claimed so far, as a tree, so each claim walks its segments once. A claim that
 * cannot be honoured on a file system is answered with its problem, `null` when fine.
 */
internal class ArchivePaths {
    val root = PathNode(PathNode.Kind.IMPLIED_DIR, null)

    /** The symlinks, in the order the zip names them. */
    val symlinks = mutableListOf<PathNode>()

    fun claimFile(path: List<String>): String? = claim(path, PathNode.Kind.FILE)

    fun claimSymlink(
        path: List<String>,
        entry: String,
        target: String,
    ): String? {
        val problem = claim(path, PathNode.Kind.SYMLINK)
        if (problem == null) {
            val node = nodeAt(path)
            node.entry = entry
            node.target = target
            node.targetSegments = target.replace('\\', '/').split('/')
            symlinks += node
        }
        return problem
    }

    /** A directory entry, as opposed to one only implied by the files below it, which may be named once. */
    fun claimDirectory(path: List<String>): String? = if (path.isEmpty()) claimRoot() else claim(path, PathNode.Kind.EXPLICIT_DIR)

    /** An entry that names the root itself, such as `./`: allowed once, like any other directory entry. */
    private fun claimRoot(): String? {
        if (root.kind == PathNode.Kind.EXPLICIT_DIR) return "repeats the root directory"
        root.kind = PathNode.Kind.EXPLICIT_DIR
        return null
    }

    private fun nodeAt(path: List<String>) = path.fold(root) { node, name -> checkNotNull(node.child(name)) }

    private fun claim(
        path: List<String>,
        kind: PathNode.Kind,
    ): String? {
        var node = root
        for (name in path.subList(0, path.size - 1)) {
            val child = node.child(name)
            if (child != null && child.isLeaf) return "is below an earlier file or symlink"
            node = child ?: node.add(name, PathNode.Kind.IMPLIED_DIR)
        }
        val existing = node.child(path.last())
        if (existing == null) {
            node.add(path.last(), kind)
            return null
        }
        return conflict(existing, kind, path)
    }

    private fun conflict(
        existing: PathNode,
        kind: PathNode.Kind,
        path: List<String>,
    ): String? {
        val joined = path.joinToString("/").escaped()
        val wantsDirectory = kind == PathNode.Kind.EXPLICIT_DIR
        return when {
            existing.isLeaf && !wantsDirectory -> "repeats the path '$joined'"
            existing.isLeaf -> "is a directory where an earlier entry is a file or symlink '$joined'"
            !wantsDirectory -> "is a file where an earlier entry made a directory '$joined'"
            existing.kind == PathNode.Kind.EXPLICIT_DIR -> "repeats the path '$joined'"
            else -> {
                existing.kind = PathNode.Kind.EXPLICIT_DIR
                null
            }
        }
    }
}

/**
 * The symlinks of an uploaded zip. Each target is resolved the way a file system would, through the other symlinks
 * of the archive, so a chain like `a -> .` and `b -> a/..` cannot climb out of the root. A target that does not exist
 * in the archive is allowed as long as it stays inside the root.
 *
 * The work is bounded: a link is followed at most [MAX_HOPS] times, and all links of one zip together take at most
 * [maxSteps] steps (one per path segment processed).
 */
internal class SymlinkChains(
    private val paths: ArchivePaths,
    private val maxSteps: Long = MAX_STEPS,
) {
    class Violation(
        val entry: String,
        val target: String,
        val problem: String,
    )

    private var steps = 0L

    /** The first symlink, in the order the zip names them, whose target leaves the root or never ends. */
    fun firstViolation(): Violation? =
        paths.symlinks.firstNotNullOfOrNull { link ->
            Walk(link).run()?.let { Violation(checkNotNull(link.entry), checkNotNull(link.target), it) }
        }

    private fun spend() {
        if (++steps > maxSteps) throw InvalidContentException("The zip's symlinks take too many steps to check")
    }

    private class Frame(
        val segments: List<String>,
    ) {
        var index = 0
    }

    /** One symlink's target, walked from the directory the link is in, on a node pointer and a stack of pending segments. */
    private inner class Walk(
        link: PathNode,
    ) {
        private var current = checkNotNull(link.parent)

        /** How many names below [current] are past the end of what the archive holds; only counted, never looked up. */
        private var missing = 0
        private var hops = 0
        private val frames = ArrayDeque(listOf(Frame(link.targetSegments)))

        fun run(): String? {
            while (frames.isNotEmpty()) {
                val frame = frames.last()
                if (frame.index >= frame.segments.size) {
                    frames.removeLast()
                    continue
                }
                spend()
                val problem = step(frame.segments[frame.index++])
                if (problem != null) return problem
            }
            return null
        }

        private fun step(segment: String): String? =
            when (segment) {
                "", "." -> null
                ".." -> up()
                else -> down(segment)
            }

        private fun up(): String? {
            when {
                missing > 0 -> missing--
                current.parent == null -> return "escapes the repository root"
                else -> current = checkNotNull(current.parent)
            }
            return null
        }

        private fun down(segment: String): String? {
            if (missing > 0) {
                missing++
                return null
            }
            val child = current.child(segment)
            when {
                child == null -> missing = 1
                child.kind == PathNode.Kind.SYMLINK -> {
                    // Followed from its own directory: the link is replaced by its target.
                    if (++hops > MAX_HOPS) return "is a loop or a chain of more than $MAX_HOPS symlinks"
                    frames.addLast(Frame(child.targetSegments))
                }
                else -> current = child
            }
            return null
        }
    }

    companion object {
        const val MAX_HOPS = 40
        const val MAX_STEPS = 1_000_000L
    }
}
