// Package pipeline connects received log records to the analyzer and metrics.
package pipeline

import (
	"context"

	"github.com/rroyoo/pii-detector/sidecar/internal/metrics"
	"github.com/rroyoo/pii-detector/sidecar/internal/presidio"
)

type Record struct {
	Service string
	Body    string
}

type Pipeline struct {
	analyzer presidio.Analyzer
	metrics  *metrics.Registry
}

func New(a presidio.Analyzer, m *metrics.Registry) *Pipeline {
	return &Pipeline{analyzer: a, metrics: m}
}

func (p *Pipeline) Handle(ctx context.Context, r Record) {
	p.metrics.IncLog()
	findings, err := p.analyzer.Analyze(ctx, r.Body)
	if err != nil {
		p.metrics.IncError()
		return
	}
	for _, f := range findings {
		p.metrics.IncFinding(r.Service, f.EntityType)
	}
}
