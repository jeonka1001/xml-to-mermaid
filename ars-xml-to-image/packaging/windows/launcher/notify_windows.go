//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

// showError displays a message box; a windowsgui executable has no console.
func showError(message string) {
	const mbIconError = 0x10
	text, _ := syscall.UTF16PtrFromString(message)
	title, _ := syscall.UTF16PtrFromString("HansolXmlToImage2")
	syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW").Call(
		0, uintptr(unsafe.Pointer(text)), uintptr(unsafe.Pointer(title)), mbIconError)
}

// freeConsole detaches from the console window opened for a double-clicked console executable.
func freeConsole() {
	syscall.NewLazyDLL("kernel32.dll").NewProc("FreeConsole").Call()
}
