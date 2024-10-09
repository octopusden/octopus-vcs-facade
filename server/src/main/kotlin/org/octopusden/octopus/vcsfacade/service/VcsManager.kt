package org.octopusden.octopus.vcsfacade.service

import java.util.Date
import org.octopusden.octopus.vcsfacade.client.common.dto.Branch
import org.octopusden.octopus.vcsfacade.client.common.dto.Commit
import org.octopusden.octopus.vcsfacade.client.common.dto.CommitWithFiles
import org.octopusden.octopus.vcsfacade.client.common.dto.CreatePullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.CreateTag
import org.octopusden.octopus.vcsfacade.client.common.dto.PullRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssueInRangesResponse
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchIssuesInRangesRequest
import org.octopusden.octopus.vcsfacade.client.common.dto.SearchSummary
import org.octopusden.octopus.vcsfacade.client.common.dto.Tag

interface VcsManager { //TODO: allow to use both http and ssh repository url (renaming `sshUrl` to `repositoryUrl`)
    val vcsServices: Collection<VcsService>
    fun findVcsServiceById(id: String): VcsService?
    fun getVcsServiceById(id: String): VcsService
    fun getVcsServiceForSshUrl(sshUrl: String): VcsService
    fun getTags(sshUrl: String): Sequence<Tag>
    fun createTag(sshUrl: String, createTag: CreateTag): Tag
    fun getTag(sshUrl: String, name: String): Tag
    fun deleteTag(sshUrl: String, name: String)
    fun getCommits(sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String): Sequence<Commit>
    fun getCommitsWithFiles(sshUrl: String, fromHashOrRef: String?, fromDate: Date?, toHashOrRef: String): Sequence<CommitWithFiles>
    fun getCommit(sshUrl: String, hashOrRef: String): Commit
    fun getCommitWithFiles(sshUrl: String, hashOrRef: String): CommitWithFiles
    fun createPullRequest(sshUrl: String, createPullRequest: CreatePullRequest): PullRequest
    fun searchIssuesInRanges(searchRequest: SearchIssuesInRangesRequest): SearchIssueInRangesResponse
    fun findBranches(issueKey: String): Sequence<Branch>
    fun findCommits(issueKey: String): Sequence<Commit>
    fun findCommitsWithFiles(issueKey: String): Sequence<CommitWithFiles>
    fun findPullRequests(issueKey: String): Sequence<PullRequest>
    fun find(issueKey: String): SearchSummary
}
