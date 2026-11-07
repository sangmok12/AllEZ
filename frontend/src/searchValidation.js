export function searchInputError({ partTime, keyword, regionIds }) {
  if (partTime && keyword.trim().length > 20) return '알바 검색어는 20자 이하로 입력해 주세요.'
  if (!keyword.trim() && (!partTime || !regionIds.length)) {
    return partTime ? '검색어를 입력하거나 지역을 선택해 주세요.' : '검색어를 입력해 주세요.'
  }
  return ''
}
