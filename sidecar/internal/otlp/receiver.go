// Package otlp implements OTLP logs receivers: HTTP (JSON encoding) and gRPC.
package otlp

import (
	"encoding/json"
	"net/http"

	"github.com/rroyoo/pii-detector/sidecar/internal/pipeline"
)

type anyValue struct {
	StringValue string `json:"stringValue"`
}

type attribute struct {
	Key   string   `json:"key"`
	Value anyValue `json:"value"`
}

type exportRequest struct {
	ResourceLogs []struct {
		Resource struct {
			Attributes []attribute `json:"attributes"`
		} `json:"resource"`
		ScopeLogs []struct {
			LogRecords []struct {
				Body anyValue `json:"body"`
			} `json:"logRecords"`
		} `json:"scopeLogs"`
	} `json:"resourceLogs"`
}

type Receiver struct{ pipe *pipeline.Pipeline }

func NewReceiver(p *pipeline.Pipeline) *Receiver { return &Receiver{pipe: p} }

func (rc *Receiver) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /v1/logs", rc.logs)
	return mux
}

func (rc *Receiver) logs(w http.ResponseWriter, r *http.Request) {
	var req exportRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4<<20)).Decode(&req); err != nil {
		http.Error(w, "invalid OTLP JSON", http.StatusBadRequest)
		return
	}
	for _, rl := range req.ResourceLogs {
		service := "unknown"
		for _, a := range rl.Resource.Attributes {
			if a.Key == "service.name" {
				service = a.Value.StringValue
			}
		}
		for _, sl := range rl.ScopeLogs {
			for _, lr := range sl.LogRecords {
				rc.pipe.Handle(r.Context(), pipeline.Record{Service: service, Body: lr.Body.StringValue})
			}
		}
	}
	w.Header().Set("Content-Type", "application/json")
	w.Write([]byte("{}"))
}
