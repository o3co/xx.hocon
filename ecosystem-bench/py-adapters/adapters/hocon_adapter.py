import sys, json
import hocon
try:
    with open(sys.argv[1]) as f:
        data = hocon.load(f)
    print(json.dumps(data))
except Exception as e:
    sys.stderr.write("ERROR: %s: %s\n" % (type(e).__name__, e))
    sys.exit(1)
