#!/usr/bin/env python3
"""Reference model of docs/resolution-model.md: an expectation generator, NOT a HOCON implementation.

It exists to compute the expected value of fixtures whose answer is an E23 / o3co decision
(see README.md). It parses only the HOCON subset those fixtures use, and it must never be used
for a case whose answer comes from Lightbend.

Usage: python3 resolution_model.py file.conf [...]
Prints one JSON line per file: {"file":..., "result": <json or "ERR ...">, "notes": [...]}
The environment is empty (the fixtures are hermetic); run_file(path, env) takes one for tests.
Section numbers in comments (§n) refer to docs/resolution-model.md.
"""
import json, os, sys, re

# ================================================================ §1 values
class Val: pass
class Scalar(Val):
    def __init__(s, kind, v, text, quoted=True):
        s.kind, s.v, s.text, s.quoted = kind, v, text, quoted
    def __repr__(s): return 'S(%r)' % (s.v,)
class Ws(Scalar):                                   # whitespace between concatenation pieces
    def __init__(s, text): Scalar.__init__(s, 'str', text, text, False)
class Lst(Val):
    def __init__(s, items): s.items = items
    def __repr__(s): return 'L%r' % (s.items,)
class Field:
    """A slot: (object, key). `stack` is top first. synth=True: created while resolving (§3.5), never mapped by a view."""
    def __init__(s, key, stack, path, synth=False):
        s.key, s.stack, s.path, s.synth = key, stack, tuple(path), synth
    def __repr__(s): return 'F(%s:%r)' % ('.'.join(s.path), s.stack)
class Obj(Val):
    def __init__(s, fields=None, flag=False):
        s.fields = fields if fields is not None else {}
        s.flag = flag                                   # ignoresFallbacks
    def __repr__(s): return 'O{%s}%s' % (', '.join('%s:%r' % (k, f.stack) for k, f in s.fields.items()), '!' if s.flag else '')
class Subst(Val):
    def __init__(s, path, optional, prefix=0):
        s.path, s.optional, s.prefix = tuple(path), optional, prefix
    def __repr__(s): return '${%s%s}' % ('?' if s.optional else '', '.'.join(s.path))
class Concat(Val):
    def __init__(s, pieces, fpath=()):
        s.pieces, s.fpath = pieces, tuple(fpath)
    def __repr__(s): return 'C%r' % (s.pieces,)
class Partial(Val):
    """A multi-entry merged value produced under a restriction (§3.4)."""
    def __init__(s, stack): s.stack = stack

MISSING = object()
NOTES = set()                                       # parse-time notes (e.g. a missing include target)
PHASE = ['parse']

def resolved(v):
    if isinstance(v, Scalar): return True
    if isinstance(v, Lst): return all(resolved(i) for i in v.items)
    if isinstance(v, Obj): return all(len(f.stack) == 1 and resolved(f.stack[0]) for f in v.fields.values())
    return False

def ignores(v):                                     # §1.2
    if isinstance(v, Scalar): return True
    if isinstance(v, Lst): return resolved(v)
    if isinstance(v, Obj): return v.flag
    return False

def ignores_stack(st): return ignores(st[-1])     # a stack ignores fallbacks iff its last entry does

# ================================================================ §2 fallback
def fallback(top, existing):
    if existing is None: return top                                     # F1
    if ignores_stack(top): return top                                   # F2
    if len(top) > 1: return top + existing                              # F3
    t = top[0]
    if len(existing) > 1 or isinstance(existing[0], (Subst, Concat)):   # F4
        return [t] + existing
    e = existing[0]
    if isinstance(t, Obj) and isinstance(e, Obj): return [merge_obj(t, e)]   # F5
    if isinstance(t, Obj):                                              # F6 (t is unflagged here)
        if resolved(t): return [Obj(dict(t.fields), True)]
        return [t] + existing
    return [t] + existing                                               # F7

def merge_obj(t, e):
    out = {}
    synth = PHASE[0] == 'resolve'
    for k, f in e.fields.items():
        out[k] = Field(k, fallback(t.fields[k].stack, f.stack), f.path, synth) if k in t.fields else f
    for k, f in t.fields.items():
        if k not in out: out[k] = f
    return Obj(out, e.flag)

