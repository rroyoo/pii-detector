package otlp

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	collogs "go.opentelemetry.io/proto/otlp/collector/logs/v1"
	commonpb "go.opentelemetry.io/proto/otlp/common/v1"
	logspb "go.opentelemetry.io/proto/otlp/logs/v1"
	resourcepb "go.opentelemetry.io/proto/otlp/resource/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"

	"github.com/rroyoo/pii-detector/sidecar/internal/metrics"
	"github.com/rroyoo/pii-detector/sidecar/internal/pipeline"
	"github.com/rroyoo/pii-detector/sidecar/internal/presidio"
)

type fakeAnalyzer struct{}

func (fakeAnalyzer) Analyze(_ context.Context, text string) ([]presidio.Finding, error) {
	if strings.Contains(text, "secret") {
		return []presidio.Finding{{EntityType: "PERSON"}}, nil
	}
	return nil, nil
}

func newReceiver() (*Receiver, *metrics.Registry) {
	m := metrics.New()
	return NewReceiver(pipeline.New(fakeAnalyzer{}, m)), m
}

func scrape(m *metrics.Registry) string {
	rec := httptest.NewRecorder()
	m.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/metrics", nil))
	return rec.Body.String()
}

func TestGRPCExportCountsFindings(t *testing.T) {
	rc, m := newReceiver()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	srv := rc.GRPCServer()
	go srv.Serve(l)
	defer srv.Stop()

	conn, err := grpc.NewClient(l.Addr().String(), grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()

	str := func(s string) *commonpb.AnyValue {
		return &commonpb.AnyValue{Value: &commonpb.AnyValue_StringValue{StringValue: s}}
	}
	_, err = collogs.NewLogsServiceClient(conn).Export(context.Background(), &collogs.ExportLogsServiceRequest{
		ResourceLogs: []*logspb.ResourceLogs{{
			Resource: &resourcepb.Resource{Attributes: []*commonpb.KeyValue{{Key: "service.name", Value: str("svc")}}},
			ScopeLogs: []*logspb.ScopeLogs{{LogRecords: []*logspb.LogRecord{
				{Body: str("a secret value")}, {Body: str("nothing here")},
			}}},
		}},
	})
	if err != nil {
		t.Fatal(err)
	}

	out := scrape(m)
	for _, want := range []string{
		`pii_findings_total{service="svc",entity_type="PERSON"} 1`,
		"pii_logs_analyzed_total 2",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("metrics missing %q in:\n%s", want, out)
		}
	}
}

func TestHTTPExportCountsFindings(t *testing.T) {
	rc, m := newReceiver()
	body := `{"resourceLogs":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"svc"}}]},` +
		`"scopeLogs":[{"logRecords":[{"body":{"stringValue":"a secret value"}}]}]}]}`
	rec := httptest.NewRecorder()
	rc.Handler().ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/v1/logs", strings.NewReader(body)))
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d", rec.Code)
	}
	if out := scrape(m); !strings.Contains(out, `pii_findings_total{service="svc",entity_type="PERSON"} 1`) {
		t.Errorf("unexpected metrics:\n%s", out)
	}
}
