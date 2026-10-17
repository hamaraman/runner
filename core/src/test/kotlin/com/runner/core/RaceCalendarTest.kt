package com.runner.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RaceCalendarTest {
    @Test fun parsesJustrunApi() {
        val text = """{"races":[
            {"id":1,"name":" 송년 마라톤 ","date":"2026-12-28","location":"춘천","distances":"[\"풀\",\"하프\"]","website":"http://a.kr","status":"접수중"},
            {"id":2,"name":"신년 런","date":"2027-01-04","location":null,"distances":"[\"10km\",\"5km\"]","website":null,"registrationUrl":null},
            {"id":3,"name":"날짜 깨짐","date":"미정"}
        ],"total":3}"""
        val races = RaceCalendar.parse(text)
        assertEquals(2, races.size)
        assertEquals(CalendarRace("1", "송년 마라톤", LocalDate.of(2026, 12, 28), "풀, 하프", "춘천", "http://a.kr"), races[0])
        assertEquals(RaceCalendar.URL, races[1].homepage)
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
