import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const parseHocon = require('hocon-parser')
try {
  const src = readFileSync(process.argv[2], 'utf-8')
  const result = parseHocon(src)
  process.stdout.write(JSON.stringify(result) + '\n')
} catch (e) {
  process.stderr.write('ERROR: ' + (e && e.message || e) + '\n')
  process.exit(1)
}
