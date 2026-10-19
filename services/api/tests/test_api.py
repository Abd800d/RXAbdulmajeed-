from fastapi.testclient import TestClient


def test_health_is_explicitly_degraded_without_cloud(client: TestClient) -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "degraded"
    assert response.json()["supabase"] == "unconfigured"
    assert response.headers["x-content-type-options"] == "nosniff"
    assert response.headers["x-frame-options"] == "DENY"
    assert response.headers["x-request-id"]


def test_search_validates_query_length(client: TestClient) -> None:
    response = client.get("/v1/catalog/search", params={"q": "a"})
    assert response.status_code == 422


def test_grounded_search_maps_catalog_result(client: TestClient) -> None:
    gateway = client.app.state.gateway
    gateway.search_rows = [
        {
            "drug": {
                "id": "amoxicillin",
                "generic_name": "Amoxicillin",
                "verification_status": "verified",
            },
            "matched_product": {"id": "amoxil", "brand_name": "Amoxil"},
            "score": 0.87,
            "matched_on": "product",
        }
    ]
    response = client.get("/v1/catalog/search", params={"q": "amoxcillin", "lang": "en"})
    assert response.status_code == 200
    payload = response.json()
    assert payload["results"][0]["ingredient"]["id"] == "amoxicillin"
    assert payload["results"][0]["product"]["brand_name"] == "Amoxil"
    assert payload["results"][0]["score"] == 0.87


def test_sync_cursor_is_monotonic(client: TestClient) -> None:
    gateway = client.app.state.gateway
    gateway.tables["catalog_change_feed"] = [
        {
            "sequence": 12,
            "entity_type": "drug",
            "entity_id": "paracetamol",
            "operation": "UPSERT",
            "entity_version": 3,
            "changed_at": "2026-10-19T00:00:00Z",
        },
        {
            "sequence": 13,
            "entity_type": "product",
            "entity_id": "panadol",
            "operation": "DELETE",
            "entity_version": 4,
            "changed_at": "2026-10-19T00:01:00Z",
        },
    ]
    response = client.get("/v1/sync/changes", params={"after": 11, "limit": 1})
    assert response.status_code == 200
    assert response.json()["next_sequence"] == 12
    assert response.json()["has_more"] is True


def test_assistant_refuses_to_answer_without_catalog_context(client: TestClient) -> None:
    response = client.post(
        "/v1/assistant/answer",
        json={"question": "What should I take?", "language": "en"},
        headers={"x-device-id": "00000000-0000-4000-8000-000000000001"},
    )
    assert response.status_code == 404
    assert "approved APH record" in response.json()["detail"]


def test_large_request_is_rejected_before_processing(client: TestClient) -> None:
    response = client.post(
        "/v1/assistant/answer",
        content=b"x",
        headers={"content-length": str(3 * 1024 * 1024), "content-type": "application/json"},
    )
    assert response.status_code == 413
