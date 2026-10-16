package com.scarlett.juzizhentan.data.search

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = "ddg"
)

class DuckDuckGoClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    fun search(query: String, limit: Int = 8): List<SearchHit> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val merged = LinkedHashMap<String, SearchHit>()
        fun addAll(list: List<SearchHit>) {
            for (h in list) {
                val key = normUrl(h.url)
                if (key.isBlank()) continue
                if (!merged.containsKey(key)) merged[key] = h
                if (merged.size >= limit) return
            }
        }

        // 1) Instant Answer JSON（HTTPS）
        runCatching { addAll(instantAnswer(q, limit)) }
        if (merged.size >= 3) return merged.values.take(limit)

        // 2) HTML 版结果页（HTTPS）
        runCatching { addAll(htmlSearch(q, limit)) }
        if (merged.isNotEmpty()) return merged.values.take(limit)

        // 换一种查询再试 HTML
        val alt = "$q 金句"
        if (alt != q) {
            runCatching { addAll(htmlSearch(alt, limit)) }
        }
        return merged.values.take(limit)
    }

    private fun instantAnswer(query: String, limit: Int): List<SearchHit> {
        val enc = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "https://api.duckduckgo.com/?q=$enc&format=json&no_html=1&skip_disambig=1"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return emptyList()
            val root = JSONObject(text)
            val hits = mutableListOf<SearchHit>()

            val abstract = root.optString("Abstract").orEmpty()
            val abstractUrl = root.optString("AbstractURL").orEmpty()
            val heading = root.optString("Heading").orEmpty()
            if (abstract.isNotBlank() && abstractUrl.isNotBlank()) {
                hits += SearchHit(heading.ifBlank { query }, abstractUrl, abstract, "ddg-ia")
            }

            val related = root.optJSONArray("RelatedTopics") ?: return hits.take(limit)
            fun walk(arr: org.json.JSONArray) {
                for (i in 0 until arr.length()) {
                    if (hits.size >= limit) return
                    val item = arr.opt(i) ?: continue
                    if (item is JSONObject) {
                        if (item.has("Topics")) {
                            walk(item.getJSONArray("Topics"))
                        } else {
                            val t = item.optString("Text").orEmpty()
                            val u = item.optString("FirstURL").orEmpty()
                            if (t.isNotBlank() && u.isNotBlank()) {
                                hits += SearchHit(t.take(80), u, t, "ddg-ia")
                            }
                        }
                    }
                }
            }
            walk(related)
            return hits.take(limit)
        }
    }

    private fun htmlSearch(query: String, limit: Int): List<SearchHit> {
        val enc = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "https://html.duckduckgo.com/html/?q=$enc"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val html = resp.body?.string().orEmpty()
            if (html.isBlank()) return emptyList()
            return parseHtmlResults(html, limit)
        }
    }

    private fun parseHtmlResults(html: String, limit: Int): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        // DDG html: result blocks with class result / links uddg=
        val blockPat = Pattern.compile(
            "(?s)<div[^>]*class=\"[^\"]*result[^\"]*\"[^>]*>(.*?)</div>\\s*(?=<div[^>]*class=\"[^\"]*result|</div>\\s*</div>\\s*</body>|$)",
            Pattern.CASE_INSENSITIVE
        )
        val linkPat = Pattern.compile(
            "uddg=([^&\"']+)|href=\"(https?://(?!duckduckgo\\.com)[^\"]+)\"",
            Pattern.CASE_INSENSITIVE
        )
        val titlePat = Pattern.compile(
            "<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val snipPat = Pattern.compile(
            "<a[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>|<td[^>]*class=\"[^\"]*result-snippet[^\"]*\"[^>]*>(.*?)</td>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )

        val matcher = blockPat.matcher(html)
        var foundBlocks = false
        while (matcher.find() && hits.size < limit) {
            foundBlocks = true
            val block = matcher.group(1) ?: continue
            val linkM = linkPat.matcher(block)
            var rawUrl = ""
            if (linkM.find()) {
                rawUrl = linkM.group(1) ?: linkM.group(2) ?: ""
            }
            val url = decodeUrl(rawUrl)
            if (url.isBlank() || url.contains("duckduckgo.com")) continue
            val titleM = titlePat.matcher(block)
            val title = if (titleM.find()) stripTags(titleM.group(1) ?: "") else url
            val snipM = snipPat.matcher(block)
            val snippet = if (snipM.find()) {
                stripTags((snipM.group(1) ?: snipM.group(2) ?: ""))
            } else ""
            if (snippet.isBlank() && title.isBlank()) continue
            hits += SearchHit(title, url, snippet.ifBlank { title }, "ddg-html")
        }

        if (!foundBlocks || hits.isEmpty()) {
            // Fallback: scan all uddg= links
            val uddg = Pattern.compile("uddg=([^&\"']+)")
            val m = uddg.matcher(html)
            val seen = HashSet<String>()
            while (m.find() && hits.size < limit) {
                val u = decodeUrl(m.group(1) ?: "")
                val key = normUrl(u)
                if (u.isBlank() || !seen.add(key) || u.contains("duckduckgo.com")) continue
                hits += SearchHit(u, u, "", "ddg-html")
            }
        }
        return hits
    }

    private fun decodeUrl(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
                .replace("&amp;", "&")
                .trim()
        } catch (_: Exception) {
            raw.trim()
        }
    }

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun normUrl(url: String): String {
            return try {
                val noFrag = url.trim().substringBefore('#').lowercase()
                noFrag.removePrefix("https://").removePrefix("http://").trimEnd('/')
            } catch (_: Exception) {
                url.trim().lowercase()
            }
        }
    }
}
