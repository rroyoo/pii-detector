package otlp

import (
	"context"
	"net"

	collogs "go.opentelemetry.io/proto/otlp/collector/logs/v1"
	"google.golang.org/grpc"

	"github.com/rroyoo/pii-detector/sidecar/internal/pipeline"
)

const maxGRPCMessage = 4 << 20

type grpcServer struct {
	collogs.UnimplementedLogsServiceServer
	pipe *pipeline.Pipeline
}

// Export handles OTLP/gRPC log exports; only string bodies are analyzed.
func (s *grpcServer) Export(ctx context.Context, req *collogs.ExportLogsServiceRequest) (*collogs.ExportLogsServiceResponse, error) {
	for _, rl := range req.GetResourceLogs() {
		service := "unknown"
		for _, a := range rl.GetResource().GetAttributes() {
			if a.GetKey() == "service.name" {
				service = a.GetValue().GetStringValue()
			}
		}
		for _, sl := range rl.GetScopeLogs() {
			for _, lr := range sl.GetLogRecords() {
				s.pipe.Handle(ctx, pipeline.Record{Service: service, Body: lr.GetBody().GetStringValue()})
			}
		}
	}
	return &collogs.ExportLogsServiceResponse{}, nil
}

// GRPCServer returns an OTLP/gRPC logs server backed by the same pipeline as the HTTP receiver.
func (rc *Receiver) GRPCServer() *grpc.Server {
	srv := grpc.NewServer(grpc.MaxRecvMsgSize(maxGRPCMessage))
	collogs.RegisterLogsServiceServer(srv, &grpcServer{pipe: rc.pipe})
	return srv
}

// ServeGRPC serves OTLP/gRPC on the listener until it is closed.
func (rc *Receiver) ServeGRPC(l net.Listener) error {
	return rc.GRPCServer().Serve(l)
}
