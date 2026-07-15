package main

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/go-akka/configuration"
	"github.com/go-akka/configuration/hocon"
)

// go-akka keeps every literal as a string internally; leaves are emitted as
// JSON strings — type fidelity (number/bool/null) is part of what the
// evaluation measures, so no adapter-side re-typing.
func toNative(v *hocon.HoconValue) interface{} {
	if v == nil {
		return nil
	}
	if v.IsObject() {
		obj := v.GetObject()
		m := map[string]interface{}{}
		for k, val := range obj.Items() {
			m[k] = toNative(val)
		}
		return m
	}
	if v.IsArray() {
		arr := v.GetArray()
		a := make([]interface{}, len(arr))
		for i, val := range arr {
			a[i] = toNative(val)
		}
		return a
	}
	s := v.GetString()
	if s == "null" {
		return nil
	}
	return s
}

func main() {
	defer func() {
		if r := recover(); r != nil {
			// go-akka signals parse errors via panic (no error returns)
			fmt.Fprintln(os.Stderr, "ERROR:", r)
			os.Exit(1)
		}
	}()
	cfg := configuration.LoadConfig(os.Args[1])
	b, err := json.Marshal(toNative(cfg.Root()))
	if err != nil {
		fmt.Fprintln(os.Stderr, "ERROR:", err)
		os.Exit(1)
	}
	fmt.Println(string(b))
}
