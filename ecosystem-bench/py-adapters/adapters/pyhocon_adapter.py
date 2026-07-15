import sys
from pyhocon import ConfigFactory, HOCONConverter
try:
    cfg = ConfigFactory.parse_file(sys.argv[1])
    print(HOCONConverter.to_json(cfg))
except Exception as e:
    sys.stderr.write("ERROR: %s: %s\n" % (type(e).__name__, e))
    sys.exit(1)
