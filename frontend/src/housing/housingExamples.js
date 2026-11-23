// Fictional UI fixtures, loaded only after an explicit "예시로 둘러보기" action.
// Names, prices, schedules and competition ratios do not describe real properties.
import { koreaToday } from './housingModel.js'
export function housingExamples(today = koreaToday()) {
  const date = offset => new Date(Date.parse(today) + offset * 86400000).toISOString().slice(0, 10)
  const definitions = [
    ['seoul', '서울', '한눈에', '서울특별시 예시구 예시로', -1, 2, 240, 590000000, 820000000],
    ['gyeonggi', '경기', '내일숲', '경기도 예시시 예시로', 4, 7, 520, 380000000, 520000000],
    ['incheon', '인천', '바다빛', '인천광역시 예시구 예시로', -7, -4, 180, 320000000, 450000000],
    ['busan', '부산', '푸른마을', '부산광역시 예시구 예시로', 8, 10, 360, 420000000, 610000000],
    ['daejeon', '대전', '하루', '대전광역시 예시구 예시로', -2, 0, 120, 260000000, 390000000],
    ['gyeonggi-two', '경기', '햇살마루', '경기도 예시시 예시로', 12, 14, 310, null, null],
  ]
  return definitions.map(([id, region, name, address, start, end, units, min, max]) => ({
    id: `example-${id}`, name: `[예시] ${name} 아파트`, region, address: `[예시 주소] ${address}`,
    units, applicationStart: date(start), applicationEnd: date(end), announcementDate: date(start - 10),
    specialStart: date(start), specialEnd: date(start), generalStart: date(start + 1), generalEnd: date(end),
    winnerDate: date(end + 7), developer: '예시 시행사', noticeUrl: null,
    houseTypes: [{ name: '59㎡', units: Math.round(units * .4), priceMin: min, priceMax: min },
      { name: '84㎡', units: units - Math.round(units * .4), priceMin: max, priceMax: max }],
    competition: end < 0 ? [{ name: '59㎡', ratio: 12.4 }, { name: '84㎡', ratio: 25.8 }] : [],
    marketComparison: null,
  }))
}
