package com.seolhwa.armyrist

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    HomeScreen(
                        onCounting = { startActivity(Intent(this, MainActivity::class.java)) },
                        onChecklist = { startActivity(Intent(this, ChecklistActivity::class.java)) },
                        onPending = { Toast.makeText(this, "다음 패치에서 연결됩니다.", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onCounting: () -> Unit,
    onChecklist: () -> Unit,
    onPending: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("군 특화 도구", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("업무 도구", style = MaterialTheme.typography.titleMedium)

        HomeCard("실셈", "수량 기록 · 그룹 집계 · 결과 공유", onCounting)
        HomeCard("체크리스트", "반복 점검 · 상태 관리 · 진행 현황", onChecklist)
        HomeCard("시간계획", "시각과 경과시간을 빠르게 계산", onPending)

        Spacer(Modifier.height(8.dp))
        Text("공통 기능", style = MaterialTheme.typography.titleMedium)
        HomeCard("보고 양식 설정", "결과 공유용 보고 양식 관리", onPending)
        HomeCard("내 정보", "보고 양식에 사용할 사용자 이름", onPending)
    }
}

@Composable
private fun HomeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
