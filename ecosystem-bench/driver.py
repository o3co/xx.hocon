#!/usr/bin/env python3
"""Cross-language HOCON conformance driver.
Runs each library adapter over the xx.hocon success fixtures (Lightbend-generated
expected JSON), normalizes output, and scores PASS / MISMATCH / ERROR / CRASH.
Splits results into two fairness layers: Lightbend upstream mirror vs o3co custom.
"""
import json, os, subprocess, sys, collections

HB = os.path.dirname(os.path.abspath(__file__))
# Repo root defaults to the parent of ecosystem-bench/; override with XX_ROOT.
XX = os.environ.get("XX_ROOT", os.path.dirname(HB))
EXP = os.path.join(XX, "expected", "hocon")
CONF = os.path.join(XX, "testdata", "hocon")

# Adapter registry: name -> (lang, version, cmd builder(conf_path) -> (argv, cwd, env))
def go_bin(name):
    return lambda p: ([os.path.join(HB, "go-adapters", "bin", name), p], HB, None)
def ts_ad(name):
    return lambda p: (["node", os.path.join(HB, "ts-adapters", "adapters", name + ".mjs"), p], os.path.join(HB, "ts-adapters"), None)
def rust_bin(name):
    return lambda p: ([os.path.join(HB, "rust-adapters", "target", "release", name), p], HB, None)
def py_ad(name):
    venv = os.path.join(HB, "py-adapters", "venv", "bin", "python3")
    return lambda p: ([venv, os.path.join(HB, "py-adapters", "adapters", name + "_adapter.py"), p], HB, None)
def ruby_ad():
    gh = os.path.join(HB, "ruby-adapters", ".gems")
    env = dict(os.environ, GEM_HOME=gh)
    return lambda p: (["ruby", os.path.join(HB, "ruby-adapters", "adapter.rb"), p], HB, env)

LIBS = [
    ("go",     "o3co/go.hocon",            "1.8.0",   go_bin("o3co")),
    ("go",     "gurkankaymak/hocon",       "1.2.23",  go_bin("gurkan")),
    ("go",     "go-akka/configuration",    "20200606","goakka" ) ,
    ("ts",     "@o3co/ts.hocon",           "1.8.0",   ts_ad("o3co")),
    ("ts",     "hocon-parser",             "1.0.1",   ts_ad("hocon-parser")),
    ("ts",     "@pushcorn/hocon-parser",   "1.3.1",   ts_ad("pushcorn")),
    ("rust",   "hocon-parser (o3co)",      "1.8.0",   rust_bin("o3co")),
    ("rust",   "hocon (mockersf)",         "0.9.0",   rust_bin("mockersf")),
    ("rust",   "hocon-rs (mikai233)",      "0.1.3",   rust_bin("mikai")),
    ("python", "pyhocon",                  "0.3.63",  py_ad("pyhocon")),
    ("python", "hocon",                    "0.3.0",   py_ad("hocon")),
    ("ruby",   "hocon (puppetlabs)",       "1.4.0",   ruby_ad()),
]
# fix goakka tuple (needs builder)
LIBS[2] = ("go", "go-akka/configuration", "20200606", go_bin("goakka"))

def norm(v):
    """Canonicalize a JSON value for comparison: numbers -> float when numeric,
    keep string/bool/null distinct; recurse objects/arrays (key order-insensitive)."""
    if isinstance(v, dict):
        # HOCON "null == missing": Lightbend's JSON renderer omits null-valued keys,
        # so drop them uniformly for all libraries before comparing.
        return {k: norm(x) for k, x in v.items() if x is not None}
    if isinstance(v, list):
        return [norm(x) for x in v]
    if isinstance(v, bool):
        return ("bool", v)
    if isinstance(v, (int, float)):
        return ("num", float(v))
    if v is None:
        return ("null",)
    return ("str", v)

def equal(a, b):
    return norm(a) == norm(b)

def is_upstream(rel):
    # Lightbend upstream mirror = root-level conf (no subdirectory in the rel path)
    return "/" not in rel

def run():
    # collect fixtures: rel (without -expected.json) -> (conf_path, expected_obj)
    fixtures = []
    for root, _, files in os.walk(EXP):
        for f in files:
            if not f.endswith("-expected.json"):
                continue
            rel = os.path.relpath(os.path.join(root, f), EXP)
            base = rel[:-len("-expected.json")]
            conf = os.path.join(CONF, base + ".conf")
            if not os.path.exists(conf):
                continue
            try:
                exp = json.load(open(os.path.join(EXP, rel)))
            except Exception:
                continue
            fixtures.append((base, conf, exp))
    fixtures.sort()

    results = {}  # libname -> Counter per layer
    for lang, name, ver, builder in LIBS:
        cnt = {"up": collections.Counter(), "o3co": collections.Counter(), "diverge": collections.Counter()}
        detail = []
        for base, conf, exp in fixtures:
            if base.startswith("empty-file/"):
                layer = "diverge"   # documented Lightbend-vs-strict split; report separately
            else:
                layer = "up" if is_upstream(base) else "o3co"
            argv, cwd, env = builder(conf)
            # load per-fixture .env sidecar (env-var-list group) into subprocess env
            envfile = conf[:-5] + ".env"
            if os.path.exists(envfile):
                base_env = dict(env) if env else dict(os.environ)
                for line in open(envfile):
                    line = line.strip()
                    if line and "=" in line and not line.startswith("#"):
                        k, v = line.split("=", 1); base_env[k] = v
                env = base_env
            try:
                r = subprocess.run(argv, cwd=cwd, env=env, capture_output=True, timeout=30, text=True)
            except subprocess.TimeoutExpired:
                cnt[layer]["TIMEOUT"] += 1; continue
            crash = (r.returncode < 0) or ("panic:" in r.stderr.lower()) \
                    or ("SIGSEGV" in r.stderr) or ("CRASH:" in r.stderr) \
                    or ("signal:" in r.stderr.lower())
            if crash:
                cnt[layer]["CRASH"] += 1; detail.append((base, "CRASH")); continue
            if r.returncode != 0:
                cnt[layer]["ERROR"] += 1; continue
            try:
                got = json.loads(r.stdout)
            except Exception:
                cnt[layer]["BADJSON"] += 1; continue
            if equal(got, exp):
                cnt[layer]["PASS"] += 1
            else:
                cnt[layer]["MISMATCH"] += 1
        results[name] = (lang, ver, cnt)
        up, o3 = cnt["up"], cnt["o3co"]
        upt = sum(up.values()); o3t = sum(o3.values())
        print(f"{name:26s} {ver:9s} up:{up['PASS']}/{upt} o3co:{o3['PASS']}/{o3t} "
              f"[E:{up['ERROR']+o3['ERROR']} X:{up['CRASH']+o3['CRASH']} M:{up['MISMATCH']+o3['MISMATCH']}]")
    # write raw
    json.dump({n:(l,v,{k:dict(c) for k,c in cc.items()}) for n,(l,v,cc) in results.items()},
              open(os.path.join(HB,"results.json"),"w"), indent=2)
    return results

if __name__ == "__main__":
    run()