# ================================================================ parser (subset; §2.3 desugaring)
class P:
    def __init__(s, text, fname):
        s.t, s.i, s.fname, s.depth = text, 0, fname, 0
    def peek(s, n=0): return s.t[s.i + n] if s.i + n < len(s.t) else ''
    def err(s, m): raise SyntaxError('%s at %d in %s' % (m, s.i, os.path.basename(s.fname)))
    def skip_ws(s, nl=False):
        while True:
            c = s.peek()
            if c and (c in ' \t\r' or (nl and c in '\n,')): s.i += 1
            elif c == '#' or (c == '/' and s.peek(1) == '/'):
                while s.peek() not in ('\n', ''): s.i += 1
            else: return
    def body(s, path, closing):
        obj = Obj()
        while True:
            s.skip_ws(nl=True)
            c = s.peek()
            if c == closing or (c == '' and closing == ''):
                if c: s.i += 1
                return obj
            if c == '': s.err('eof')
            if s.t.startswith('include', s.i) and s.t[s.i+7:s.i+8] in ' \t"':
                s.i += 7; s.skip_ws()
                s.include(obj, s.qstring(), path); continue
            keys = s.keypath(); s.skip_ws(); c = s.peek()
            if c == '{': sep = '='
            elif c in ':=': sep = '='; s.i += 1
            elif c == '+' and s.peek(1) == '=': sep = '+='; s.i += 2
            else: s.err('expected separator')
            if sep == '+=' and s.depth > 0: s.err('+= inside a list')                # §2.3 [LB ConfigParser:262-276]
            s.skip_ws()
            full = tuple(path) + tuple(keys)
            if sep == '+=': s.depth += 1          # a += value counts as inside a list [LB ConfigParser.java:275]
            v = s.value(full)
            if sep == '+=': s.depth -= 1
            if sep == '+=': v = Concat([Subst(full, True), Lst([v])], full)
            s.put(obj, keys, v, path)
    def put(s, obj, keys, v, path):
        k = keys[0]
        if len(keys) > 1:
            inner = v
            for j in range(len(keys) - 1, 0, -1):
                inner = Obj({keys[j]: Field(keys[j], [inner], tuple(path) + tuple(keys[:j+1]))})
            v = inner
        ex = obj.fields.get(k)
        st = fallback([v], ex.stack if ex else None)
        if ex: ex.stack = st
        else: obj.fields[k] = Field(k, st, tuple(path) + (k,))
    def include(s, obj, fn, path):
        full = os.path.join(os.path.dirname(s.fname), fn)
        if not os.path.exists(full):              # Lightbend ignores a missing non-required include
            NOTES.add('missing include target ' + fn); return
        sub = parse_file(full)
        if s.depth > 0 and not resolved(sub): s.err('include with substitutions inside a list')
        if path: sub = relativize(sub, tuple(path))
        for k, f in sub.fields.items():
            ex = obj.fields.get(k)
            if ex: ex.stack = fallback(f.stack, ex.stack)
            else: obj.fields[k] = Field(k, f.stack, tuple(path) + (k,))
    def keypath(s):
        keys = []; cur = ''
        while True:
            c = s.peek()
            if c == '"': cur += s.qstring()
            elif c == '.': keys.append(cur); cur = ''; s.i += 1
            elif c and c not in ' \t\n:={}+[],#$"' and not (c == '/' and s.peek(1) == '/'): cur += c; s.i += 1
            else: break
        keys.append(cur)
        if not keys[0] and len(keys) == 1: s.err('empty key')
        return keys
    def qstring(s):
        if s.peek() != '"': s.err('expected string')
        if s.t.startswith('"""', s.i):
            j = s.t.index('"""', s.i + 3); r = s.t[s.i+3:j]; s.i = j + 3; return r
        s.i += 1; out = ''
        while s.peek() != '"':
            c = s.peek()
            if c == '' or c == '\n': s.err('unterminated string')
            if c == '\\': out += {'n': '\n', 't': '\t'}.get(s.peek(1), s.peek(1)); s.i += 2
            else: out += c; s.i += 1
        s.i += 1
        return out
    def value(s, path):
        pieces = []
        while True:
            c = s.peek()
            if c in ('', '\n', ',', '}', ']') or c == '#' or (c == '/' and s.peek(1) == '/'): break
            if c in ' \t':
                j = s.i
                while s.peek() and s.peek() in ' \t': s.i += 1
                pieces.append(Ws(s.t[j:s.i])); continue
            if c == '{': s.i += 1; pieces.append(s.body(path, '}'))
            elif c == '[': s.i += 1; s.depth += 1; pieces.append(s.array(path)); s.depth -= 1
            elif c == '"': pieces.append(Scalar('str', s.qstring(), None, True))
            elif c == '$' and s.peek(1) == '{':
                s.i += 2; opt = False
                if s.peek() == '?': opt = True; s.i += 1
                j = s.t.index('}', s.i); expr = s.t[s.i:j].strip(); s.i = j + 1
                if expr.replace(' ', '').endswith('[]'): s.err('${X[]} list expansion is not modelled')
                pieces.append(Subst(tuple(split_path(expr)), opt, 0))
            else:
                j = s.i
                while s.peek() and s.peek() not in ' \t\n,}]$"{[#' and not (s.peek() == '/' and s.peek(1) == '/'): s.i += 1
                if j == s.i: s.err('bad char %r' % c)
                pieces.append(unquoted(s.t[j:s.i]))
        while pieces and isinstance(pieces[-1], Ws): pieces.pop()
        while pieces and isinstance(pieces[0], Ws): pieces.pop(0)
        if not pieces: s.err('empty value')
        v = make_concat(pieces)
        if isinstance(v, Concat): v.fpath = tuple(path)
        return v
    def array(s, path):
        items = []
        while True:
            s.skip_ws(nl=True)
            if s.peek() == ']': s.i += 1; return Lst(items)
            items.append(s.value(path))

