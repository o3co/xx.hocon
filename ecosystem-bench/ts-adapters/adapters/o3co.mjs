import { readFileSync } from 'node:fs'
import { parse } from '@o3co/ts.hocon'
try {
  const src = readFileSync(process.argv[2], 'utf-8')
  const cfg = parse(src)
  process.stdout.write(JSON.stringify(cfg.toObject()) + '\n')
} catch (e) {
  process.stderr.write('ERROR: ' + (e && e.message || e) + '\n')
  process.exit(1)
}
