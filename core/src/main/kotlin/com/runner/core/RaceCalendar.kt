package com.runner.core

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 대회 일정표의 대회 한 줄. */
data class CalendarRace(
    val no: String,
    val name: String,
    val date: LocalDate,
    val courses: String,
    val place: String,
    val homepage: String,
) {
    /** 코스 중 가장 긴 거리를 가까운 종목으로 묶는다(트레일·울트라 포함, 없으면 10K). */
    val distance: RaceDistance get() {
        val km = Regex("""(\d+(?:\.\d+)?)\s*k""", RegexOption.IGNORE_CASE).findAll(courses)
            .map { it.groupValues[1].toDouble() }.maxOrNull()
        return when {
            "풀" in courses || (km ?: 0.0) >= 30 -> RaceDistance.FULL
            "하프" in courses || (km ?: 0.0) >= 15 -> RaceDistance.HALF
            km == null || km >= 8 -> RaceDistance.TEN_K
            else -> RaceDistance.FIVE_K
        }
    }
}

@Serializable
private data class JrPage(val races: List<JrRace> = emptyList())

@Serializable
private data class JrRace(
    val id: Int,
    val name: String,
    val date: String,
    val location: String? = null,
    /** JSON 배열을 담은 문자열. 예: "[\"하프\",\"10km\"]" */
    val distances: String? = null,
    val website: String? = null,
    val registrationUrl: String? = null,
)

/**
 * 저스트런(justrun.co.kr) 대회 일정 API. 사이트 운영자 허락을 받고 쓴다.
 * 한 번에 최대 100개, 날짜 늦은 순으로 주므로 지난 대회가 나올 때까지 넘긴다.
 */
object RaceCalendar {
    const val URL = "https://justrun.co.kr/schedule"
    private const val API = "https://marathon-api.t8-stats-rooney.workers.dev/api/races"
    private const val PAGE = 100
    private val json = Json { ignoreUnknownKeys = true }

    /** 네트워크로 받아 오늘 이후 대회를 날짜순으로 돌려준다. 블로킹이라 IO 스레드에서 부를 것. */
    fun fetch(today: LocalDate = LocalDate.now()): List<CalendarRace> {
        val all = mutableListOf<CalendarRace>()
        // ponytail: 최대 10페이지(1000개). 다가오는 대회가 그보다 많아지면 늘릴 것
        for (page in 0 until 10) {
            val conn = java.net.URL("$API?limit=$PAGE&offset=${page * PAGE}").openConnection()
                .apply { connectTimeout = 10_000; readTimeout = 15_000 }
            val races = parse(conn.getInputStream().use { it.readBytes() }.decodeToString())
            all += races
            if (races.size < PAGE || races.any { it.date.isBefore(today) }) break
        }
        return all.filter { !it.date.isBefore(today) }.sortedBy { it.date }
    }

    fun parse(text: String): List<CalendarRace> = json.decodeFromString<JrPage>(text).races.mapNotNull { r ->
        val date = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@mapNotNull null
        val courses = runCatching { json.decodeFromString<List<String>>(r.distances.orEmpty()) }.getOrDefault(emptyList())
        CalendarRace(
            no = r.id.toString(),
            name = r.name.trim(),
            date = date,
            courses = courses.joinToString(", "),
            place = r.location.orEmpty().trim(),
            homepage = listOfNotNull(r.website, r.registrationUrl).firstOrNull { it.startsWith("http") } ?: URL,
        )
    }
}
