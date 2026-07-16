// Vendored ts.hocon differential adapter. Imports the npm-published
// @o3co/ts.hocon (installed via `npm install`) rather than a local build, so
// the differential harness is reproducible from a clean xx.hocon checkout.
const EXIT_ERROR = 3

async function main() {
  const file = process.argv[2]
  if (file === undefined) {
    console.error('usage: hocon-json <conf-file>')
    process.exitCode = 2
    return
  }
  try {
    const { parseFile } = await import('@o3co/ts.hocon')
    const cfg = parseFile(file)
    process.stdout.write(cfg._renderJSONForTest() + '\n')
  } catch (e) {
    const type = e?.constructor?.name ?? 'Error'
    const message = e instanceof Error ? e.message : String(e)
    process.stdout.write(JSON.stringify({ __error__: { type, message } }) + '\n')
    process.exitCode = EXIT_ERROR
  }
}

main()
