import sys, json
import hocon  # o3co/py.hocon: PyPI dist `hocon-parser`, import name `hocon`
try:
    cfg = hocon.parse_file(sys.argv[1])
    print(json.dumps(cfg.to_object()))
except Exception as e:
    sys.stderr.write("ERROR: %s: %s\n" % (type(e).__name__, e))
    sys.exit(1)
