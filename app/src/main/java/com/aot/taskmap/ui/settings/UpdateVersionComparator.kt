package com.aot.taskmap.ui.settings

object UpdateVersionComparator {

    fun isRemoteVersionNewer(remoteVersion: String, localVersion: String): Boolean {
        val remoteParts = extractVersionParts(remoteVersion)
        val localParts = extractVersionParts(localVersion)

        if (remoteParts.isEmpty() || localParts.isEmpty()) {
            return remoteVersion.trim() != localVersion.trim()
        }

        val maxSize = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until maxSize) {
            val remote = remoteParts.getOrElse(index) { 0 }
            val local = localParts.getOrElse(index) { 0 }
            if (remote != local) return remote > local
        }
        return false
    }

    private fun extractVersionParts(version: String): List<Int> {
        val normalized = version.trim().lowercase()
        val match = Regex("""\d+(?:\.\d+)*""").find(normalized) ?: return emptyList()
        return match.value
            .split('.')
            .mapNotNull { part -> part.toIntOrNull() }
    }
}
