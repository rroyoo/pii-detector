// Package metrics exposes finding counters in Prometheus text format.
// Labels must never contain PII values: only entity type and service name.
package metrics

import (
	"fmt"
	"net/http"
	"sort"
	"sync"
)

type key struct{ service, entity string }

type Registry struct {
	mu       sync.Mutex
	findings map[key]uint64
	logs     uint64
	errors   uint64
}

func New() *Registry { return &Registry{findings: map[key]uint64{}} }

func (r *Registry) IncFinding(service, entity string) {
	r.mu.Lock()
	r.findings[key{service, entity}]++
	r.mu.Unlock()
}
func (r *Registry) IncLog()   { r.mu.Lock(); r.logs++; r.mu.Unlock() }
func (r *Registry) IncError() { r.mu.Lock(); r.errors++; r.mu.Unlock() }

func (r *Registry) ServeHTTP(w http.ResponseWriter, _ *http.Request) {
	r.mu.Lock()
	defer r.mu.Unlock()
	keys := make([]key, 0, len(r.findings))
	for k := range r.findings {
		keys = append(keys, k)
	}
	sort.Slice(keys, func(i, j int) bool {
		if keys[i].service != keys[j].service {
			return keys[i].service < keys[j].service
		}
		return keys[i].entity < keys[j].entity
	})
	w.Header().Set("Content-Type", "text/plain; version=0.0.4")
	fmt.Fprintln(w, "# TYPE pii_findings_total counter")
	for _, k := range keys {
		fmt.Fprintf(w, "pii_findings_total{service=%q,entity_type=%q} %d\n", k.service, k.entity, r.findings[k])
	}
	fmt.Fprintln(w, "# TYPE pii_logs_analyzed_total counter")
	fmt.Fprintf(w, "pii_logs_analyzed_total %d\n", r.logs)
	fmt.Fprintln(w, "# TYPE pii_analysis_errors_total counter")
	fmt.Fprintf(w, "pii_analysis_errors_total %d\n", r.errors)
}
