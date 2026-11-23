"""Spring 전용 색인 API. 기본 실행은 로컬 주소에 바인딩한다."""
import secrets
from contextlib import asynccontextmanager
from fastapi import Depends, FastAPI, Header, HTTPException, Path, Request
from fastapi.concurrency import run_in_threadpool
from engine import StubEngine
from graph import build_graph, run_graph
from schemas import IndexRequest, IndexResponse, DeleteResponse, QueryRequest, QueryResponse
from settings import Settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    """프로세스당 하나의 엔진을 생성해 요청 사이의 색인을 공유한다."""
    settings = Settings()
    app.state.settings = settings
    app.state.engine = StubEngine(settings)
    app.state.graph = build_graph(app.state.engine, settings)
    yield


app = FastAPI(title="MOMO Review RAG (Stub)", lifespan=lifespan)


def verify_token(request: Request, x_service_token: str = Header(default="")):
    """MOMO JWT와 별개인 서버 간 토큰을 비교하며 값은 기록하지 않는다."""
    expected = request.app.state.settings.service_token
    if not secrets.compare_digest(x_service_token.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="서비스 인증 실패")


@app.get("/health")
def health():
    return {"status": "OK"}


@app.post("/index", response_model=IndexResponse, dependencies=[Depends(verify_token)])
async def index(req: IndexRequest, request: Request):
    chunks = await run_in_threadpool(request.app.state.engine.index,
                                    req.review_id, req.movie_code, req.title, req.content)
    return IndexResponse(review_id=req.review_id, chunks=chunks)


@app.delete("/index/{board_id}", response_model=DeleteResponse, dependencies=[Depends(verify_token)])
async def delete(request: Request, board_id: int = Path(gt=0)):
    deleted = await run_in_threadpool(request.app.state.engine.delete, board_id)
    return DeleteResponse(review_id=board_id, deleted=deleted)


@app.post("/query", response_model=QueryResponse, dependencies=[Depends(verify_token)])
async def query(req: QueryRequest, request: Request):
    return await run_in_threadpool(run_graph, request.app.state.graph,
                                  req.question, req.board_ids, req.top_k)
