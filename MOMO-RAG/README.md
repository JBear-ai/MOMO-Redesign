# MOMO RAG — 1~4차시

리뷰를 청킹하고 문자 2-gram 겹침 비율로 검색하는 메모리 StubEngine이다.
임베딩·벡터 DB·LLM 답변은 구현하지 않는다.
4차시는 LangGraph로 검색·재작성·답변 보류를 분기하고, 검색된 리뷰의 일부를 모의 답변으로 반환한다.
로컬 `D:/Dev/Ai/rag-ai-service`의 수정본과 강사님 Spring RestClient 예제를 참고했다.
참고 저장소 자체는 수정하지 않았다.

## 환경과 실행

Python 3.14.5와 프로젝트 `.venv`를 사용한다. PyCharm에서 기존 인터프리터로
`MOMO-RAG/.venv/Scripts/python.exe`를 선택한다. Anaconda pip와 혼용하지 않는다.
1~3차시 환경에는 추가된 LangGraph를 설치해야 하므로 아래 requirements 설치를 실행한다.

저장소 루트의 PowerShell에서:

```powershell
.\MOMO-RAG\.venv\Scripts\python.exe -m pip install -r .\MOMO-RAG\requirements.txt
Set-Location MOMO-RAG
# .env.example을 참고해 추적하지 않는 .env에 SERVICE_TOKEN을 직접 설정한다.
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

FastAPI는 `SERVICE_TOKEN`이 없거나 비어 있으면 기동하지 않는다.
토큰 값을 코드·문서·로그에 기록하지 않는다. Spring 실행 환경에는 다음을 설정한다.
기존 `.env`와 실제 비밀값은 이번 작업에서 변경하지 않았다.

| 이름 | 의미 / 기본값 |
|---|---|
| `RAG_ENABLED` | 기본 false, 연동 시 true |
| `RAG_BASE_URL` | 기본 http://127.0.0.1:8000 |
| `RAG_SERVICE_TOKEN` | Python SERVICE_TOKEN과 같은 비밀값 |
| `RAG_CONNECT_TIMEOUT_MS` | 연결 제한, 기본 500ms |
| `RAG_READ_TIMEOUT_MS` | 읽기 제한, 기본 1000ms |

Spring의 환경변수 또는 기존 설정 import로 주입한다. 두 서비스는 MOMO JWT와 별개인
`X-Service-Token`으로 인증한다. React의 설정은 바꾸지 않는다.

## API와 구현 위치

- `GET /health`: 설정·엔진 초기화 후 공개 상태 응답.
- `POST /index`: 인증 필요. `review_id`, `movie_code`, `title`, `content` → `review_id`, `chunks`.
- `DELETE /index/{review_id}`: 인증 필요. 기존과 같은 URL이며 응답은 `review_id`, `deleted`.
- `POST /query`: 인증 필요. `question`, `review_ids`(리뷰 ID 허용 목록), `top_k`를 받는다. 기존 `board_ids` 입력도 호환하며 둘 다 있으면 `review_ids`가 우선한다.
- 질의 응답은 `answer`, `abstained`, `sources`, `trace`이다. 출처는 `review_id`, `title`, `snippet`, `score`만 포함하며 같은 리뷰는 최고 점수 청크 하나로 정리한다.
- 작성자 이메일·닉네임·MOMO JWT는 Python에 전달하지 않는다.
- `engine.py`: 청킹·동일 ID 교체·삭제·최소 점수 검색. 제목을 각 청크에 붙이고 허용 ID만 검색한다.
- `graph.py`: `retrieve → grade → generate / rewrite / abstain → format`을 실행한다. 재작성하면 `retrieve`로 돌아가며 `trace`에 실제 노드 순서가 누적된다.

Python 설정 `MAX_REWRITES`는 기본 1이며 0 또는 1만 허용한다. 최초 검색을 포함해 최대 2회 검색한다.
빈 허용 목록에서는 검색·재작성 없이 보류한다. `grade`는 엔진이 이미 적용한 점수 임계값을 다시 계산하지 않는다.
그래프 초기화 실패 시 색인·삭제는 계속 제공하고 `/query`는 503을 반환한다. 질의 실행 오류는 내부 내용 없이 500을 반환한다.

## Spring 연동과 실패 정책

`momo-backend/src/main/java/com/momo/backend/client/RagClient.java`에 HTTP·내부 DTO·타임아웃·커밋 후 콜백을 모았다.
`ReviewService`의 등록·수정·삭제가 스냅샷/ID를 넘긴다. DB 커밋 전·롤백 시 HTTP 호출은 없다.
트랜잭션 동기화 또는 실제 트랜잭션이 없는 단위 호출은 건너뛴다.
커밋 후 같은 요청 스레드에서 실행하므로 통신 대기만큼 응답 지연이 추가될 수 있다.
콜백 예외는 리뷰 ID·예외 유형만 경고로 남기고 기존 리뷰 응답에는 전파하지 않는다.
기본 비활성화로 기존 실행 환경을 유지한다. 토큰 없이 활성화하면 설정 오류로 기동을 거부한다.

## 가상 데이터·재현·정리

기존 MySQL 회원 및 약 3개 기존 리뷰는 초기 색인하거나 변경하지 않았다.
`test_rag.py`의 ID 910001~910005는 메모리 전용이다. 영화 20990001/20990002의 서로 다른 의견,
관계없는 리뷰, 수정·삭제와 허용 ID 필터를 같은 엔진에서 검증한다. 테스트 종료 시 폐기된다.

`RagLiveIntegrationTest`는 명시적으로 선택할 때 실제 두 HTTP 서버와 H2 메모리 DB를 실행한다.
전용 가상 회원 `rag_test_one`과 영화 20990001/20990002를 사용하며 각 영화에 리뷰 하나씩 작성한다.
같은 영화의 여러 리뷰를 가진 Python 테스트 자료는 DB unique 제약 대상이 아니다.
DB에서 같은 영화에 여러 리뷰가 필요하면 서로 다른 전용 계정을 만들어야 한다.
H2 스키마·회원·리뷰와 FastAPI 프로세스는 종료 시 폐기된다. 공유 MySQL 데이터 삽입은 별도 승인 대상이다.

```powershell
# 저장소 루트: Python 검증, DB 접근 없음
.\MOMO-RAG\.venv\Scripts\python.exe -B -m unittest discover -s MOMO-RAG -p 'test_*.py'

