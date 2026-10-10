package com.runner.core

import java.net.URL
import java.nio.charset.Charset
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 실제 사이트를 긁어 파싱되는지 본다. 네트워크가 필요해서 LIVE=1 일 때만 돈다. */
class RaceCalendarLiveCheck {
    @Test fun parsesLiveSchedule() {
        assumeTrue(System.getenv("LIVE") == "1")
        val html = URL(RaceCalendar.URL).readBytes().toString(Charset.forName("EUC-KR"))
        val races = RaceCalendar.parse(html, LocalDate.now().year)
        println("parsed ${races.size} races")
        races.take(5).forEach { println(it) }
        assertTrue("no races parsed", races.isNotEmpty())
        assertTrue("names garbled", races.all { it.name.any { c -> c in '가'..'힣' || c.isLetterOrDigit() } })
    }
}
