"""4차시 연결 위치. 이번에는 LangGraph나 실제 답변을 생성하지 않는다."""

def build_graph(engine, settings):
    return None


def run_graph(graph, question, board_ids, top_k):
    return {"answer": "질의 답변은 4차시 적용 후 제공됩니다.",
            "abstained": True, "sources": [], "trace": ["todo"]}