def split_path(expr):
    out = []; cur = ''; i = 0
    while i < len(expr):
        c = expr[i]
        if c == '"': j = expr.index('"', i + 1); cur += expr[i+1:j]; i = j + 1
        elif c == '.': out.append(cur.strip()); cur = ''; i += 1
        else: cur += c; i += 1
    out.append(cur.strip())
    return out

NUM = re.compile(r'^-?\d+(\.\d+)?([eE][+-]?\d+)?$')
def unquoted(t):
    if t == 'true': return Scalar('bool', True, t)
    if t == 'false': return Scalar('bool', False, t)
    if t == 'null': return Scalar('null', None, t)
    if NUM.match(t): return Scalar('num', json.loads(t), t)
    return Scalar('str', t, t, False)

def to_string(v):
    if isinstance(v, Scalar):
        return v.v if v.kind == 'str' else v.text
    return None

class HardError(Exception): pass      # aborts the whole resolve (§6.3)
class ReEntry(Exception):              # caught by the innermost active substitution frame (§6.3)
    def __init__(s, kind): s.kind = kind   # 'marker' (an active substitution again) or 'empty' (EMPTY in a view)

def join(builder, right):             # §1.3 [LB ConfigConcatenation.join]
    left = builder[-1]
    if isinstance(left, Obj) and isinstance(right, Obj):
        st = fallback([right], [left])
        if len(st) != 1: raise HardError('internal: unresolved object concatenation')
        builder[-1] = st[0]; return
    if isinstance(left, Lst) and isinstance(right, Lst): builder[-1] = Lst(left.items + right.items); return
    if isinstance(left, (Lst, Obj)) and isinstance(right, Scalar) and right.kind == 'str' and not right.quoted: return
    if isinstance(left, (Subst, Concat)) or isinstance(right, (Subst, Concat)): builder.append(right); return
    s1, s2 = to_string(left), to_string(right)
    if s1 is None or s2 is None: raise HardError('WrongType: cannot concatenate object or list with a non-object-or-list')
    builder[-1] = Scalar('str', s1 + s2, None, True)

