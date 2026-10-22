FROM golang:1.22 AS build
WORKDIR /src
COPY . .
RUN CGO_ENABLED=0 go build -o /sidecar ./cmd/sidecar

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /sidecar /sidecar
EXPOSE 4318 9464
ENTRYPOINT ["/sidecar"]
