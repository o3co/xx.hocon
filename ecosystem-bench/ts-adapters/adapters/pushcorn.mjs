import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const parser = require('@pushcorn/hocon-parser')
try {
  const result = await parser.parse({ url: process.argv[2] })
  process.stdout.write(JSON.stringify(result) + '\n')
} catch (e) {
  process.stderr.write('ERROR: ' + (e && e.message || e) + '\n')
  process.exit(1)
}
