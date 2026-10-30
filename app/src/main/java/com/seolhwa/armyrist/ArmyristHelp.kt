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
        "간결 모드 배치" to "간결 모드의 도구 버튼을 길게 누른 채 좌우·위아래로 밀어 위치를 바꾸고 손을 떼면 저장됩니다. 화면 읽기 도구에서는 앞으로·뒤로 이동 동작도 사용할 수 있습니다.",
        "업무 도구" to "실셈, 체크리스트, 시간계획, 보고 양식, 핸디캡 상황을 열 수 있습니다. 업무 도구를 길게 누른 채 끌면 배치가 바뀌고 저장됩니다.",
        "달력" to "홈 달력에서 날짜별 일정을 확인하세요. 달력 접기 상태가 유지되며 설정 · 내 정보의 홈 배너 설정에서 달력 위젯을 독립적으로 켜고 끌 수 있습니다.",
        "홈 간결 모드" to "설정 · 내 정보의 홈 배너 설정에서 홈 간결 모드를 켜면 월 달력 대신 오늘 일정 요약과 여섯 도구를 한 화면에 표시합니다. 도구를 길게 누르면 배치를 변경할 수 있습니다.",
        "시간계획 배너" to "현재 진행 중인 시간계획과 다음 미완료 실시사항을 보여줍니다. 설정 · 내 정보에서 연동할 계획과 배너 문구를 바꾸거나 숨길 수 있습니다.",
        "저장과 백업" to "작성 내용은 이 기기에 저장됩니다. 기기를 바꾸거나 앱을 삭제하기 전에는 데이터 관리에서 백업 범위를 확인하세요."
    )),
    CALENDAR("달력", listOf(
        "월 이동" to "달력을 왼쪽으로 밀면 다음 달, 오른쪽으로 밀면 이전 달로 이동합니다. 화살표와 오늘 버튼도 사용할 수 있습니다.",
        "휴대폰 바탕화면 위젯" to "달력 우측 위 위젯 추가를 누르거나, 홈 화면을 길게 누르고 위젯에서 낄롬리스 달력을 선택하세요. 1×1 날짜형과 4×4 달력형을 추가한 뒤 길게 눌러 크기를 조절합니다. 넓으면 월 달력, 세로로 늘리면 스크롤 가능한 일정 목록을 함께 표시합니다. 위젯은 화살표로 월 이동하며 날짜·항목을 누르면 앱에서 해당 날짜를 엽니다. 4×12 같은 큰 크기는 런처가 해당 격자를 지원해야 합니다.",
        "통합 일정" to "날짜를 누르면 시간계획·일정·실시사항·체크리스트를 함께 볼 수 있습니다. 실시사항은 자체 시각으로 관리되며 달력에서는 해당 시각이 포함된 일정 아래에 같은 색으로 표시합니다. 일정 밖의 실시사항은 따로 표시합니다. 일정 편집에서 표시 색상을 바꿀 수 있습니다. D-Day 상대 계획은 제외됩니다.",
        "추가와 편집" to "일정 추가에서 시간계획·실시사항·체크리스트를 선택하세요. 실시사항은 연결할 시간계획과 시각을 정하고 필요한 경우 소요 시간을 입력합니다. 기존 항목을 누르면 해당 편집 화면으로 이동합니다.",
        "커버 이미지" to "달력 전체 커버와 날짜별 대표이미지를 따로 선택하거나 제거할 수 있습니다. 날짜별 이미지는 해당 날짜와 홈 달력에 표시됩니다. 달력 이미지는 이 기기에 저장되며 데이터 백업에는 포함되지 않습니다."
    )),
    COUNTING("실셈", listOf(
        "수량 기록" to "실셈표와 항목을 만든 뒤 수량을 늘리거나 줄이고 직접 입력할 수 있습니다. 항목별 단위와 메모도 설정할 수 있습니다.",
        "그룹과 집계" to "항목을 그룹으로 나누어 관리하고 계산·집계 결과를 확인하세요. 단위가 다른 항목은 구분하여 확인합니다.",
        "순서와 결과" to "항목을 길게 눌러 끌면 순서를 바꿀 수 있습니다. 결과 화면에서 내용을 확인하고 복사하거나 공유할 수 있습니다."
    )),
    CHECKLIST("체크리스트", listOf(
        "점검 기록" to "체크리스트에 항목을 추가하고 상태를 표시하세요. 항목을 길게 누르면 편집할 수 있습니다.",
        "그룹과 순서" to "그룹으로 항목을 정리하고 길게 눌러 순서를 변경할 수 있습니다. 반복 점검 전에는 초기화 범위를 확인하세요.",
        "알림에서 완료" to "간단한 알림과 음악 알림을 선택할 수 있습니다. 알림의 ‘할 일 완료’는 해당 항목을 완료로 저장하고, ‘알림 중지’는 소리만 멈춥니다. 음악 알림은 중지하거나 완료할 때까지 반복됩니다.",
        "알림" to "날짜·시간 지정을 켜고 달력과 시계로 입력하세요. 알림을 끄더라도 날짜가 지정된 항목은 달력에 표시됩니다. 날짜 없는 기존 알림은 오늘 기준으로 동작합니다. 휴대전화의 알림 권한과 소리 설정이 꺼져 있으면 알림이 보이거나 울리지 않을 수 있습니다."
    )),
    TIMEPLAN("시간계획", listOf(
        "목록 일괄 선택·삭제" to "시간계획 목록의 제목을 길게 누르고 위아래로 밀면 지나간 계획을 연속 선택합니다. 이미 선택된 제목에서 시작하면 연속 해제합니다. 화면 끝에서 계속 밀면 스크롤합니다. 하단 휴지통 버튼으로 선택한 계획과 하위 일정·실시사항을 함께 보관하고 휴지통에서 복구할 수 있습니다. 왼쪽 점 손잡이는 순서 변경용입니다.",
        "시간축과 점" to "목록과 시간표 버튼으로 표시 방식을 전환합니다. 시간표는 각 항목의 시각과 소요 시간을 막대로 표시하며 이름은 시작 부분에 한 번 표시합니다. 기간 밖의 항목도 표시하고 겹치는 항목은 별도 열로 나눕니다. 좌우로 이동해 확인하세요. 항목을 누르면 편집합니다.",
        "밀어서 요약" to "일정과 실시사항을 하나의 실시사항 목록으로 통합했습니다. 목록과 시간표에서 같은 항목을 바로 편집하며 별도 요약 창을 열 필요가 없습니다.",
        "끌어서 선택·휴지통" to "목록이나 시간표를 길게 누른 채 위아래로 밀면 지나간 실시사항을 선택합니다. 가장자리에서 계속 끌면 자동 스크롤됩니다. 하단 휴지통으로 선택한 항목만 이동하며 계획 시작·종료와 다른 항목은 유지됩니다. 고정된 항목은 먼저 고정을 해제하세요.",
        "선택 항목 복원" to "시간계획 목록의 휴지통에서 선택 항목을 원래 계획으로 복원합니다. 삭제 뒤 수정한 제목·메모·다른 항목은 덮어쓰지 않습니다. 원래 계획을 삭제했다면 그 계획부터 복원해야 합니다.",
        "계획과 실시사항" to "시간계획 기간을 정한 뒤 + 실시사항을 누르세요. 내용과 시각만 입력해 저장하거나 15분·30분·1시간 또는 직접 입력으로 소요 시간을 정할 수 있습니다. 메모·색상·알림은 추가 설정에서 바꿉니다. 기존 일정도 같은 화면에서 편집할 수 있습니다.",
        "일정 옆 실시사항" to "목록에서는 항목의 시각·시간·메모를 함께 표시합니다. 계획 메모도 화면 아래에 바로 표시하며 편집 버튼으로 내용을 바꿀 수 있습니다.",
        "실시사항 모드의 동그라미" to "목록의 체크 상자를 누르면 완료·미완료가 바뀝니다. 시간표에서는 항목을 눌러 편집 화면의 완료 체크를 바꾸세요. 알림의 할 일 완료 버튼과 결과에도 같은 상태가 반영됩니다.",
        "알림에서 할 일 완료" to "알림을 펼쳐 ‘할 일 완료’를 누르면 해당 실시사항을 완료로 저장하고 동그라미에도 반영합니다. 앱을 열지 않아도 저장되며, 음악 알림도 함께 멈춥니다. 반복해서 눌러도 미완료로 돌아가지 않습니다.",
        "음악 알림 중지" to "‘알림 중지’는 소리만 끄며 실시사항을 완료 처리하지 않습니다. 완료 표시는 실시사항 모드에서 다시 변경할 수 있습니다.",
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
        "백업" to "전체 백업에는 시간계획 등 자료 폴더의 이름·소속 자료·순서·커버 이미지도 포함됩니다. 이전 버전에서 만든 백업에 없던 폴더 정보는 복구할 수 없습니다. 백업 파일을 안전한 곳에 보관하세요. 암호화 백업은 복원할 때 설정한 암호가 필요합니다.",
        "복원과 가져오기" to "전체 복원은 기존 데이터를 대체할 수 있으므로 적용 범위를 먼저 확인하세요. 개별 자료를 가져올 때는 표시되는 중복 처리 안내를 확인합니다.",
        "다른 기기로 전송" to "내보낸 파일이나 지원되는 기기 간 전송을 사용할 수 있습니다. 전송 전에 자료와 받는 기기를 확인하세요."
    ));

    companion object {
        fun forScreen(title: String, subtitle: String?): ArmyristHelpTopic {
            val text = "$title ${subtitle.orEmpty()}".uppercase()
            return when {
                "달력" in text -> CALENDAR
                "핸디캡" in text -> HANDICAP
                listOf("시간계획", "실시사항", "실시사항 모드", "TIMEPLAN", "TIME PLAN").any { it in text } -> TIMEPLAN
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
    ArmyristDialog(
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
