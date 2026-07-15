package main

import (
	"fmt"
	"os"

	hocon "github.com/o3co/go.hocon"
)

func main() {
	cfg, err := hocon.ParseFile(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, "ERROR:", err)
		os.Exit(1)
	}
	s, err := cfg.RenderJSONForTest()
	if err != nil {
		fmt.Fprintln(os.Stderr, "ERROR:", err)
		os.Exit(1)
	}
	fmt.Println(s)
}
