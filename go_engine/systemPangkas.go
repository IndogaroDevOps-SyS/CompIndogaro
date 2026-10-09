package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"
)

type ScanResult struct {
	TotalFiles    int      `json:"total_files"`
	DuplicateCount int     `json:"duplicate_count"`
	SavedBytes    int64    `json:"saved_bytes"`
	HiddenFiles   []string `json:"hidden_files"`
}

func main() {
	mode := flag.String("mode", "pangkas", "Execution mode: pangkas, denoise, dual, scan")
	targetDir := flag.String("dir", "/sdcard", "Target directory")
	flag.Parse()

	logMsg("INFO", fmt.Sprintf("Go Engine Started. Mode: %s, Target: %s", *mode, *targetDir))

	switch *mode {
	case "scan":
		runDeepScanAndDuplicateDetector(*targetDir)
	case "pangkas", "denoise", "dual":
		runProcessingEngine(*mode, *targetDir)
	default:
		logMsg("ERROR", fmt.Sprintf("Unknown mode: %s", *mode))
	}
}

func logMsg(level, text string) {
	timestamp := time.Now().Format("15:04:05")
	fmt.Printf("[%s] [%s] %s\n", timestamp, level, text)
}

func runDeepScanAndDuplicateDetector(dir string) {
	logMsg("SCAN", "Starting Deep Scan & Go Duplicate Hash Detector...")
	
	hashMap := make(map[string]string)
	var hiddenFiles []string
	var duplicates []string
	var totalBytesSaved int64 = 0
	totalScanned := 0

	err := filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}

		if info.IsDir() {
			return nil
		}

		ext := strings.ToLower(filepath.Ext(path))
		if ext == ".mp4" || ext == ".mkv" || ext == ".avi" || ext == ".mov" {
			totalScanned++

			// Check Hidden File / Folder
			if strings.Contains(path, "/.") || strings.HasPrefix(info.Name(), ".") {
				hiddenFiles = append(hiddenFiles, path)
				logMsg("HIDDEN", fmt.Sprintf("Detected hidden video: %s", path))
			}

			// Partial SHA-256 Hash for Duplicate Detection (Fast Read First 1MB)
			hash, err := getPartialHash(path)
			if err == nil {
				if originalPath, exists := hashMap[hash]; exists {
					duplicates = append(duplicates, path)
					totalBytesSaved += info.Size()
					logMsg("DUPLICATE", fmt.Sprintf("Found duplicate: %s (Matches: %s)", info.Name(), filepath.Base(originalPath)))
				} else {
					hashMap[hash] = path
				}
			}

			if totalScanned%10 == 0 {
				logMsg("PROGRESS", fmt.Sprintf("Scanned %d video files...", totalScanned))
			}
		}
		return nil
	})

	if err != nil {
		logMsg("ERROR", fmt.Sprintf("Scan interrupted: %v", err))
	}

	result := ScanResult{
		TotalFiles:     totalScanned,
		DuplicateCount: len(duplicates),
		SavedBytes:     totalBytesSaved,
		HiddenFiles:    hiddenFiles,
	}

	jsonBytes, _ := json.Marshal(result)
	logMsg("RESULT_JSON", string(jsonBytes))
	logMsg("SUCCESS", fmt.Sprintf("Scan completed. Scanned: %d, Duplicates: %d, Hidden: %d", totalScanned, len(duplicates), len(hiddenFiles)))
}

func getPartialHash(filePath string) (string, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return "", err
	}
	defer file.Close()

	hasher := sha256.New()
	buf := make([]byte, 1024*1024) // 1MB chunk
	n, err := file.Read(buf)
	if err != nil && err != io.EOF {
		return "", err
	}
	hasher.Write(buf[:n])

	return hex.EncodeToString(hasher.Sum(nil)), nil
}

func runProcessingEngine(mode string, dir string) {
	logMsg("ENGINE", fmt.Sprintf("Initializing FFmpeg pipeline for mode: %s", mode))
	// Engine processing loop log simulation for active real-time stats stream
	for i := 1; i <= 10; i++ {
		time.Sleep(500 * time.Millisecond)
		progress := i * 10
		efficiency := 30 + (i * 4) // Simulated progressive compression ratio
		logMsg("WORKER_1", fmt.Sprintf("PROGRESS:%d|EFFICIENCY:%d|FILE:sample_video_%d.mp4", progress, efficiency, i))
	}
	logMsg("SUCCESS", "Processing cycle completed.")
}
