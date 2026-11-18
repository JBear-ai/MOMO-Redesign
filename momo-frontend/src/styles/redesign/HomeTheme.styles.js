import { css } from "styled-components";

// 두 실행 버튼의 색상·크기·상호작용을 같은 기준으로 맞추되 홈 밖에는 적용하지 않는다.
export const homeFloatingButtonStyle = css`
  box-sizing: border-box;
  position: fixed; right: 20px;
  display: grid; place-items: center;
  width: 56px; height: 56px; min-width: 56px;
  padding: 0; border: 0; border-radius: 50%;
  background: #775A48; color: #FFFFFF; cursor: pointer;
  &:hover { background: #604735; }
  &:focus-visible { outline: 3px solid #292929; outline-offset: 4px; }
`;

// 홈 컨테이너에만 적용하며, 펼침 속성이 있는 채팅 실행 버튼의 모양만 바꾼다.
// 상태·알림 DOM은 유지하고 아이콘 전용 표시에서도 읽지 않은 메시지 배지는 남긴다.
export const homeRedesignTheme = css`
  --momo-bg: #F5F1E8;
  --momo-surface: #FFFFFF;
  --momo-text: #292929;
  --momo-muted: #775A48;
  --momo-border: #DED6CB;
  --momo-note: #F1E7DB;
  --momo-note-border: #DED6CB;
  --momo-accent: #C66B4E;
  --momo-accent-ink: #8E402B;
  --momo-primary: #B45B40;
  --momo-primary-hover: #9E4932;
  --momo-radius: 14px;
  --momo-content-width: 1120px;
  --home-header-bg: #292929;
  --home-header-height: 88px;
  --home-header-padding: 0 28px;
  --home-nav-color: #E7DDD2;
  --home-nav-active: #FFFFFF;
  --home-logo-filter: grayscale(1) invert(1);
  --home-main-width: calc(100% - 64px);
  --home-main-max: var(--momo-content-width);
  --home-main-padding: 32px 0 64px;
  --home-footer-bg: #EEE6DA;
  --home-footer-padding: 28px max(32px, calc((100% - 1120px) / 2));

  background-color: var(--momo-bg);
  color: var(--momo-text);

  .momo-search-form {
    min-height: 42px;
    background: #3A3633;
    border-color: #665B53;
    border-radius: 8px;
  }
  .momo-search-input:focus-visible {
    outline: 2px solid #E2AE97;
    outline-offset: 4px;
    border-radius: 3px;
  }
  .momo-search-input, .momo-search-button { color: #FFFFFF; }
  .momo-search-input::placeholder { color: #D9CFC5; }
  .momo-search-button:hover { color: #F0C9B7; }

  .favorite-star.is-active {
    background: var(--momo-note);
    color: var(--momo-accent-ink);
    border-color: var(--momo-accent);
  }


  button[aria-expanded][aria-label="공용 채팅 열기"],
  button[aria-expanded][aria-label="채팅 닫기"] {
    ${homeFloatingButtonStyle}
    bottom: 20px;
    &::before { content: ""; width: 26px; height: 26px; background: url("data:image/svg+xml,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20viewBox%3D%220%200%2024%2024%22%20fill%3D%22none%22%20stroke%3D%22white%22%20stroke-width%3D%221.8%22%3E%3Cpath%20d%3D%22M6%204h12a3%203%200%200%201%203%203v7a3%203%200%200%201-3%203H9l-5%204v-4a3%203%200%200%201-3-3V7a3%203%200%200%201%203-3Z%22%2F%3E%3Cpath%20d%3D%22M7%2010h.01M12%2010h.01M17%2010h.01%22%20stroke-linecap%3D%22round%22%20stroke-width%3D%222.8%22%2F%3E%3C%2Fsvg%3E") center / contain no-repeat; }
    > span { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
    > span[aria-label^="새 메시지"] { top: -4px; right: -4px; width: auto; min-width: 20px; height: 20px; clip-path: none; overflow: visible; }
  }

  ::selection { background: #E6C2AF; color: #292929; }
  input, textarea { caret-color: var(--momo-accent); }
  a, button { text-underline-offset: 4px; }
`;
