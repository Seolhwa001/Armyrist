Armyrist TimePlan Partial Duration Fix 024

대상:
app/src/main/java/com/seolhwa/armyrist/TimePlanActivity.kt

목적:
뒤쪽에 시각 미입력 중도 지점을 추가해도 이미 양쪽 시각이 확정된 앞 구간의
경과시간 표시가 '경과시간 입력'으로 되돌아가지 않도록 수정.

핵심:
- 구간 표시 계산을 전체 TimePlan 완성 여부와 분리
- 해당 구간 양 끝 시각만 있으면 경과시간 표시
- 다음 지점이 비어 있는 구간만 '경과시간 입력'
- 자정 통과 구간도 계산
