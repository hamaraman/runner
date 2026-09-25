@file:OptIn(ExperimentalMaterial3Api::class)

package com.runner.app.ui.run

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.ui.rememberContainer

/** 지금까지 달린 모든 경로를 한 지도에 겹쳐 보여준다. */
@Composable
fun AllRoutesScreen(onBack: () -> Unit) {
    val runs by rememberContainer().runs.state.collectAsStateWithLifecycle()
    // 기록마다 segment 번호를 겹치지 않게 바꿔서, 서로 다른 러닝끼리 선이 이어지지 않게 한다
    val points = remember(runs) {
        runs.flatMapIndexed { i, run -> run.points.map { it.copy(segment = i * 100_000 + it.segment) } }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("전체 경로 (${runs.size}회)") },
            windowInsets = WindowInsets(0),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
        )
        RouteMap(points, Modifier.fillMaxSize(), markers = false)
    }
}
