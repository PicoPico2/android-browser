package com.example.privatebrowser

import java.net.URI

/** Deliberately limited grammar. Unsupported syntax is counted, never broadened. */
internal data class FilterRules(
    val blockedHosts: Set<String> = emptySet(),
    val allowedHosts: Set<String> = emptySet(),
    val cosmetic: Map<String, Set<String>> = emptyMap(),
    val cosmeticExceptions: Map<String, Set<String>> = emptyMap(),
    val skipped: Int = 0,
) {
    fun blocks(url: String): Boolean {
        val host = hostOf(url)
        if (host.isEmpty()) return false
        return !matches(host, allowedHosts) && matches(host, blockedHosts)
    }

    fun selectors(host: String): Set<String> {
        val hidden = cosmetic.filterKeys { it.isEmpty() || domainMatches(host, it) }.values.flatten().toSet()
        val exceptions = cosmeticExceptions.filterKeys { it.isEmpty() || domainMatches(host, it) }.values.flatten().toSet()
        return hidden - exceptions
    }

    companion object {
        private val domain = Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}")
        private val selector = Regex("[.#][A-Za-z_][A-Za-z0-9_-]*")

        fun parse(lines: Sequence<String>): FilterRules {
            val deny = mutableSetOf<String>()
            val allow = mutableSetOf<String>()
            val hide = mutableMapOf<String, MutableSet<String>>()
            val show = mutableMapOf<String, MutableSet<String>>()
            var skipped = 0
            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() || line.startsWith("!") || line.startsWith("[") -> Unit
                    line.contains("#@#") || line.contains("##") -> {
                        val exception = line.contains("#@#")
                        val pieces = line.split(if (exception) "#@#" else "##", limit = 2)
                        val domains = pieces[0].lowercase().split(',')
                        if (!selector.matches(pieces[1]) || domains.any { it.isNotEmpty() && !domain.matches(it) }) skipped++
                        else domains.forEach { host ->
                            (if (exception) show else hide).getOrPut(host) { mutableSetOf() }.add(pieces[1])
                        }
                    }
                    else -> {
                        val exception = line.startsWith("@@")
                        val rule = line.removePrefix("@@")
                        val host = rule.removePrefix("||").removeSuffix("^").lowercase()
                        if (rule.startsWith("||") && rule.endsWith("^") && domain.matches(host)) {
                            (if (exception) allow else deny).add(host)
                        } else skipped++
                    }
                }
            }
            return FilterRules(deny.toSet(), allow.toSet(), hide.mapValues { it.value.toSet() }, show.mapValues { it.value.toSet() }, skipped)
        }

        fun hostOf(url: String): String = runCatching {
            val uri = URI(url)
            if (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) uri.host.orEmpty().lowercase().trimEnd('.') else ""
        }.getOrDefault("")

        fun domainMatches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

        private fun matches(host: String, hosts: Set<String>): Boolean {
            var suffix = host
            while (suffix.isNotEmpty()) {
                if (suffix in hosts) return true
                suffix = suffix.substringAfter('.', "")
            }
            return false
        }
    }
}
