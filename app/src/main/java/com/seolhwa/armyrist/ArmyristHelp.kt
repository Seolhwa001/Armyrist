package com.seolhwa.armyrist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

enum class ArmyristHelpTopic(val title: String, val sections: List<Pair<String, String>>) {
    HOME("홈", listOf(
        "업무 도구" to "실셈, 체크리스트, 시간계획, 보고 양식, 핸디캡 상황을 열 수 있습니다. 업무 도구를 길게 누른 채 끌면 배치가 바뀌고 저장됩니다.",
        "시간계획 배너" to "현재 진행 중인 시간계획과 다음 미완료 실시사항을 보여줍니다. 설정 · 내 정보에서 연동할 계획과 배너 문구를 바꾸거나 숨길 수 있습니다.",
        "저장과 백업" to "작성 내용은 이 기기에 저장됩니다. 기기를 바꾸거나 앱을 삭제하기 전에는 데이터 관리에서 백업 범위를 확인하세요."
    )),
    COUNTING("실셈", listOf(
        "수량 기록" to "실셈표와 항목을 만든 뒤 수량을 늘리거나 줄이고 직접 입력할 수 있습니다. 항목별 단위와 메모도 설정할 수 있습니다.",
        "그룹과 집계" to "항목을 그룹으로 나누어 관리하고 계산·집계 결과를 확인하세요. 단위가 다른 항목은 구분하여 확인합니다.",
        "순서와 결과" to "항목을 길게 눌러 끌면 순서를 바꿀 수 있습니다. 결과 화면에서 내용을 확인하고 복사하거나 공유할 수 있습니다."
    )),
    CHECKLIST("체크리스트", listOf(
        "점검 기록" to "체크리스트에 항목을 추가하고 상태를 표시하세요. 항목을 길게 누르면 편집할 수 있습니다.",
        "그룹과 순서" to "그룹으로 항목을 정리하고 길게 눌러 순서를 변경할 수 있습니다. 반복 점검 전에는 초기화 범위를 확인하세요.",
        "알림" to "알림을 설정해 점검 시각을 안내받을 수 있습니다. 휴대전화의 알림 권한과 소리 설정이 꺼져 있으면 알림이 보이거나 울리지 않을 수 있습니다."
    )),
    TIMEPLAN("시간계획", listOf(
        "계획과 실시사항" to "계획의 시작·종료 및 중도 지점 시간을 정한 뒤 실시사항을 추가하세요. 실시사항의 시각과 알림 종류를 설정할 수 있습니다.",
        "수행 모드의 동그라미" to "수행 모드에서 실시사항의 동그라미를 누르면 완료·미완료가 바뀝니다. 누른 채 위아래로 끌면 지나간 실시사항들을 같은 상태로 바꿀 수 있습니다. 결과와 다음 미완료 실시사항에도 반영됩니다.",
        "알림에서 할 일 완료" to "알림을 펼쳐 ‘할 일 완료’를 누르면 해당 실시사항을 완료로 저장하고 동그라미에도 반영합니다. 앱을 열지 않아도 저장되며, 음악 알림도 함께 멈춥니다. 반복해서 눌러도 미완료로 돌아가지 않습니다.",
        "음악 알림 중지" to "‘알림 중지’는 소리만 끄며 실시사항을 완료 처리하지 않습니다. 완료 표시는 수행 모드에서 다시 변경할 수 있습니다.",
        "알림이 보이지 않을 때" to "휴대전화의 앱 알림 권한, 알림 채널, 배터리 제한을 확인하세요. 기기 설정에 따라 알림을 아래로 펼쳐야 동작 버튼이 보일 수 있습니다. D-day 상대 표시 계획은 실제 시각 알림을 사용하지 않습니다."
    )),
    REPORT("보고 양식", listOf(
        "양식 준비" to "자주 사용하는 보고 문구와 입력 항목을 양식으로 만들어 두세요. 필요한 양식을 선택해 내용을 작성합니다.",
        "내용 확인" to "보고 전에 날짜, 수치, 이름과 입력하지 않은 항목이 있는지 확인하세요.",
        "결과 사용" to "완성한 결과를 복사하거나 공유할 수 있습니다. 공유할 대상과 내용을 직접 확인한 뒤 전송하세요."
    )),
    HANDICAP("핸디캡 상황", listOf(
        "단어를 누르는 순서" to "부여제대와 블랙·화이트를 정한 뒤 누가·언제·어디서·무엇을·어떻게를 편한 순서로 선택하세요. ‘확인 · 문장 정리’를 누르면 문장이 정리됩니다.",
        "시각과 사용자 항목" to "‘현재시간’은 누른 순간의 시각을 기록합니다. ‘직접입력’으로 다른 시각을 적을 수 있습니다. ‘전체’에서 더 많은 단어를 보고, 설정에서 단어를 추가·수정·삭제할 수 있습니다.",
        "연락할 제대" to "블랙은 1차 상급제대, 화이트는 1차 하급제대로 안내합니다. 해당 1차 제대가 없으면 상황부여 제대로 안내합니다. 설정에서 제대명과 전화번호를 등록하세요.",
        "복사와 전화" to "결과 팝업에서 문장과 연락할 제대를 확인하고 복사할 수 있습니다. ‘전화 앱 열기’는 번호를 전화 앱에 표시하며 자동으로 발신하지 않습니다.",
        "초기화와 백업" to "초기화는 작성 중인 선택과 시각을 지우며 제대·단어 설정은 유지합니다. 핸디캡 설정과 작성 중 상황은 기존 전체 데이터 백업에 포함되지 않습니다."
    )),
    SETTINGS("설정 · 내 정보", listOf(
        "홈 배너" to "시간계획 연동, 배너 제목과 안내 문구를 바꾸고 표시 여부를 설정할 수 있습니다.",
        "앱 업데이트" to "앱 정보의 ‘업데이트 확인’으로 새 정식 버전을 확인합니다. 다운로드에는 인터넷 연결이 필요하며 설치 권한은 휴대전화에서 직접 허용해야 합니다.",
        "기기 변경" to "설정 변경과 기기 변경 전에 필요한 내용을 백업하세요. 검수용 미리보기 앱은 정식 앱과 별도로 설치되며 데이터가 자동 이전되지 않습니다."
    )),
    DATA("데이터 관리·공유", listOf(
        "백업" to "백업 종류와 포함 범위를 확인하고 파일을 안전한 곳에 보관하세요. 암호화 백업은 복원할 때 설정한 암호가 필요합니다.",
        "복원과 가져오기" to "전체 복원은 기존 데이터를 대체할 수 있으므로 적용 범위를 먼저 확인하세요. 개별 자료를 가져올 때는 표시되는 중복 처리 안내를 확인합니다.",
        "다른 기기로 전송" to "내보낸 파일이나 지원되는 기기 간 전송을 사용할 수 있습니다. 전송 전에 자료와 받는 기기를 확인하세요."
    ));

