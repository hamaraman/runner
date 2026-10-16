package com.runner.app.ui.run

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runner.app.ui.DateField
import com.runner.app.ui.EmptyState
import com.runner.app.ui.NumberField
import com.runner.app.ui.SectionTitle
import com.runner.app.ui.SoftCard
import com.runner.app.ui.StatusPill
import com.runner.app.ui.TextInput
import com.runner.app.ui.epochDay
import com.runner.app.ui.rememberContainer
import com.runner.core.RunRecord
import com.runner.core.Shoe
import com.runner.core.totalKm
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 러닝화 관리 탭. 카드나 추가 버튼을 누르면 같은 자리에서 등록·수정 화면으로 바뀐다. */
@Composable
fun ShoesScreen() {
    var editing by remember { mutableStateOf<Shoe?>(null) }
    var adding by remember { mutableStateOf(false) }
    if (adding || editing != null) {
        val close = { adding = false; editing = null }
        BackHandler(onBack = close)
        ShoeEditScreen(editing, close)
    } else {
        ShoesList(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), onAdd = { adding = true }, onEdit = { editing = it })
    }
}

/** 러닝화 목록: 사용 중 / 보관함. 맨 위 사용 중 신발이 새 러닝에 자동으로 붙는다. 카드를 누르면 수정. */
@Composable
private fun ShoesList(modifier: Modifier, onAdd: () -> Unit, onEdit: (Shoe) -> Unit) {
    val container = rememberContainer()
    val shoes by container.shoes.state.collectAsStateWithLifecycle()
    val runs by container.runs.state.collectAsStateWithLifecycle()
    val current = shoes.firstOrNull { !it.retired }?.id
    val (active, retired) = shoes.partition { !it.retired }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("사용 중")
        if (active.isEmpty()) EmptyState("러닝화를 등록하면 달린 거리가 자동으로 쌓여요.")
        active.forEach { ShoeCard(it, runs, it.id == current, onSelect = { container.selectShoe(it.id) }) { onEdit(it) } }
        Button(onClick = onAdd, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("러닝화 추가")
        }
        if (retired.isNotEmpty()) {
            SectionTitle("보관함")
            retired.forEach { ShoeCard(it, runs, false, onSelect = null) { onEdit(it) } }
        }
    }
}

