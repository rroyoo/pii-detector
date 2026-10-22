package main

import (
	"log"
	"net/http"
	"os"
	"strconv"

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

func envFloat(k string, d float64) float64 {
	v := os.Getenv(k)
	if v == "" {
		return d
	}
	f, err := strconv.ParseFloat(v, 64)
	if err != nil || f < 0 || f > 1 {
		log.Fatalf("%s must be a number between 0 and 1", k)
	}
	return f
}

func main() {
	m := metrics.New()
	analyzer := presidio.NewClient(env("PRESIDIO_URL", "http://localhost:3000"), env("PRESIDIO_LANGUAGE", "en"), envFloat("PRESIDIO_MIN_SCORE", 0.4))
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
