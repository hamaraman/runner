package com.runner.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RaceCalendarTest {
    private fun row(md: String, no: String, name: String, courses: String, home: String = "") = """
<tr>
   <td width="18%">
	<div align="center"><b><font size="4" face="Arial, Helvetica, sans-serif">$md</font></b><br><font color="#959595">(토)</font><br>
		</div>
   </td>
   <td width="29%"><b><font face="Arial, Helvetica, sans-serif" size="3"><a href="javascript:open_window('win', 'view.php?no=$no', 0, 0, 550, 700, 0, 0, 0, 1, 0)">$name</a><br><font size="2" color="#990000">$courses</font></font></b></td>
   <td width="19%">
	<div align="center">춘천시 엘리시안 강촌</div>
   </td>
   <td width="30%">
	<div align="right" valign="bottom">주최<br>
	 $home<a href="javascript:open_window('win', 'view.php?no=$no', 0, 0, 550, 700, 0, 0, 0, 1, 0)"><img src="image/info.gif" border=0></a></div>
   </td>
  </tr>
<tr><td colspan="4"><hr></td></tr>"""

    @Test fun parsesRowsAndRollsYear() {
        val html = row("12/28", "1", "송년 마라톤", "풀,하프", """<a href="http://a.kr" target="_new"><img></a>""") +
            row("1/4", "2", "신년 런", "10km,5km")
        val races = RaceCalendar.parse(html, 2026)
        assertEquals(2, races.size)
        assertEquals(CalendarRace("1", "송년 마라톤", LocalDate.of(2026, 12, 28), "풀,하프", "춘천시 엘리시안 강촌", "http://a.kr"), races[0])
        assertEquals(LocalDate.of(2027, 1, 4), races[1].date)
        assertEquals(RaceCalendar.DETAIL_URL + "2", races[1].homepage)
        assertEquals(RaceDistance.FULL, races[0].distance)
        assertEquals(RaceDistance.TEN_K, races[1].distance)
    }

    @Test fun distanceUsesLongestCourse() {
        fun d(courses: String) = CalendarRace("", "", LocalDate.of(2026, 1, 1), courses, "", "").distance
        assertEquals(RaceDistance.FULL, d("36K,22K,7K"))
        assertEquals(RaceDistance.FULL, d("100K,50K"))
        assertEquals(RaceDistance.HALF, d("22K,7K"))
        assertEquals(RaceDistance.HALF, d("15km,5km"))
        assertEquals(RaceDistance.TEN_K, d("10km,4.4km걷기"))
        assertEquals(RaceDistance.FIVE_K, d("5km,3km"))
        assertEquals(RaceDistance.TEN_K, d("기타"))
    }
}
