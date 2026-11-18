import { safeRequestFailure } from "../utils/safeRequestFailure";
import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import AxiosApi, { storeLoginTokens } from "../api/AxiosApi";
import ArchiveAuthFrame from "../components/redesign/ArchiveAuthFrame";

function Login() {
  const [inputId, setInputId] = useState("");
  const [inputPwd, setInputPwd] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const navigate = useNavigate();

  // 아이디와 비밀번호 입력 여부 확인
  const isValid = inputId.trim() !== "" && inputPwd !== "" && !isLoading;

  // 로그인 요청
  const onSubmit = async (event) => {
    event.preventDefault();

    if (!isValid) return;

    setIsLoading(true);

    try {
      const response = await AxiosApi.memberLogin(inputId.trim(), inputPwd);

      if (response.status !== 200 || !response.data) {
        alert("아이디 또는 비밀번호가 틀립니다.");
        return;
      }

      const loginId = response.data.loginId || inputId.trim();
      const nickname = response.data.nickname || "회원";
      const accessToken = response.data.accessToken;
      const refreshToken = response.data.refreshToken;

      // 토큰 발급 여부 확인
      if (!accessToken || !refreshToken) {
        alert("로그인 토큰을 발급받지 못했습니다.");
        return;
      }

      // 회원 정보 저장
      localStorage.setItem("username", loginId);
      localStorage.setItem("nickname", nickname);

      // Access Token / Refresh Token 저장
      // 내부에서 인증 상태 변경 이벤트도 발생시킨다.
      storeLoginTokens(accessToken, refreshToken);

      // 로그인 화면이 바뀌어도 알림이 유지되도록 전역 이벤트로 전달한다.
      window.dispatchEvent(
        new CustomEvent("momo-toast", {
          detail: { nickname },
        }),
      );

      navigate("/", { replace: true });
    } catch (error) {
      console.error("로그인 에러:", safeRequestFailure(error));

      const message =
        error.response?.data?.message ||
        "아이디 또는 비밀번호가 올바르지 않습니다.";

      alert(message);
    } finally {
      setIsLoading(false);
    }
  };

  return <ArchiveAuthFrame><main data-archive-page="login" className="signup-page">
    <section className="signup-wrapper"><section className="signup-card login-card">
      <div className="signup-card-heading"><h1>다시 만나서 반가워요.</h1><p>당신의 영화 기록이 기다리고 있어요.</p></div>
      <form onSubmit={onSubmit}>
        <div className="signup-field"><label htmlFor="loginId">아이디</label><input id="loginId" type="text" value={inputId} placeholder="아이디를 입력해 주세요." onChange={(event) => setInputId(event.target.value)} autoComplete="username" /></div>
        <div className="signup-field"><label htmlFor="loginPassword">비밀번호</label><input id="loginPassword" type="password" value={inputPwd} placeholder="비밀번호를 입력해 주세요." onChange={(event) => setInputPwd(event.target.value)} autoComplete="current-password" /></div>
        <button type="submit" className="signup-submit" disabled={!isValid}>{isLoading ? "로그인 중..." : "로그인"}</button>
      </form>
      <div className="signup-footer"><span>아직 계정이 없으신가요?</span><Link to="/signup">회원가입</Link></div>
    </section></section>
  </main></ArchiveAuthFrame>;
}

export default Login;
