package com.runner.core

import java.time.Instant

/** 러닝 경로를 GPX 1.1로 변환. 구글 지도·Strava·Komoot 등 지도 앱에서 불러올 수 있다. */
object Gpx {
    fun from(run: RunRecord): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Runner" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("<trk><name>Run ${Instant.ofEpochMilli(run.startedAtMs)}</name>\n")
        // 일시정지 구간은 별도 trkseg로 나눠 선이 이어지지 않게 한다
        run.points.groupBy { it.segment }.values.forEach { seg ->
            append("<trkseg>\n")
            seg.forEach { append("""<trkpt lat="${it.lat}" lon="${it.lng}"><time>${Instant.ofEpochMilli(it.timeMs)}</time></trkpt>""").append('\n') }
            append("</trkseg>\n")
        }
        append("</trk></gpx>\n")
    }
}
