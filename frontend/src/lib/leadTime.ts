// leadTimeMinutes는 "지금부터 남은 분"이라 시간이 흐르며 계속 줄어든다. predictedBreachAt이
// 지났는데 아직 EXPIRED 판정(1분 주기 스케줄러)이 나기 전인 짧은 구간에서는 음수가 될 수
// 있다 — 이건 버그가 아니라 정직한 과도 상태이므로 "-6분 후" 같은 문구 대신 "임박"으로 뭉갠다.
export function formatLeadTimeMinutes(minutes: number): string {
  return minutes <= 0 ? '임박' : `약 ${Math.round(minutes)}분 후`
}
