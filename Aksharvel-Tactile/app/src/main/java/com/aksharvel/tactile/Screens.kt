package com.aksharvel.tactile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aksharvel.tactile.BuildConfig

// ---------------------------------------------------------------- path (home)

private val Zigzag = listOf(0.dp, 46.dp, 20.dp, 60.dp)

@Composable
fun PathScreen(store: Store, onPlay: (Int) -> Unit) {
    val done = store.doneSet
    // Header shows the part of the first unfinished letter. Nothing is locked.
    val firstOpen = (0 until VARNAMALA.size).firstOrNull { it !in done } ?: 0
    val part = firstOpen / PART_SIZE
    val partLetters = VARNAMALA.drop(part * PART_SIZE).take(PART_SIZE)
    val doneInPart = (part * PART_SIZE until part * PART_SIZE + partLetters.size).count { it in done }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, firstOpen - 1))

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Mascot(56.dp)
            Spacer(Modifier.width(10.dp))
            Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_NAME}", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Pill("🔥 ${store.currentStreak()}", Navy, Yellow, fontSize = 20.sp)
        }
        Spacer(Modifier.height(14.dp))

        Chunky(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(100.dp),
            Purple, PurpleDark, RoundedCornerShape(28.dp), depth = 8.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    "भाग ${marathiNumber(part + 1)} · ${partLetters.joinToString(" ")}",
                    color = Color(0xFFE9E1FF), fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                ProgressBar(doneInPart.toFloat() / partLetters.size, Yellow, Color(0xFF9379E8))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            items(VARNAMALA.size) { i ->
                PathNode(i, i in done, onPlay)
            }
        }
    }
}

@Composable
private fun PathNode(i: Int, isDone: Boolean, onPlay: (Int) -> Unit) {
    val letter = VARNAMALA[i]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp + Zigzag[i % Zigzag.size], top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Chunky(
            Modifier.size(72.dp),
            if (isDone) Teal else Coral,
            if (isDone) TealDark else CoralDark,
            RoundedCornerShape(24.dp), depth = 8.dp,
            onClick = { onPlay(i) }
        ) {
            Text(
                letter,
                fontSize = if (letter.length > 1) 28.sp else 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(Modifier.width(14.dp))
        if (isDone) {
            Pill("👂 ✓", TealDark, TealTint, fontSize = 20.sp, onClick = { onPlay(i) })
        } else {
            Chunky(
                Modifier
                    .width(132.dp)
                    .height(58.dp),
                Yellow, YellowDark, RoundedCornerShape(20.dp), depth = 6.dp,
                onClick = { onPlay(i) }
            ) {
                Text("खेळा  →", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
        }
    }
}

// ---------------------------------------------------------------- bottom nav

@Composable
fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("🗺️ वाट", "✏️ लिहा", "🎤 बोला", "🎯 सराव", "🏅 मी")
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(SoftYellow),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (i == selected) Purple else Muted
                )
            }
        }
    }
}

// ---------------------------------------------------------------- practice tab

@Composable
fun PracticeScreen(store: Store, onRandom: () -> Unit, onLearned: () -> Unit) {
    val hasLearned = store.completed > 0
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascot(80.dp)
        Spacer(Modifier.height(20.dp))
        Text("सराव", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(28.dp))
        Chunky(
            Modifier
                .fillMaxWidth()
                .height(76.dp),
            Yellow, YellowDark, RoundedCornerShape(24.dp), onClick = onRandom
        ) {
            Text("🎲 कोणतीही अक्षरे", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        Spacer(Modifier.height(16.dp))
        Chunky(
            Modifier
                .fillMaxWidth()
                .height(76.dp),
            if (hasLearned) Teal else Lavender,
            if (hasLearned) TealDark else Color(0xFFD5D0EA),
            RoundedCornerShape(24.dp),
            enabled = hasLearned,
            onClick = onLearned
        ) {
            Text(
                "📚 शिकलेली अक्षरे", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = if (hasLearned) Color.White else LockedFg
            )
        }
        if (!hasLearned) {
            Spacer(Modifier.height(10.dp))
            Text(
                "एखादा धडा पूर्ण केल्यावर हे सुरू होईल.",
                fontSize = 16.sp, color = Muted, textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------- me tab

@Composable
fun ProfileScreen(store: Store) {
    var askReset by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Mascot(80.dp)
        Spacer(Modifier.height(16.dp))
        Text("मी", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(24.dp))
        StatCard("पूर्ण केलेली अक्षरे", "${store.completed} / ${VARNAMALA.size}")
        Spacer(Modifier.height(12.dp))
        StatCard("✏️ लिहिलेली अक्षरे", "${store.written} / ${VARNAMALA.size}")
        Spacer(Modifier.height(12.dp))
        StatCard("🔥 दिवसांची मालिका", store.currentStreak().toString())
        Spacer(Modifier.weight(1f))
        Text("अक्षरवेल · ${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_NAME}", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Pill("सुरुवातीपासून सुरू करा", Muted, Lavender, onClick = { askReset = true }, fontSize = 16.sp)
    }
    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("सगळी प्रगती पुसायची?") },
            text = { Text("पूर्ण केलेली सर्व अक्षरे पुन्हा नव्याने सुरू होतील.") },
            confirmButton = {
                TextButton(onClick = { store.reset(); askReset = false }) { Text("हो, पुसा") }
            },
            dismissButton = {
                TextButton(onClick = { askReset = false }) { Text("नाही") }
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SoftYellow)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Purple)
    }
}

// ---------------------------------------------------------------- lesson complete

@Composable
fun ResultScreen(letter: String?, firstTry: Int, total: Int, missed: List<String>, onNext: () -> Unit) {
    val accuracy = firstTry.toFloat() / total
    val stars = when {
        accuracy >= 0.8f -> "⭐⭐⭐"
        accuracy >= 0.5f -> "⭐⭐"
        else -> "⭐"
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("धडा पूर्ण! 🎉", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(20.dp))
            Chunky(Modifier.size(170.dp), Yellow, YellowDark, CircleShape, depth = 10.dp) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Cream),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (letter != null) {
                            Text(letter, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Gold)
                        }
                        Text(stars, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            SkillRow("👂 ऐकणे", firstTry, total, Teal, locked = false)
            Spacer(Modifier.height(14.dp))
            SkillRow("✏️ लिहिणे", 0, total, Purple, locked = true)
            Spacer(Modifier.height(14.dp))
            SkillRow("🎤 बोलणे", 0, total, Coral, locked = true)

            if (missed.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SoftYellow)
                        .padding(18.dp)
                ) {
                    Text("यांचा पुन्हा सराव", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        missed.forEach { m ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(m, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Navy)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Chunky(
            Modifier
                .fillMaxWidth()
                .height(72.dp),
            Teal, TealDark, RoundedCornerShape(28.dp), onClick = onNext
        ) {
            Text("पुढे चला", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun SkillRow(label: String, score: Int, total: Int, color: Color, locked: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = if (locked) LockedFg else Navy, modifier = Modifier.width(116.dp)
        )
        ProgressBar(
            if (locked) 0f else score.toFloat() / total,
            color, Lavender, Modifier.weight(1f), height = 20.dp
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (locked) "🔒" else "$score/$total",
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Muted,
            modifier = Modifier.width(48.dp), textAlign = TextAlign.End
        )
    }
}
