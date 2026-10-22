// Package presidio adapts log text to the Presidio Analyzer REST API.
package presidio

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

type Finding struct {
	EntityType string  `json:"entity_type"`
	Start      int     `json:"start"`
	End        int     `json:"end"`
	Score      float64 `json:"score"`
}

type Analyzer interface {
	Analyze(ctx context.Context, text string) ([]Finding, error)
}

type Client struct {
	baseURL  string
	language string
	http     *http.Client
}

func NewClient(baseURL, language string) *Client {
	return &Client{baseURL: baseURL, language: language, http: &http.Client{Timeout: 5 * time.Second}}
}

// Analyze only detects; the text is never masked or stored.
func (c *Client) Analyze(ctx context.Context, text string) ([]Finding, error) {
	body, _ := json.Marshal(map[string]string{"text": text, "language": c.language})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/analyze", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("presidio analyze: status %d", resp.StatusCode)
	}
	var findings []Finding
	return findings, json.NewDecoder(resp.Body).Decode(&findings)
}
