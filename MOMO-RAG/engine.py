"""임베딩 없이 문자 2-gram 겹침으로 검색하는 메모리 엔진."""
import re
from dataclasses import dataclass
from threading import RLock
from langchain_text_splitters import RecursiveCharacterTextSplitter
from settings import Settings


@dataclass(frozen=True)
class Chunk:
    review_id: int
    movie_code: str
    title: str
    text: str
    score: float = 0.0


def bigrams(text: str) -> set[str]:
    """공백·기호를 제외한 연속 두 글자를 검색 단위로 만든다."""
    text = re.sub(r"[^0-9a-z가-힣]", "", text.lower())
    return {text[i:i + 2] for i in range(len(text) - 1)} if len(text) > 1 else set(text)


class StubEngine:
    """동일 ID는 통째로 교체한다. 재시작 시 모든 색인이 사라진다."""
    name = "stub"

    def __init__(self, settings: Settings):
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=settings.chunk_size, chunk_overlap=settings.chunk_overlap,
            separators=["\n\n", "\n", ". ", " ", ""],
        )
        self.min_score = settings.stub_min_score
        self.store: dict[int, list[Chunk]] = {}
        self.lock = RLock()

    def index(self, review_id: int, movie_code: str, title: str, content: str) -> int:
        """제목을 각 청크에 붙이고 작성자 개인정보 없이 저장한다."""
        texts = self.splitter.split_text(content) or [""]
        chunks = [Chunk(review_id, movie_code, title, f"제목: {title}\n{text}") for text in texts]
        with self.lock:
            self.store[review_id] = chunks
        return len(chunks)

    def delete(self, review_id: int) -> int:
        """이미 없는 ID를 삭제하면 0을 반환한다."""
        with self.lock:
            return len(self.store.pop(review_id, []))

    def search(self, question: str, allowed_ids: list[int], k: int) -> list[Chunk]:
        """허용된 리뷰만 점수화한다. 호출자가 영화별 허용 ID를 결정해야 한다."""
        query = bigrams(question)
        if not query or not allowed_ids or k <= 0:
            return []
        with self.lock:
            candidates = [c for review_id in set(allowed_ids) for c in self.store.get(review_id, [])]
        result = []
        for c in candidates:
            score = len(query & bigrams(c.text)) / len(query)
            if score > 0 and score >= self.min_score:
                result.append(Chunk(c.review_id, c.movie_code, c.title, c.text, round(score, 3)))
        return sorted(result, key=lambda c: c.score, reverse=True)[:k]
