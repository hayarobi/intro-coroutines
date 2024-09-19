package tasks

import contributors.GitHubService
import contributors.RequestData
import contributors.User
import contributors.log
import contributors.logRepos
import contributors.logUsers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

suspend fun loadContributorsChannels(
    service: GitHubService,
    req: RequestData,
    updateResults: suspend (List<User>, completed: Boolean) -> Unit
) {
    val channel = Channel<List<User>>(10)
    coroutineScope {
        val repos = service
            .getOrgRepos(req.org)
            .also { logRepos(req, it) }
            .bodyList()

        val repoSize = repos.size
        for ((index, repo) in repos.withIndex()) {
            async {
                val users = service.getRepoContributors(req.org, repo.name)
                    .also { logUsers(repo, it) }
                    .bodyList()
                channel.send(users)
            }
        }

        var allUsers = emptyList<User>()
        repeat(repoSize) {
            var received = 0
            val users = channel.receive()
            allUsers = (allUsers + users).aggregate()
            updateResults(allUsers, false)
        }
        log.info("Received all repos ")
        updateResults(allUsers, true)
    }
}