    companion object {
        fun forScreen(title: String, subtitle: String?): ArmyristHelpTopic {
            val text = "$title ${subtitle.orEmpty()}".uppercase()
            return when {
                "핸디캡" in text -> HANDICAP
                listOf("시간계획", "실시사항", "수행 모드", "TIMEPLAN", "TIME PLAN").any { it in text } -> TIMEPLAN
                "CHECKLIST" in text || "체크리스트" in text -> CHECKLIST
                "COUNTING" in text || "실셈" in text -> COUNTING
                "REPORT" in text || "보고 양식" in text -> REPORT
                listOf("데이터", "복원", "백업", "전송", "공유").any { it in text } -> DATA
                "내 정보" in text || "설정" in text -> SETTINGS
                else -> HOME
            }
        }
    }
}

@Composable
fun ArmyristHelpButton(topic: ArmyristHelpTopic) {
    var opened by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { opened = true }, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Outlined.HelpOutline, contentDescription = "도움말", modifier = Modifier.size(24.dp))
    }
    if (opened) ArmyristHelpDialog(topic) { opened = false }
}

@Composable
internal fun ArmyristHelpDialog(initialTopic: ArmyristHelpTopic, onDismiss: () -> Unit) {
    var topic by rememberSaveable(initialTopic) { mutableStateOf(initialTopic) }
    var allTopics by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (allTopics) "전체 도움말" else "${topic.title} 도움말") },
        text = {
            key(topic, allTopics) {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (allTopics) {
                        ArmyristHelpTopic.entries.forEach { entry ->
                            OutlinedButton(onClick = { topic = entry; allTopics = false }, modifier = Modifier.fillMaxWidth()) { Text(entry.title) }
                        }
                    } else topic.sections.forEach { (heading, body) ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ArmyristColors.PrimaryControl)
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        dismissButton = { TextButton(onClick = { if (allTopics) topic = initialTopic; allTopics = !allTopics }) { Text(if (allTopics) "현재 화면" else "전체 도움말") } }
    )
}
