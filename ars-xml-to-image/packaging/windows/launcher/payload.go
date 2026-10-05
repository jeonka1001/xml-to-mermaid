package main

// Standalone executables carry the whole application folder as a zip appended to the
// launcher binary, followed by a fixed-size trailer:
//
//	[launcher][zip payload][id: 32 bytes, space padded][payload offset: uint64 LE][magic: 8 bytes]
//
// On first run the payload is extracted to <user cache>/hx2/<id> (Windows: %LOCALAPPDATA%\hx2\<id>);
// later runs reuse it. The id changes with every build, so builds never share a folder.

import (
	"archive/zip"
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

const (
	payloadMagic   = "HXI2PAY1"
	payloadIDLen   = 32
	payloadTrailer = payloadIDLen + 8 + 8
	completeMarker = ".complete"
)

// standaloneAppDir returns the extracted application folder, or "" when exe has no payload.
func standaloneAppDir(exe string, progress io.Writer) (string, error) {
	f, err := os.Open(exe)
	if err != nil {
		return "", err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		return "", err
	}
	if info.Size() < payloadTrailer {
		return "", nil
	}
	trailer := make([]byte, payloadTrailer)
	if _, err := f.ReadAt(trailer, info.Size()-payloadTrailer); err != nil {
		return "", err
	}
	if string(trailer[payloadIDLen+8:]) != payloadMagic {
		return "", nil // plain launcher next to an extracted folder
	}
	id := strings.TrimSpace(string(trailer[:payloadIDLen]))
	offset := int64(binary.LittleEndian.Uint64(trailer[payloadIDLen : payloadIDLen+8]))
	size := info.Size() - payloadTrailer - offset
	if id == "" || strings.ContainsAny(id, `/\.:`) || offset <= 0 || size <= 0 {
		return "", errors.New("corrupt payload trailer")
	}

	root, err := cacheRoot()
	if err != nil {
		return "", err
	}
	dest := filepath.Join(root, id)
	if fileExists(filepath.Join(dest, completeMarker)) {
		return dest, nil
	}
	if err := os.MkdirAll(root, 0o755); err != nil {
		return "", err
	}
	// Extract into a private folder, then publish it with one rename. A concurrent first run
	// either wins the rename or finds the finished folder.
	if progress != nil {
		fmt.Fprintf(progress, "Preparing the bundled runtime (first run only): %s\n", dest)
	}
	tmp, err := os.MkdirTemp(root, id+".tmp-")
	if err != nil {
		return "", err
	}
	if err := extract(io.NewSectionReader(f, offset, size), size, tmp, progress); err != nil {
		os.RemoveAll(tmp)
		return "", fmt.Errorf("extracting runtime to %s: %w", root, err)
	}
	if err := os.WriteFile(filepath.Join(tmp, completeMarker), []byte(id+"\n"), 0o644); err != nil {
		os.RemoveAll(tmp)
		return "", err
	}
	if err := os.Rename(tmp, dest); err != nil {
		os.RemoveAll(tmp)
		if !fileExists(filepath.Join(dest, completeMarker)) {
			return "", fmt.Errorf("publishing runtime folder %s: %w", dest, err)
		}
	}
	return dest, nil
}

// cacheRoot: HX2_HOME, else <user cache dir>/hx2, else <temp>/hx2.
func cacheRoot() (string, error) {
	if home := os.Getenv("HX2_HOME"); home != "" {
		return filepath.Abs(home)
	}
	if dir, err := os.UserCacheDir(); err == nil {
		return filepath.Join(dir, "hx2"), nil
	}
	return filepath.Join(os.TempDir(), "hx2"), nil
}

func extract(r io.ReaderAt, size int64, dest string, progress io.Writer) error {
	zr, err := zip.NewReader(r, size)
	if err != nil {
		return err
	}
	base := filepath.Clean(dest) + string(os.PathSeparator)
	total := len(zr.File)
	for i, zf := range zr.File {
		target := filepath.Join(dest, filepath.FromSlash(zf.Name))
		// Reject entries that would land outside dest (zip slip: "../", absolute paths, drive letters).
		if !strings.HasPrefix(filepath.Clean(target)+string(os.PathSeparator), base) || filepath.IsAbs(zf.Name) ||
			strings.Contains(zf.Name, ":") {
			return fmt.Errorf("unsafe path in payload: %q", zf.Name)
		}
		if zf.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0o755); err != nil {
				return err
			}
			continue
		}
		if zf.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("symbolic link in payload: %q", zf.Name)
		}
		if err := writeEntry(zf, target); err != nil {
			return err
		}
		if progress != nil && (i+1)%5000 == 0 {
			fmt.Fprintf(progress, "  %d / %d files\n", i+1, total)
		}
	}
	return nil
}

func writeEntry(zf *zip.File, target string) error {
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return err
	}
	in, err := zf.Open()
	if err != nil {
		return err
	}
	defer in.Close()
	mode := zf.Mode().Perm() | 0o600 // keeps executable bits on macOS/Linux test builds
	out, err := os.OpenFile(target, os.O_CREATE|os.O_EXCL|os.O_WRONLY, mode)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		out.Close()
		return err
	}
	return out.Close()
}

func fileExists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}

// readLauncherProperties reads key=value lines (launcher.properties in the application folder).
func readLauncherProperties(path string) map[string]string {
	props := map[string]string{}
	data, err := os.ReadFile(path)
	if err != nil {
		return props
	}
	for _, line := range bytes.Split(data, []byte("\n")) {
		s := strings.TrimSpace(string(line))
		if s == "" || strings.HasPrefix(s, "#") {
			continue
		}
		if k, v, ok := strings.Cut(s, "="); ok {
			props[strings.TrimSpace(k)] = strings.TrimSpace(v)
		}
	}
	return props
}
