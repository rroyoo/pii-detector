package main

import (
	"log"
	"net/http"
	"os"

	"github.com/rroyoo/pii-detector/sidecar/internal/metrics"
	"github.com/rroyoo/pii-detector/sidecar/internal/otlp"
	"github.com/rroyoo/pii-detector/sidecar/internal/pipeline"
	"github.com/rroyoo/pii-detector/sidecar/internal/presidio"
)

func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}

func main() {
	m := metrics.New()
	analyzer := presidio.NewClient(env("PRESIDIO_URL", "http://localhost:5002"), env("PRESIDIO_LANGUAGE", "en"))
	recv := otlp.NewReceiver(pipeline.New(analyzer, m))

	go func() {
		mux := http.NewServeMux()
		mux.Handle("/metrics", m)
		log.Fatal(http.ListenAndServe(env("METRICS_ADDR", ":9464"), mux))
	}()

	addr := env("OTLP_HTTP_ADDR", ":4318")
	log.Printf("OTLP/HTTP logs receiver on %s", addr)
	log.Fatal(http.ListenAndServe(addr, recv.Handler()))
}
