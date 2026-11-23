from pydantic import BaseModel, Field


class IndexRequest(BaseModel):
    review_id: int = Field(gt=0)
    movie_code: str = Field(min_length=1, max_length=20)
    title: str = Field(min_length=1, max_length=100)
    content: str = Field(min_length=1, max_length=2000)


class IndexResponse(BaseModel):
    review_id: int
    chunks: int


class DeleteResponse(BaseModel):
    review_id: int
    deleted: int


class QueryRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    # 예제 계약을 유지한다. MOMO에서는 리뷰 ID 허용 목록으로 사용한다.
    board_ids: list[int] = Field(default_factory=list)
    top_k: int = Field(default=5, ge=1, le=20)


class QueryResponse(BaseModel):
    answer: str
    abstained: bool
    sources: list = Field(default_factory=list)
    trace: list[str]
