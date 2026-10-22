FROM golang:1.22 AS build
WORKDIR /src
COPY go.mod ./
COPY . .
RUN CGO_ENABLED=0 go build -o /sidecar ./cmd/sidecar

FROM mcr.microsoft.com/presidio-analyzer:latest
COPY --from=build /sidecar /usr/local/bin/pii-sidecar