def consolidate(pieces):
    out = []
    for p in pieces:
        for q in (p.pieces if isinstance(p, Concat) else [p]):
            if not out: out.append(q)
            else: join(out, q)
    return out

def make_concat(pieces):
    cleaned = []
    for i, p in enumerate(pieces):
        if isinstance(p, Ws):
            prev = cleaned[-1] if cleaned else None
            nxt = pieces[i + 1] if i + 1 < len(pieces) else None
            if isinstance(prev, (Obj, Lst)) or isinstance(nxt, (Obj, Lst)): continue
        cleaned.append(p)
    if len(cleaned) == 1: return cleaned[0]
    c = consolidate(cleaned)
    return c[0] if len(c) == 1 else Concat(c)

def relativize(v, prefix):             # §7.1 [LB relativized]
    if isinstance(v, Subst): return Subst(prefix + v.path, v.optional, v.prefix + len(prefix))
    if isinstance(v, Concat): return Concat([relativize(p, prefix) for p in v.pieces], prefix + v.fpath)
    if isinstance(v, Lst): return Lst([relativize(p, prefix) for p in v.items])
    if isinstance(v, Obj):
        return Obj({k: Field(k, [relativize(x, prefix) for x in f.stack], prefix + f.path) for k, f in v.fields.items()}, v.flag)
    return v

def parse_file(fname):
    p = P(open(fname).read(), fname)
    p.skip_ws(nl=True)
    if p.peek() == '{': p.i += 1; return p.body((), '}')
    return p.body((), '')

def substs_in(v, out):
    if isinstance(v, Subst): out.add(id(v))
    elif isinstance(v, Concat):
        for p in v.pieces: substs_in(p, out)
    elif isinstance(v, Lst):
        for p in v.items: substs_in(p, out)
    elif isinstance(v, Obj):
        for f in v.fields.values():
            for x in f.stack: substs_in(x, out)
    return out

# ================================================================ §4 views
class Empty:
    """EMPTY substitute: nothing below the entry. `owned` = substitutions written inside that bottom entry."""
    def __init__(s, entry): s.owned = substs_in(entry, set())

class View:
    def __init__(s, m=None):
        s.m = m or {}
        s.key = frozenset((k, id(v)) for k, v in s.m.items())
    def get(s, F):
        if F.synth: return None
        return s.m.get(('slot', id(F), F.path))
    def with_(s, F, sub):
        m = dict(s.m); m[('slot', id(F), F.path)] = sub
        return View(m)
    def below(s, path, strict=False):
        n = len(path)
        return frozenset((k, id(v)) for k, v in s.m.items()
                         if k[2][:n] == tuple(path) and (len(k[2]) > n or not strict))

