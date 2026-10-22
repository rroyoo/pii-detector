package presidio

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestAnalyzeSendsLanguageAndScoreThreshold(t *testing.T) {
	var got map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
			t.Fatal(err)
		}
		w.Write([]byte(`[{"entity_type":"CREDIT_CARD","start":0,"end":16,"score":1}]`))
	}))
	defer srv.Close()

	findings, err := NewClient(srv.URL, "es", 0.4).Analyze(context.Background(), "4242424242424242")
	if err != nil {
		t.Fatal(err)
	}
	if len(findings) != 1 || findings[0].EntityType != "CREDIT_CARD" {
		t.Fatalf("unexpected findings: %+v", findings)
	}
	if got["language"] != "es" || got["score_threshold"] != 0.4 {
		t.Fatalf("unexpected request: %+v", got)
	}
}

func TestAnalyzeOmitsScoreThresholdWhenZero(t *testing.T) {
	var got map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewDecoder(r.Body).Decode(&got)
		w.Write([]byte(`[]`))
	}))
	defer srv.Close()

	if _, err := NewClient(srv.URL, "en", 0).Analyze(context.Background(), "x"); err != nil {
		t.Fatal(err)
	}
	if _, ok := got["score_threshold"]; ok {
		t.Fatalf("score_threshold should be omitted: %+v", got)
	}
}
