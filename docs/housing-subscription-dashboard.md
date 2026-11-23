# AllEZ 청약정보 — 1차 개발

## 작업 기준과 범위

- 시작 당시 `/workspace/AllEZ`는 변경사항 없이 `main`의 `7f9cd87`이었다.
- 원격 `feature/housing-subscription-dashboard`를 fetch하여 확인했다. 동일한 `7f9cd87`을 가리키며 main과 diff가 없었다. 구현된 기존 청약 작업은 없었다.
- 별도 `feature/housing-dashboard-phase1` 브랜치에서 개발했다. 기존 청약 브랜치를 덮어쓰지 않는다.
- 기존 채용·알바 검색/광고/관리자/백엔드/DB/배포 설정/환경변수는 수정하지 않는다. 신규 패키지 설치도 없다.

## 구현한 화면

- 상단 세 번째 `청약정보` 탭. 기존 단일 행 헤더를 320px부터 유지한다.
- `/?view=housing` 직접 접근·새로고침, 메뉴 간 이동·브라우저 뒤로가기 지원. 기존 `/admin` 유지.
- 전체·접수 중·모집 예정·접수 마감 현황 카드와 상태 바로 필터.
- 지역 17개/전국, 단지명, 모집 상태, 분양가 상한의 조합 필터와 초기화.
- 지역별 공고 건수와 지역 바로 선택.
- 접수 시작일 오름차순/내림차순, 접수 마감일순 정렬. 일정 미공개는 끝으로 정렬.
- 단지 카드: 주소, 공급 규모, 분양가 범위, 접수 기간, 한국 날짜 기준 D-day.
- 상세 펼치기: 모집공고일, 특별·일반공급, 당첨자 발표, 주택형별 공급·가격, 공개된 경쟁률, 공식 모집공고 링크.
- 접수 마지막 날까지 `접수 중`으로 처리. 매분 한국 날짜를 확인해 자정 경계를 반영한다. 이것은 API 자동 갱신과는 별개다.
- 실제 정보 미연결, 로딩, API 오류+재시도, 정상 0건, 조건에 맞는 결과 없음 상태를 구분한다.
- 공식 정보 미연결/오류 시 가상의 공고로 자동 대체하지 않는다. 통계 숫자도 미연결이면 `—`로 표시한다.
- `예시로 둘러보기`를 직접 눌러야 가상 UI 예시를 표시한다. 상단 배너, 카드 이름 `[예시]`, 주소·UI 예시 표기를 유지한다. 예시를 닫거나 페이지를 새로 열면 실제 정보 화면으로 돌아온다.
- 예시 날짜는 확인하는 한국 날짜를 기준으로 접수 중·예정·마감을 재현한다. 실제 청약 일정과 무관하다.
- 분양가 미공개를 0원으로 취급하지 않으며, 가격 상한 필터에서는 제외한다. 공개된 주택형 중 상한 이하 가격이 있는 단지를 표시한다.
- 새로운 페이지 JS/CSS는 청약 탭 접근 시에만 가져온다(React lazy).

## 변경 파일

| 파일 | 역할 |
| --- | --- |
| `frontend/src/App.jsx` | 신규 메뉴, URL·히스토리, 신규 페이지 지연 로딩 |
| `frontend/src/App.css` | 청약 테마 및 세 탭 모바일 헤더 |
| `frontend/src/HousingSubscriptions.jsx` | 대시보드, 필터, 단지 카드, 상세, 상태 안내 |
| `frontend/src/HousingSubscriptions.css` | 페이지에 한정한 카드·필터·반응형 스타일 |
| `frontend/src/housing/housingModel.js` | 날짜·상태·D-day·가격·검색·정렬·공식 링크 검사 |
| `frontend/src/housing/housingData.js` | 2차 서버 응답을 위한 읽기 전용 어댑터 |
| `frontend/src/housing/housingExamples.js` | 명시적으로 선택한 경우에만 표시할 가상 UI 예시 |
| `frontend/src/housingModel.test.js` | 날짜 경계, 필터·정렬, 가격 미공개, API 상태, 링크 검사 |
| `docs/housing-subscription-dashboard.md` | 구현 범위, API 조사, 확인·배포 방법 |

## 공식 API 조사 결과와 한계

확인한 공식 출처:

- 청약홈: <https://www.applyhome.co.kr/> — HTTP 200 및 `한국부동산원 주택청약` 제목 확인.
- 공공데이터포털 청약홈 분양정보 상세조회 서비스 안내: <https://www.data.go.kr/data/15098547/openapi.do> — 이번 환경에서는 503 연결 시간 초과. 포털의 현재 문서·이용 조건은 활용 신청 시 재확인해야 한다.
- 분양정보 조회 후보: `https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancDetail`
- 주택형 조회 후보: `https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancMdl`
- 경쟁률 조회 후보: `https://api.odcloud.kr/api/ApplyhomeInfoCmpetRtSvc/v1/getAPTLttotPblancCmpet`

위 세 API 경로에 `page=1&perPage=1`로 키 없이 요청했을 때 HTTP 401과 `인증키는 필수 항목 입니다.` 응답을 확인했다. 이는 인증 게이트 응답을 확인한 것이며, 정상 키로 개별 경로의 실제 데이터·필드·쿼터를 검증했다는 뜻은 아니다. 인증키가 없으므로 실제 공고는 한 건도 저장하거나 실제 정보처럼 표시하지 않았다. API 문서 자동 조회 경로 역시 404여서 실제 필드 정의는 2차 연동 때 확인해야 한다.

