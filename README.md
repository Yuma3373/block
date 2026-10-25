# Shorts & Reels Blocker

유튜브 **쇼츠**와 인스타그램 **릴스**를 숨기고 차단하는 브라우저 확장 프로그램입니다.
Chrome · Edge · 네이버 웨일 · Firefox(PC / Android)에서 동작합니다.

## 하는 일

| | 숨기기 | 링크로 직접 들어갔을 때 |
|---|---|---|
| YouTube | 홈·구독·검색·채널의 쇼츠 선반과 영상, 사이드바 "Shorts" 메뉴, 검색 필터·채널 탭 | 차단 화면 / 홈으로 / 일반 영상으로 보기 |
| Instagram | 내비게이션 "릴스" 메뉴, 피드의 릴스 게시물, 탐색·프로필 그리드의 릴스, 프로필 릴스 탭 | 차단 화면 / 홈으로 |

툴바의 확장 아이콘을 누르면 사이트별로 켜고 끄거나 동작을 바꿀 수 있습니다. 설정은 브라우저 계정으로 동기화됩니다.

## 설치

### Chrome / Edge / 웨일
1. 이 저장소를 내려받습니다 (`Code → Download ZIP` 후 압축 해제).
2. 주소창에 `chrome://extensions` 입력 (Edge는 `edge://extensions`, 웨일은 `whale://extensions`).
3. 오른쪽 위 **개발자 모드**를 켭니다.
4. **압축해제된 확장 프로그램을 로드합니다**를 누르고, `manifest.json`이 있는 폴더를 선택합니다.

### Firefox
1. 주소창에 `about:debugging#/runtime/this-firefox` 입력.
2. **임시 부가 기능 로드**를 누르고 `manifest.json`을 선택합니다.
   (임시 로드는 브라우저를 재시작하면 사라집니다. 계속 쓰려면 [AMO](https://addons.mozilla.org/developers/)에서 서명받아야 합니다.)

## 참고

- 휴대폰의 **유튜브·인스타그램 앱**은 브라우저 확장으로 막을 수 없습니다. 휴대폰에서는 앱을 지우고
  Firefox for Android(또는 확장을 지원하는 브라우저)에서 웹으로 접속하면 이 확장이 적용됩니다.
- YouTube와 Instagram은 화면 구조를 자주 바꿉니다. 일부가 다시 보이기 시작하면
  `src/youtube.css`, `src/instagram.css`의 선택자를 고치면 됩니다.

## 구조

```
manifest.json        확장 설정 (Manifest V3)
src/settings.js      기본 설정과 불러오기
src/guard.js         URL 감시 → 차단 화면/리다이렉트, 숨김 클래스 토글
src/youtube.*        유튜브 쇼츠 규칙
src/instagram.*      인스타그램 릴스 규칙
popup/               툴바 팝업 (설정 화면)
blocked/             차단 화면
```
