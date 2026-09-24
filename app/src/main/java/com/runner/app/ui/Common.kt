@file:OptIn(ExperimentalMaterial3Api::class)

package com.runner.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.runner.app.RunnerApp
import com.runner.app.data.AppContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun rememberContainer(): AppContainer {
    val ctx = LocalContext.current
    return remember { (ctx.applicationContext as RunnerApp).container }
}

val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
val FullDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
val DateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)

fun epochDay(day: Long): LocalDate = LocalDate.ofEpochDay(day)
fun millisToLocal(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

fun dDay(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val d = ChronoUnit.DAYS.between(today, date)
    return when {
        d == 0L -> "D-DAY"
        d > 0 -> "D-$d"
        else -> "D+${-d}"
    }
}

fun openUrl(context: Context, url: String) {
    val fixed = if (url.startsWith("http")) url else "https://$url"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))
    } catch (e: Exception) {
        Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show()
    }
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "공유하기"))
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** 날짜 선택 버튼 + 머티리얼 달력 다이얼로그. */
@Composable
fun DateField(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { open = true }) { Text(date?.format(FullDateFmt) ?: "날짜 선택") }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("취소") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        suffix = { if (suffix.isNotEmpty()) Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}
