package com.runner.core

import java.time.LocalDate

/** 마라톤온라인 일정표의 대회 한 줄. */
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

/**
 * roadrun.co.kr/schedule/list.php HTML을 파싱한다.
 * 날짜에 연도가 없어서, 목록이 날짜순인 점을 이용해 월이 줄어들면 다음 해로 넘긴다.
 * ponytail: 사이트 HTML이 바뀌면 깨진다. 그땐 빈 목록이 나오고 외부 링크로 볼 수 있다.
 */
object RaceCalendar {
    const val URL = "http://www.roadrun.co.kr/schedule/list.php"
    const val DETAIL_URL = "http://www.roadrun.co.kr/schedule/view.php?no="

    private val dateRe = Regex("""face="Arial, Helvetica, sans-serif">(\d{1,2})/(\d{1,2})</font>""")
    private val nameRe = Regex("""view\.php\?no=(\d+)[^>]*>([^<]+)</a><br><font size="2" color="#990000">([^<]*)""")
    private val placeRe = Regex("""<div align="center">\s*([^<]*?)\s*</div>""")
    private val homeRe = Regex("""<a href="(https?://[^"]+)" target="_new">""")

    /** 네트워크로 받아 파싱한다. 블로킹이라 IO 스레드에서 부를 것. 사이트가 EUC-KR이다. */
    fun fetch(today: LocalDate = LocalDate.now()): List<CalendarRace> {
        val conn = java.net.URL(URL).openConnection().apply { connectTimeout = 10_000; readTimeout = 15_000 }
        val html = conn.getInputStream().use { it.readBytes() }.toString(charset("EUC-KR"))
        return parse(html, today.year).filter { !it.date.isBefore(today) }
    }

    fun parse(html: String, startYear: Int): List<CalendarRace> {
        var year = startYear
        var lastMonth = 0
        return html.split("<tr>").mapNotNull { row ->
            val (m, d) = dateRe.find(row)?.destructured ?: return@mapNotNull null
            val (no, name, courses) = nameRe.find(row)?.destructured ?: return@mapNotNull null
            val month = m.toInt()
            if (month < lastMonth) year++
            lastMonth = month
            val date = runCatching { LocalDate.of(year, month, d.toInt()) }.getOrNull() ?: return@mapNotNull null
            CalendarRace(
                no = no,
                name = name.trim(),
                date = date,
                courses = courses.trim(),
                place = placeRe.findAll(row).map { it.groupValues[1] }.firstOrNull { it.isNotEmpty() && it.first() != '<' }.orEmpty(),
                homepage = homeRe.find(row)?.groupValues?.get(1) ?: DETAIL_URL + no,
            )
        }
    }
}
