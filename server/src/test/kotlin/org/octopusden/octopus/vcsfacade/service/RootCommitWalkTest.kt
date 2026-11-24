package org.octopusden.octopus.vcsfacade.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RootCommitWalkTest {
    private fun commit(
        id: String,
        vararg parents: String,
    ) = WalkCommit(id, parents.toList(), "message of $id")

    /**
     * Pages are produced lazily; [requested] records which ones the walk asked for.
     */
    private class Pages(
        private val pages: List<List<WalkCommit>>,
    ) {
        val requested = mutableListOf<Int>()

        fun asSequence() =
            pages.indices.asSequence().map {
                requested += it
                pages[it]
            }
    }

    @Test
    fun `no commits means no root`() {
        assertNull(RootCommitWalk.find(sequenceOf(emptyList())))
        assertNull(RootCommitWalk.find(emptySequence()))
    }

    @Test
    fun `a single commit is its own root`() {
        assertEquals("a", RootCommitWalk.find(sequenceOf(listOf(commit("a"))))?.id)
    }

    @Test
    fun `linear history ends at the commit without parents`() {
        val history = listOf(commit("c", "b"), commit("b", "a"), commit("a"))
        assertEquals("a", RootCommitWalk.find(sequenceOf(history))?.id)
    }

    @Test
    fun `a merge follows the first parent, not another root brought in by the second`() {
        // head m merges main (first parent, root r1) and a side branch with its own root r2
        val history = listOf(
            commit("m", "main2", "side2"),
            commit("side2", "side1"),
            commit("side1"), // another root, listed last by the provider
            commit("main2", "main1"),
            commit("main1", "r1"),
            commit("r1"),
        )
        assertEquals("r1", RootCommitWalk.find(sequenceOf(history))?.id)
    }

    @Test
    fun `the last commit of the list is not taken for the root`() {
        val history = listOf(commit("m", "a", "b"), commit("a"), commit("b", "x"), commit("x"))
        assertEquals("a", RootCommitWalk.find(sequenceOf(history))?.id)
    }

    @Test
    fun `the chain is followed across pages`() {
        val pages = Pages(
            listOf(
                listOf(commit("e", "d"), commit("d", "c")),
                listOf(commit("c", "b"), commit("b", "a")),
                listOf(commit("a")),
            ),
        )
        assertEquals("a", RootCommitWalk.find(pages.asSequence())?.id)
        assertEquals(listOf(0, 1, 2), pages.requested)
    }

    @Test
    fun `paging stops as soon as the chain ends`() {
        val pages = Pages(
            listOf(
                listOf(commit("c", "b"), commit("b", "a")),
                listOf(commit("a")),
                listOf(commit("zzz")),
                listOf(commit("yyy")),
            ),
        )
        assertEquals("a", RootCommitWalk.find(pages.asSequence())?.id)
        assertEquals(listOf(0, 1), pages.requested)
    }

    @Test
    fun `no further page is read when the root is on the first one`() {
        val pages = Pages(listOf(listOf(commit("b", "a"), commit("a")), listOf(commit("zzz"))))
        assertEquals("a", RootCommitWalk.find(pages.asSequence())?.id)
        assertEquals(listOf(0), pages.requested)
    }

    @Test
    fun `a parent found on a later page than commits of another branch`() {
        val pages = Pages(
            listOf(
                listOf(commit("m", "p1", "s1"), commit("s1", "s0")),
                listOf(commit("s0")),
                listOf(commit("p1")),
            ),
        )
        assertEquals("p1", RootCommitWalk.find(pages.asSequence())?.id)
    }

    @Test
    fun `history that ends before the parent is found is an error, not a root`() {
        val history = listOf(commit("c", "b"), commit("b", "missing"))
        assertThrows(IllegalStateException::class.java) { RootCommitWalk.find(sequenceOf(history)) }
    }
}