@Composable
private fun ShoeCard(shoe: Shoe, runs: List<RunRecord>, isCurrent: Boolean, onSelect: (() -> Unit)?, onClick: () -> Unit) {
    val container = rememberContainer()
    val km = shoe.totalKm(runs)
    val runCount = runs.count { it.shoeId == shoe.id }
    val worn = !shoe.retired && km >= shoe.limitKm
    val accent = if (worn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    SoftCard(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick), highlight = isCurrent) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ShoePhoto(container.shoePhoto(shoe), 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shoe.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (shoe.retired) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("신는 중", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Text(
                    listOfNotNull(
                        shoe.firstEpochDay?.let { "${LocalDate.now().toEpochDay() - it + 1}일째" },
                        "${runCount}회 러닝",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (km / shoe.limitKm).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "%.1f / %.0f km · ".format(km, shoe.limitKm) +
                        if (worn) "교체할 때가 됐어요" else "%.1f km 남음".format(shoe.limitKm - km),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (worn) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onSelect != null && !isCurrent) TextButton(onClick = onSelect) { Text("신기") }
        }
    }
}

/**
 * 러닝화 등록(shoe == null)·수정 전체 화면. 큰 사진 → 이름·초기 거리·누적 거리·첫 러닝 날짜·교체 기준 → 하단 버튼.
 * 사진은 저장할 때 반영하고, 저장하지 않고 닫으면 새로 고른 사진을 지운다.
 */
@Composable
private fun ShoeEditScreen(shoe: Shoe?, onDismiss: () -> Unit) {
    val container = rememberContainer()
    val runs by container.runs.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(shoe?.name.orEmpty()) }
    var baseKm by remember { mutableStateOf(shoe?.baseKm?.takeIf { it > 0 }?.let { "%.1f".format(it) }.orEmpty()) }
    var limitKm by remember { mutableStateOf("%.0f".format(shoe?.limitKm ?: DEFAULT_LIMIT_KM)) }
    var firstDay by remember { mutableStateOf(shoe?.firstEpochDay?.let(::epochDay) ?: if (shoe == null) LocalDate.now() else null) }
    var newPhoto by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val base = baseKm.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val appKm = shoe?.let { s -> runs.filter { it.shoeId == s.id }.sumOf { it.distanceM } / 1000 } ?: 0.0

    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val photo = withContext(Dispatchers.IO) { container.importShoePhoto(uri) } ?: return@launch
            newPhoto?.let(container::discardShoePhoto)
            newPhoto = photo
        }
    }
    DisposableEffect(Unit) { onDispose { if (!saved) newPhoto?.let(container::discardShoePhoto) } }

    fun save() {
        val limit = limitKm.toDoubleOrNull()?.takeIf { it > 0 } ?: DEFAULT_LIMIT_KM
        val first = firstDay?.toEpochDay()
        if (shoe == null) {
            val created = Shoe(UUID.randomUUID().toString(), name.trim(), base, photo = newPhoto, limitKm = limit, firstEpochDay = first)
            container.shoes.update { listOf(created) + it }
        } else {
            container.shoes.update { list ->
                list.map { if (it.id == shoe.id) it.copy(name = name.trim(), baseKm = base, limitKm = limit, firstEpochDay = first) else it }
            }
            newPhoto?.let { container.setShoePhoto(shoe.id, it) }
        }
        saved = true
        onDismiss()
    }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "닫기") }
                    Text(if (shoe == null) "러닝화 등록" else "러닝화 수정", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    if (shoe != null) {
                        TextButton(onClick = {
                            container.shoes.update { list -> list.map { if (it.id == shoe.id) it.copy(retired = !it.retired) else it } }
                            onDismiss()
                        }) { Text(if (shoe.retired) "꺼내기" else "보관") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "삭제", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 큰 사진 + 카메라 배지
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.clip(MaterialTheme.shapes.small).clickable { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) {
                            ShoePhoto(newPhoto?.let(container::photoFile) ?: shoe?.let(container::shoePhoto), 160.dp)
                            Box(
                                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.PhotoCamera, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                    }
                    LabeledField("러닝화 이름") {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= NAME_MAX) name = it },
                            placeholder = { Text("예: 페가수스 41") },
                            supportingText = { Text("${name.length}/$NAME_MAX", Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    LabeledField("초기 거리", "이 앱을 쓰기 전에 이미 달린 거리") {
                        NumberField("", baseKm, { baseKm = it }, suffix = "km")
                    }
                    LabeledField("누적 거리", "초기 거리 + 이 신발로 기록한 러닝") {
                        Text("%.1f km".format(base + appKm), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    LabeledField("첫 러닝 날짜") { DateField("", firstDay, { firstDay = it }) }
                    LabeledField("교체 기준 거리", "이 거리에 다다르면 교체를 알려드려요 (보통 600~800km)") {
                        NumberField("", limitKm, { limitKm = it }, suffix = "km")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = ::save,
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) { Text(if (shoe == null) "등록하기" else "저장하기") }
            }
        }

    if (confirmDelete && shoe != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("'${shoe.name}'을(를) 삭제할까요?") },
            text = { Text("기록은 남지만 이 신발과의 연결이 끊겨요. 더 신지 않는다면 보관을 추천해요.") },
            confirmButton = {
                TextButton(onClick = { container.deleteShoe(shoe.id); onDismiss() }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun LabeledField(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}

private const val NAME_MAX = 20

/** 보통 600~800km에서 교체를 권한다. */
private const val DEFAULT_LIMIT_KM = 700.0

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
