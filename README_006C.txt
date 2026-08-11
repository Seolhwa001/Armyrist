Armyrist Product Polish 006C — Build Fix

원인:
- 006B에서 '미지정' 버튼을 GroupPickerDialog가 아니라 ItemDialog 내부에도 잘못 삽입함.
- ItemDialog의 done 콜백은 ItemDraft?를 요구하므로 String "__UNASSIGNED__" 전달에서 Kotlin 컴파일 실패.

수정:
- ItemDialog의 잘못된 미지정 TextButton 제거
- 실제 GroupPickerDialog에 미지정 항목 추가
- 미지정 선택 시 기존 assignment flow를 통해 groupId = null 처리
- 006B의 나머지 UI 개선은 유지

변경 파일:
- MainActivity.kt 1개