# 설치된 Java 21을 현재 셸에서 지정
$env:JAVA_HOME='C:\Users\admin\.jdks\corretto-21.0.12.1'
.\momo-backend\gradlew.bat -p momo-backend test --tests '*RagClientTest' --tests '*Review*Test'

# 실제 Spring HTTP ↔ FastAPI, H2 전용
$env:RAG_LIVE_TEST='1'
.\momo-backend\gradlew.bat -p momo-backend test --tests '*RagLiveIntegrationTest'
Remove-Item Env:RAG_LIVE_TEST
```

다른 OS는 `RAG_TEST_PYTHON`에 가상환경 Python 절대 경로를 전달할 수 있다.
FastAPI 접근 로그는 추적 제외된 `momo-backend/build/rag-live-fastapi.log`에 남는다.
테스트용 서비스 토큰은 매 실행 메모리에서 생성하며 헤더·리뷰 내용은 로그에 출력하지 않는다.

## 제한 및 후속 작업

- 단일 프로세스 메모리 색인이다. 재시작 시 사라지고 여러 worker 사이에 공유되지 않으므로 기본 1 worker를 사용한다.
- 실패·동시 수정의 전송 순서 역전·프로세스 종료에 대한 자동 복구나 최신성 보장은 없다.
- 회원 탈퇴 등 ReviewService를 거치지 않는 일괄 삭제는 색인에 반영하지 않는다.
- 전체 초기 색인·Spring 질의 API·React AI UI·Atlas·OpenAI는 구현하지 않았다.
- 후속 질의에서 Spring이 현재 영화의 허용 리뷰 ID를 계산해야 한다. movie_code 필드만으로 범위/권한이 자동 보장되지 않는다.

## 1~3차시 당시 실행 결과 (2026-11-23)

| 구분 | 결과 |
|---|---|
| Python unittest | PASS, 4개 (청킹·검색/영화별 필터/교체·삭제/lifespan·인증·HTTP 계약) |
| Python 의존성 | PASS, `pip check` |
| Spring 전체 테스트 | PASS 340, 실패 0, SKIP 14 / 총 354 |
| SKIP 구분 | 기존 폐기용 MySQL 조건 13개 + 기본 실행에서 제외되는 실제 FastAPI 통합 1개 |
| 실제 두 서버 통합 별도 실행 | PASS 1개, H2 전용. health·색인·수정 재색인·삭제·FastAPI 중단 후 리뷰 CRUD 성공 |
| RAG HTTP·트랜잭션 테스트 | PASS 6개, 위 전체 테스트에 포함. 커밋/롤백·동기화 없음·401·연결 실패·읽기 타임아웃 |
| 리뷰 연결 단위 테스트 | PASS 5개, 위 전체 테스트에 포함. CRUD·중복·소유권 |
| Spring bootJar | PASS |
| Git diff 공백 점검 | PASS |
| 공유 MySQL·실제 회원 데이터 테스트 | 미검증, 데이터 변경 승인 없이 실행하지 않음 |
| React 테스트·브라우저 확인 | 미검증, 프론트엔드 변경 없음 |
| LLM·임베딩·Atlas·LangGraph | 후속 작업 |

최초 전체 회귀에서 H1 인증 테스트의 새 의존성 등록 누락으로 4개가 실패했다.
해당 테스트에 모의 RagClient를 등록한 뒤 전체 재실행에서 실패 0을 확인했다.
실제 두 서버 통합은 전체 테스트와 별도로 실행했으므로 기본 SKIP과 구분한다.
실제 통신 중 FastAPI 접근 로그에서 `/health`와 `/index` 요청의 200 응답을 확인했다.
테스트 프로세스는 종료했으며 Commit·Push는 수행하지 않았다.
