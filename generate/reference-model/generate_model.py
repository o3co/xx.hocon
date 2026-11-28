#!/usr/bin/env python3
"""Write the expected files of E23 / o3co fixtures from the reference model.

A fixture is handled here only if it has a `<name>.expectation.md` sidecar next to its `.conf`
in a group directory under testdata/hocon/. Every other fixture's expected file comes from
Lightbend (generate/src/main/java/GenerateExpected.java, `make generate`), and this script refuses
to write for any `.conf` that GenerateExpected.java lists, or for the root-level upstream mirror.

Usage (from the repository root):
  python3 generate/reference-model/generate_model.py           # write
  python3 generate/reference-model/generate_model.py --check   # fail if a file would change
"""
import importlib.util, json, os, posixpath, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
TESTDATA = os.path.join(ROOT, 'testdata', 'hocon')
EXPECTED = os.path.join(ROOT, 'expected', 'hocon')
GENERATE_EXPECTED = os.path.join(ROOT, 'generate', 'src', 'main', 'java', 'GenerateExpected.java')
KINDS = {'e23-order', 'e23-view-reentry', 'e23-memo', 'o3co-J1', 'o3co-J2', 'o3co-J3', 'o3co-J4', 'o3co-J5', 'pending-step-b'}
MARKER = 'Exception class: o3co reference model (generate/reference-model, not Lightbend)'

spec = importlib.util.spec_from_file_location('resolution_model', os.path.join(HERE, 'resolution_model.py'))
model = importlib.util.module_from_spec(spec)
spec.loader.exec_module(model)


def fail(msg):
    print('ERROR: ' + msg, file=sys.stderr)
    sys.exit(1)


def read_expectation(path):
    """The fields are the `key: value` lines of the first block (up to the first blank line after them)."""
    fields, started = {}, False
    for line in open(path, encoding='utf-8'):
        line = line.strip()
        m = re.match(r'^([a-z0-9.-]+):\s*(.*)$', line)
        if m:
            started = True
            if m.group(1) in fields: fail('%s: field %r given twice' % (path, m.group(1)))
            fields[m.group(1)] = m.group(2)
        elif started and not line:
            break
    kind = fields.get('kind')
    if kind not in KINDS:
        fail('%s: kind %r is not one of %s' % (path, kind, sorted(KINDS)))
    if kind == 'pending-step-b':
        for k in ('e23-final', 'interim'):
            if k not in fields: fail('%s: kind pending-step-b needs %r' % (path, k))
        try:
            json.loads(fields['e23-final'])
            if fields['interim'] != 'ERR': json.loads(fields['interim'])
        except ValueError as e:
            fail('%s: e23-final / interim must be JSON (or interim: ERR): %s' % (path, e))
    return fields


def main():
    check = '--check' in sys.argv[1:]
    lightbend_listed = set(re.findall(r'"([^"]+\.conf)"', open(GENERATE_EXPECTED, encoding='utf-8').read()))
    planned = []
    for dirpath, _, files in os.walk(TESTDATA):
        for f in sorted(files):
            if not f.endswith('.expectation.md'): continue
            name = f[:-len('.expectation.md')]
            group = posixpath.normpath(os.path.relpath(dirpath, TESTDATA).replace(os.sep, '/'))
            if group in ('.', ''):
                fail('%s is in the root of testdata/hocon/, the Lightbend upstream mirror; the model never writes there' % f)
            conf = os.path.join(dirpath, name + '.conf')
            rel = '%s/%s.conf' % (group, name)
            if not os.path.exists(conf): fail('%s has no %s.conf' % (f, name))
            if rel in lightbend_listed:
                fail('%s is listed in GenerateExpected.java (its expected file comes from Lightbend); '
                     'remove %s or the GenerateExpected entry' % (rel, f))
            fields = read_expectation(os.path.join(dirpath, f))
            result, notes = model.run_file(conf)
            if notes: fail('%s: %s' % (rel, '; '.join(sorted(notes))))
            is_err = isinstance(result, str)
            if is_err and result.startswith(('ERR parse', 'ERR internal')):
                fail('%s: the model cannot evaluate this case (%s); it is outside the modelled subset' % (rel, result))
            if fields['kind'] == 'pending-step-b':
                got = 'ERR' if is_err else json.dumps(result, sort_keys=True)
                want = fields['interim'] if fields['interim'] == 'ERR' else json.dumps(json.loads(fields['interim']), sort_keys=True)
                if got != want:
                    fail('%s: the model gives %s for the interim, the sidecar records %s' % (rel, got, fields['interim']))
                planned.append((group, name, 'json', json.loads(fields['e23-final'])))
            elif is_err:
                planned.append((group, name, 'error', result))
            else:
                planned.append((group, name, 'json', result))

    changed = []
    owned = set()
    for group, name, kind, value in planned:
        out_dir = os.path.join(EXPECTED, group)
        json_path = os.path.join(out_dir, name + '-expected.json')
        err_path = os.path.join(out_dir, name + '.error')
        if kind == 'json':
            path, other = json_path, err_path
            text = json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + '\n'
        else:
            path, other = err_path, json_path
            text = MARKER + '\nMessage: ' + value[len('ERR '):] + '\n'
        owned.add(path)
        old = open(path, encoding='utf-8').read() if os.path.exists(path) else None
        if old != text or os.path.exists(other):
            changed.append(os.path.relpath(path, ROOT))
            if not check:
                os.makedirs(out_dir, exist_ok=True)
                open(path, 'w', encoding='utf-8').write(text)
                if os.path.exists(other): os.remove(other)
        print('OK (reference model): %s/%s -> %s' % (group, name, os.path.basename(path)))

    # A model-written .error whose case no longer has an .expectation.md is stale.
    for dirpath, _, files in os.walk(EXPECTED):
        for f in files:
            p = os.path.join(dirpath, f)
            if f.endswith('.error') and p not in owned:
                with open(p, encoding='utf-8', errors='replace') as fh:
                    if fh.readline().rstrip('\n') == MARKER:
                        changed.append(os.path.relpath(p, ROOT) + ' (stale)')
                        if not check: os.remove(p)

    if check and changed:
        fail('out of date: ' + ', '.join(changed) + ' (run make generate-model)')
    print('Done. %d expectation(s); %d written%s.' % (len(planned), 0 if check else len(changed), ' (check)' if check else ''))


if __name__ == '__main__':
    main()
