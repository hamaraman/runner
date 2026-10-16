package com.runner.app.ui.run

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.ui.NumberField
import com.runner.app.ui.TextInput
import com.runner.app.ui.rememberContainer
import com.runner.core.Shoe
import com.runner.core.totalKm
import java.util.UUID

/** 보통 600~800km에서 교체를 권한다. */
internal const val REPLACE_KM = 700.0

@Composable
fun ShoesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("러닝화") },
        text = { ShoesContent(Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 러닝화 관리 탭. */
@Composable
fun ShoesScreen() {
    ShoesContent(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp))
}

/** 러닝화 관리: 맨 위 현역 신발이 새 러닝에 자동으로 붙는다. */
@Composable
fun ShoesContent(modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val shoes by container.shoes.state.collectAsStateWithLifecycle()
    val runs by container.runs.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var baseKm by remember { mutableStateOf("") }
    var newPhoto by remember { mutableStateOf<String?>(null) }
    val current = shoes.firstOrNull { !it.retired }?.id

    // 사진 고르기: 대상이 null이면 새 신발 폼, 아니면 그 신발의 사진을 바꾼다. 권한 필요 없는 시스템 사진 선택기.
    val scope = rememberCoroutineScope()
    var pickFor by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val target = pickFor
        if (uri != null) scope.launch {
            val photo = withContext(Dispatchers.IO) { container.importShoePhoto(uri) } ?: return@launch
            if (target == null) { newPhoto?.let(container::discardShoePhoto); newPhoto = photo } else container.setShoePhoto(target, photo)
        }
    }
    fun pickPhoto(shoeId: String?) {
        pickFor = shoeId
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }
    // 사진만 고르고 추가하지 않은 채 닫으면 파일을 지운다.
    DisposableEffect(Unit) { onDispose { newPhoto?.let(container::discardShoePhoto) } }

            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shoes.forEach { shoe ->
                    val km = shoe.totalKm(runs)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = shoe.id == current, onClick = { container.selectShoe(shoe.id) }, enabled = !shoe.retired)
                        ShoePhoto(container.shoePhoto(shoe), 44.dp) { pickPhoto(shoe.id) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(shoe.name, color = if (shoe.retired) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                            Text(
                                "%.1f km".format(km) + if (!shoe.retired && km >= REPLACE_KM) " · 교체할 때가 됐어요" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!shoe.retired && km >= REPLACE_KM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            container.shoes.update { list -> list.map { if (it.id == shoe.id) it.copy(retired = !it.retired) else it } }
                        }) { Text(if (shoe.retired) "꺼내기" else "보관") }
                        IconButton(onClick = { container.deleteShoe(shoe.id) }) { Icon(Icons.Filled.Delete, "삭제") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShoePhoto(newPhoto?.let(container::photoFile), 56.dp) { pickPhoto(null) }
                    Spacer(Modifier.width(12.dp))
                    TextInput("새 러닝화", name, { name = it }, placeholder = "예: 페가수스 41")
                }
                NumberField("이미 달린 거리 (선택)", baseKm, { baseKm = it }, suffix = "km")
                TextButton(
                    onClick = {
                        val shoe = Shoe(UUID.randomUUID().toString(), name.trim(), baseKm.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0, photo = newPhoto)
                        container.shoes.update { listOf(shoe) + it }
                        name = ""
                        baseKm = ""
                        newPhoto = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text("추가") }
            }
}

/** 기록에 붙은 신발을 바꾸는 줄. 누르면 다음 신발(마지막 다음은 없음)로 넘어간다. */
@Composable
fun RunShoeRow(runId: String, shoeId: String?, modifier: Modifier = Modifier) {
    val container = rememberContainer()
    val shoes by container.shoes.state.collectAsStateWithLifecycle()
    if (shoes.isEmpty()) return
    val options = listOf<Shoe?>(null) + shoes
    val idx = options.indexOfFirst { it?.id == shoeId }.coerceAtLeast(0)
    Text(
        "러닝화: ${options[idx]?.name ?: "선택 안 함"}  (눌러서 변경)",
        modifier.clickable { container.setRunShoe(runId, options[(idx + 1) % options.size]?.id) }.padding(vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 러닝화 사진 썸네일. 사진이 없으면 사진 추가 아이콘. 누르면 사진을 고른다. */
@Composable
fun ShoePhoto(file: File?, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) Image(bmp, "러닝화 사진", Modifier.size(size), contentScale = ContentScale.Crop)
        else if (onClick != null) Icon(Icons.Filled.AddAPhoto, "사진 추가", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
