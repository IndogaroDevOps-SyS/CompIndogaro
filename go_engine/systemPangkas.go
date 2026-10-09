package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync/atomic"
	"syscall"
)

type Stats struct {
	TotalProcessed int64
	TotalFailed    int64
	TotalSavedBytes int64
}

var (
	OutputDir = "/sdcard/ToolsC/hasil_jernih"
)

func main() {
	_ = os.MkdirAll(OutputDir, 0755)
	fmt.Println("🚀 Pangkas Go Native Engine Daemon Started")
	select {}
}

func shortenName(name string, maxLen int) string {
	if len(name) <= maxLen {
		return name
	}
	return name[:maxLen-3] + "..."
}

func processSingleVideoPangkas(workerID int, filePath string, stats *Stats) {
	infoStart, err := os.Stat(filePath)
	if err != nil {
		return
	}
	originalSize := infoStart.Size()
	fileName := filepath.Base(filePath)
	ext := filepath.Ext(filePath)
	baseWithoutExt := strings.TrimSuffix(fileName, ext)
	outPath := filepath.Join(OutputDir, baseWithoutExt+"_pangkas_jernih.mp4")

	args := []string{
		"-y", "-stats",
		"-i", filePath,
		"-map", "0:v:0", "-map", "0:a:0?",
		"-sn", "-dn", "-map_metadata", "-1", "-map_chapters", "-1",
		"-c:v", "libx265", "-crf", "24", "-preset", "ultrafast",
		"-pix_fmt", "yuv420p10le", "-tag:v", "hvc1",
		"-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
		"-c:a", "aac", "-b:a", "128k",
		"-movflags", "+faststart",
		outPath,
	}

	cmd := exec.Command("ffmpeg", args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}

	if err := cmd.Run(); err != nil {
		atomic.AddInt64(&stats.TotalFailed, 1)
		return
	}

	infoEnd, errStat := os.Stat(outPath)
	if errStat != nil || infoEnd.Size() == 0 {
		os.Remove(outPath)
		atomic.AddInt64(&stats.TotalFailed, 1)
		return
	}

	newSize := infoEnd.Size()
	saved := originalSize - newSize
	origMB := float64(originalSize) / (1024 * 1024)
	newMB := float64(newSize) / (1024 * 1024)
	ratio := (float64(saved) / float64(originalSize)) * 100

	os.Remove(filePath)

	atomic.AddInt64(&stats.TotalProcessed, 1)
	atomic.AddInt64(&stats.TotalSavedBytes, saved)

	fmt.Printf("🎉 [Pangkas] %s | %.1fMB ➔ %.1fMB (-%.1f%%)\n", shortenName(fileName, 20), origMB, newMB, ratio)
}
