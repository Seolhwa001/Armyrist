Armyrist Product Polish Patch 006B

요청 반영:
1. 실셈 그룹 생성/편집
   - 체크리스트 계열과 유사한 팝업형 Modal
   - 그룹명/색상/합계 표시 유지

2. 실셈 그룹 지정
   - '미지정' 추가
   - 선택 후 확인 시 해당 항목 groupId = null

3. 빈 목록 화면 통일
   - 체크리스트 / 시간계획 Empty State를 실셈과 동일한 Light Work Surface 계열로 통일

4. 각 도구 새 문서 버튼 확대
   - 새 실셈
   - 새 체크리스트
   - 새 시간계획
   - 새 양식
   - 모두 Extended FAB + 최소 높이 58dp + Armyrist PrimaryControl

5. 보고 양식 목록 색상 통일
   - Pink 계열 기본 Card 제거
   - RaisedSurface + Armyrist Border 적용

변경 파일:
- MainActivity.kt
- ChecklistActivity.kt
- TimePlanActivity.kt
- ReportTemplateActivity.kt