# ================================================================ §3-§6 resolver
class Resolver:
    def __init__(s, root, env):
        s.root, s.env = root, env
        s.smemo, s.cmemo, s.stmemo = {}, {}, {}
        s.smemo_view = {}
        s.active = set()
        s.subcache = {}
        s.notes = set()
        s.keep = []

    def substitute(s, stack, start):                      # §4.2
        key = (id(stack), start)
        if key not in s.subcache:
            rest = stack[start:]
            if not rest: sub = Empty(stack[start - 1])
            else:
                m = [rest[0]]
                for v in rest[1:]: m = fallback(m, [v])
                sub = m
            s.subcache[key] = (sub, stack)
        return s.subcache[key][0]

    def stack_of(s, F, view):
        m = view.get(F)
        return F.stack if m is None else m

    def value_of(s, F, view, restrict, need_key=None):
        st = s.stack_of(F, view)
        vk = view.below(F.path)
        full = s.stmemo.get((id(F), id(st), vk, None))
        if full is not None:
            if restrict is None: return full
            # §5.3: a full result satisfies a restricted request, except that presence is judged on the raw value
            if need_key is None or (isinstance(full, Obj) and need_key in full.fields): return full
        if restrict is not None:
            r = s.stmemo.get((id(F), id(st), vk, restrict))
            if r is not None: return r
        r = s.walk(F, st, view, restrict)
        if r is not MISSING:
            s.stmemo[(id(F), id(st), vk, restrict)] = r
            s.keep.append((F, st))                 # keep the keyed objects alive, so their ids are never reused
        return r

    def walk(s, F, st, view, restrict):                   # §3.2
        merged = None
        for idx, e in enumerate(st):
            if merged is not None and ignores_stack(merged): break                       # W1 cut
            if isinstance(e, (Subst, Concat)):
                if len(st) > 1 and not F.synth: v2 = view.with_(F, s.substitute(st, idx + 1))   # W3 look-back
                else: v2 = view
                r = s.resolve(e, v2, restrict)
            else:
                if (merged is not None and len(merged) == 1 and isinstance(merged[0], Obj) and isinstance(e, Obj)
                        and e.fields and all(k in merged[0].fields and ignores_stack(merged[0].fields[k].stack) for k in e.fields)):
                    continue                                                              # W2 shadow skip
                r = s.resolve(e, view, restrict)                                          # W4 forward
            if r is MISSING: continue                                                     # W5
            rs = r.stack if isinstance(r, Partial) else [r]
            merged = rs if merged is None else fallback(merged, rs)
        if merged is None: return MISSING
        return merged[0] if len(merged) == 1 else Partial(merged)

    def resolve(s, v, view, restrict):
        if isinstance(v, Scalar): return v
        if isinstance(v, Lst):
            if restrict is not None: return v                                             # §3.4 list under restriction
            out = []
            for it in v.items:
                r = s.resolve(it, view, None)
                if r is not MISSING: out.append(r)
            return Lst(out)
        if isinstance(v, Obj): return s.resolve_obj(v, view, restrict)
        if isinstance(v, Subst): return s.resolve_subst(v, view, restrict)
        if isinstance(v, Concat): return s.resolve_concat(v, view)
        raise HardError('internal resolve %r' % (v,))

    def resolve_concat(s, c, view):                       # §1.3, §5.2
        ck = (id(c), view.below(c.fpath, strict=True))
        if ck in s.cmemo: return s.cmemo[ck]
        out = []
        for p in c.pieces:
            r = s.resolve(p, view, None)
            if r is not MISSING: out.append(r)
        if not out: r = MISSING
        else:
            j = consolidate(out)
            if len(j) != 1: raise HardError('internal: concatenation did not join')
            r = j[0]
        if ck in s.cmemo: return s.cmemo[ck]               # first completed result wins
        if r is not MISSING: s.cmemo[ck] = r
        return r

    def resolve_obj(s, o, view, restrict):
        if restrict is None:
            out = {}
            for k, F in o.fields.items():
                if isinstance(s.stack_of(F, view), Empty): continue                       # §4.3 absent
                r = s.value_of(F, view, None)
                if r is MISSING: continue
                out[k] = Field(k, [r], F.path)
            return Obj(out, o.flag)
        k, rest = restrict[0], restrict[1:]
        if k not in o.fields or not rest: return o
        F = o.fields[k]
        if isinstance(s.stack_of(F, view), Empty): return o
        r = s.value_of(F, view, rest, need_key=rest[0])
        nf = dict(o.fields)
        if r is MISSING: del nf[k]
        elif isinstance(r, Partial): nf[k] = Field(k, r.stack, F.path, True)
        else: nf[k] = Field(k, [r], F.path, True)
        return Obj(nf, o.flag)

    def child(s, v, key, path):                           # §3.4 descend one key
        if isinstance(v, Obj): return v.fields.get(key)
        if isinstance(v, Partial):
            parts = []
            for layer in v.stack:
                if not isinstance(layer, Obj): break
                if key in layer.fields: parts.append(layer.fields[key].stack)
            if not parts: return None
            st = parts[0]
            for p in parts[1:]: st = fallback(st, p)
            return Field(key, st, path, True)
        return None

    def find(s, path, view, S):                           # §3.3; returns (slot or None, reached EMPTY?)
        F = s.root.fields.get(path[0])
        if F is None: return None, False
        for i in range(len(path)):
            m = s.stack_of(F, view)
            if isinstance(m, Empty):
                if S.path == F.path and id(S) in m.owned: return None, False   # §4.4 own-path: undefined
                return None, True                    # §4.4: absent for §7.2 (original path, env), then a re-entry
            if i == len(path) - 1: return F, False
            v = s.value_of(F, view, path[i+1:], need_key=path[i+1])
            if v is MISSING: return None, False
            F = s.child(v, path[i+1], F.path + (path[i+1],))
            if F is None: return None, False
        return F, False

    def resolve_subst(s, S, view, restrict):             # §5.1, §6
        if id(S) in s.smemo:
            hit = s.smemo[id(S)]
            # §5.3: a full result answers a restricted request, except that presence is judged on the raw
            # value -- in the view that produced the memoized result, not in the caller's view.
            if not (restrict and isinstance(hit, Obj) and restrict[0] not in hit.fields): return hit
            view = s.smemo_view[id(S)]
        mk = (id(S), view.key)
        if mk in s.active: raise ReEntry('marker')
        s.active.add(mk)
        caught = False
        frame = {'snap': s.snapshot()}
        try:
            try:
                r = s.lookup(S, view, restrict, frame)
            except ReEntry as e:
                snap = frame['snap']                  # §5.4
                for memo, keys in ((s.smemo, snap[0]), (s.cmemo, snap[1]), (s.stmemo, snap[2])):
                    for k in set(memo) - keys: del memo[k]
                if not S.optional: raise HardError('UnresolvedSubstitution: %r was part of a cycle' % (S,))
                r = MISSING; caught = e.kind == 'marker'      # §5.1: only a cycle break is remembered
        finally:
            s.active.discard(mk)
        if restrict is None or (isinstance(r, Val) and resolved(r)) or r is MISSING:
            if id(S) in s.smemo and restrict is None: return s.smemo[id(S)]   # first completed result wins
            if id(S) in s.smemo: return r
            if r is not MISSING or caught: s.smemo[id(S)] = r; s.smemo_view[id(S)] = view
        return r

    def snapshot(s):
        return (set(s.smemo), set(s.cmemo), set(s.stmemo))

    def lookup(s, S, view, restrict, frame):              # §7.2
        F, hit = s.find(S.path, view, S)
        if F is None and S.prefix > 0:
            F, hit2 = s.find(S.path[S.prefix:], view, S)
            hit = hit or hit2
        frame['snap'] = s.snapshot()                       # §5.4: what the lookup itself memoized is kept
        if F is None:
            names = (['.'.join(S.path)] if S.prefix > 0 else []) + ['.'.join(S.path[S.prefix:])]
            for n in names:
                if n in s.env: return Scalar('str', s.env[n], None, True)
            if hit: raise ReEntry('empty')                    # §4.4: nothing found anywhere -> re-entry
            if S.optional: return MISSING
            raise HardError('UnresolvedSubstitution: %r' % (S,))
        r = s.value_of(F, view, restrict, need_key=restrict[0] if restrict else None)
        if r is MISSING and not S.optional: raise HardError('UnresolvedSubstitution: %r (resolved to nothing)' % (S,))
        return r

def to_json(v):
    if isinstance(v, Scalar): return v.v
    if isinstance(v, Lst): return [to_json(i) for i in v.items]
    if isinstance(v, Obj): return {k: to_json(f.stack[0]) for k, f in v.fields.items()}
    raise HardError('unresolved in output %r' % (v,))

def run_file(fn, env=None):
    sys.setrecursionlimit(20000)
    PHASE[0] = 'parse'
    NOTES.clear()
    try: root = parse_file(fn)
    except (SyntaxError, HardError) as e: return 'ERR parse ' + str(e), set()
    PHASE[0] = 'resolve'
    R = Resolver(root, env or {})
    R.notes |= NOTES
    try: return to_json(R.resolve_obj(root, View(), None)), R.notes
    except HardError as e: return 'ERR ' + str(e), R.notes
    except ReEntry: return 'ERR internal: re-entry escaped to top level', R.notes
    except RecursionError: return 'ERR internal: recursion', R.notes

if __name__ == '__main__':
    args = sys.argv[1:]
    for fn in args:
        r, notes = run_file(fn)
        print(json.dumps({'file': os.path.basename(fn), 'result': r, 'notes': sorted(notes)}))