### 2차에서 필요한 항목

1. 공공데이터포털에서 필요한 청약홈 서비스별 활용 신청 및 승인/키 확보. 경쟁률 서비스도 별도 신청이 필요한지 확인한다.
2. 서버 전용 환경변수 `APPLYHOME_SERVICE_KEY`(제안 이름)를 설정한다. **1차에서는 이 변수 자체가 필요 없으며 어떤 기존 변수도 변경하지 않는다.** `VITE_` 변수나 프런트엔드 소스·URL에 키를 넣지 않는다.
3. 승인된 API의 실제 응답으로 단지·공고 식별자, 지역·일정, 주택형, 경쟁률을 연결한다. 주택형 가격이 만원 단위면 서버에서 원 단위로 변환한다. 특별공급·일반공급 세부 일정을 확인한 뒤 접수 전체 범위를 정한다.
4. 서버에서 페이지 전체 조회, 외부 오류 제한·재시도, 캐시, 수집 시각, 공식 API 일별 호출 한도를 관리한다.
5. 갱신 주기와 스케줄러는 승인된 실제 쿼터 확인 후 결정한다. 1차에서는 API 폴링이나 예약 작업을 추가하지 않았다.
6. 주변 시세 비교는 별도 공신력 있는 가격 데이터와 정확한 단지 매칭이 확보된 다음 진행한다. 현재는 미연결로 안내한다.

## 2차용 프런트엔드 응답 계약

브라우저는 기존 `apiUrl()` 기반으로 `GET /api/housing-subscriptions`만 읽는다. 공식 API를 브라우저에서 직접 호출하지 않는다. **현재 백엔드에는 이 API가 구현되지 않았다.**

- 404/501: 공식 정보 미연결 안내.
- 다른 HTTP 오류, 잘못된 응답, 네트워크 오류/15초 초과: 오류 및 재시도. 예시로 자동 대체하지 않는다.
- 200 + `{ "items": [] }`: 정상 조회 0건.
- 200 + 아래 정규화된 데이터: 실제 정보 카드와 필터. 잘못된 필수 필드·중복 ID는 오류로 처리한다.
- UI에 표시하지 않는 API 필드는 브라우저에 전송할 필요가 없다. 인증키는 절대 응답에 포함하지 않는다.

```ts
{
  fetchedAt?: string; // ISO timestamp, 향후 수집 시각 표시용
  items: Array<{
    id: string; // 공고 기준 고유 ID
    name: string;
    region: string; // 서울, 경기 등 REGIONS의 짧은 표기
    address: string;
    units?: number | null;
    applicationStart?: string | null; // YYYY-MM-DD, 한국 날짜
    applicationEnd?: string | null;
    announcementDate?: string | null;
    specialStart?: string | null;
    specialEnd?: string | null;
    generalStart?: string | null;
    generalEnd?: string | null;
    winnerDate?: string | null;
    developer?: string | null;
    noticeUrl?: string | null; // HTTPS 공식 모집공고 URL
    houseTypes: Array<{
      name: string;
      units?: number | null;
      priceMin?: number | null; // 원, 주택 총액
      priceMax?: number | null;
    }>;
    competition?: Array<{ name: string; ratio?: number | null }>;
  }>;
}
```

공식 공고 링크는 `https` 및 `applyhome.co.kr`, `data.go.kr`, `lh.or.kr`의 본·하위 도메인만 허용한다. 예시 공고는 링크를 만들지 않는다. 향후 실제 공식 첨부파일 호스트가 다르면 소유권 확인 후 허용 목록을 수정한다.

## 확인·빌드·배포

로컬:

```sh
cd frontend
npm ci
npm run dev
# http://localhost:5173/?view=housing
npm run lint
node --test src/*.test.js
npm run build
```

현재 API 미구현이므로 로컬에서 기존 백엔드까지 실행하면 404 미연결 안내가 나온다. 백엔드 없이 Vite 프록시가 연결 실패하면 오류 화면에서 `예시로 둘러보기`를 눌러 UI를 확인할 수 있다. 예시는 실제 서비스 데이터가 아니다.

검증:

- 프런트엔드 기존 18개 + 신규 5개 단위 테스트.
- Playwright: 320/390/768/1280px에서 기본 미연결 상태, 예시 명시적 진입·종료, 지역·단지명·상태·가격 조합 필터, 초기화, 일정 정렬, 상세 경쟁률, API 오류·재시도·정상 0건, 기존 메뉴 이동·뒤로가기, 가로 넘침 및 JS 오류 확인.
- 기존 빌드의 500kB 번들 경고는 남아 있다. 청약 페이지는 별도 JS/CSS 청크로 분리한다.

기능 브랜치의 PR에서 Vercel Preview를 열어 확인한다. 운영에 반영하려면 이 PR을 main에 병합하면 기존 Vercel 설정으로 프런트엔드가 배포된다. **1차만 배포하면 실제 공고 대신 미연결 안내와 선택 가능한 예시 화면이 나온다.** 백엔드 배포, 새로운 환경변수, DB 마이그레이션은 필요 없다. 실제 API와 자동 갱신은 2차 범위다.
