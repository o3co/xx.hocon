package main

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/gurkankaymak/hocon"
)

func toNative(v hocon.Value) interface{} {
	switch t := v.(type) {
	case hocon.Object:
		m := map[string]interface{}{}
		for k, val := range t {
			m[k] = toNative(val)
		}
		return m
	case hocon.Array:
		a := make([]interface{}, len(t))
		for i, val := range t {
			a[i] = toNative(val)
		}
		return a
	case hocon.String:
		return string(t)
	case hocon.Int:
		return float64(t)
	case hocon.Float32:
		return float64(t)
	case hocon.Float64:
		return float64(t)
	case hocon.Boolean:
		return bool(t)
	case hocon.Null:
		return nil
	default:
		return fmt.Sprintf("<unrenderable:%T>", v)
	}
}

func main() {
	defer func() {
		if r := recover(); r != nil {
			fmt.Fprintln(os.Stderr, "CRASH:", r)
			os.Exit(2)
		}
	}()
	conf, err := hocon.ParseResource(os.Args[1])
	if err != nil {
		fmt.Fprintln(os.Stderr, "ERROR:", err)
		os.Exit(1)
	}
	b, _ := json.Marshal(toNative(conf.GetRoot()))
	fmt.Println(string(b))
}
