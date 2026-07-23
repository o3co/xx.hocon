import com.typesafe.config.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public class GenerateExpected {

    // Conf files that should parse successfully and produce expected JSON.
    // Fixtures with a .env sidecar are processed via EnvVarListExpander
    // (which expands ${X[]} patterns using the sidecar env vars before Lightbend parses).
    static final String[] SUCCESS_CONFS = {
        // xx.hocon#50: leading-zero numeric VALUE literals canonicalize to numbers
        // (Lightbend authority): 023->23, 08.53->8.53, -023->-23. Distinct from
        // E2/E3/E4 (leading-zero/sign object KEYS for array conversion).
        "leading-zero-value/lzv01-int-float-negative.conf",
        "test01.conf",
        "test02.conf",
        "test03.conf",
        "test04.conf",
        "test05.conf",
        "test06.conf",
        "test07.conf",
        "test08.conf",
        "test09.conf",
        "test10.conf",
        "test11.conf",
        "test12.conf",
        "test13-reference-with-substitutions.conf",
        "test13-application-override-substitutions.conf",
        "bom.conf",
        "file-include.conf",
        // "include-from-list.conf",  // typesafe-config limitation: include in list can't resolve ${}
        // "env-variables.conf",      // ${VAR[]} now supported natively (typesafe-config 1.4.6+),
        //                            // but env vars (SECRET_A, MY_LIST, etc.) aren't namespaced;
        //                            // host env would leak. Equivalent coverage provided by ev01-ev13
        //                            // fixtures with project-namespaced keys + .env sidecar pipeline.
        "subst-tokenize/st01-unquoted-simple.conf",
        "subst-tokenize/st02-quoted-single-segment.conf",
        "subst-tokenize/st03-quoted-dot-in-key.conf",
        "subst-tokenize/st04-quoted-dots-separator.conf",
        "subst-tokenize/st05-unquoted-dot-chain.conf",
        "subst-tokenize/st06-mixed-quoted-unquoted.conf",
        "subst-tokenize/st07-quoted-ws-concat.conf",
        "subst-tokenize/st08-quoted-unquoted-concat.conf",
        "subst-tokenize/st09-empty-quoted-key.conf",
        "subst-tokenize/st10-escape-newline.conf",
        "subst-tokenize/st11-escape-tab.conf",
        "subst-tokenize/st12-escape-backslash.conf",
        "subst-tokenize/st13-escape-quote.conf",
        "subst-tokenize/st14-escape-unicode-bmp.conf",
        "subst-tokenize/st15-optional-subst.conf",
        "subst-tokenize/st16-optional-quoted.conf",
        "subst-tokenize/st17-optional-mixed-concat.conf",
        "subst-tokenize/st18-deep-nested-quoted.conf",
        "subst-tokenize/st19-quoted-escape-slash.conf",
        "subst-tokenize/st20-quoted-escape-backspace-formfeed.conf",
        "numeric-obj-array/na01-basic.conf",
        "numeric-obj-array/na02-lazy-getobject.conf",
        "numeric-obj-array/na03a-concat-left-list.conf",
        "numeric-obj-array/na03b-concat-right-list.conf",
        "numeric-obj-array/na03c-concat-two-objs.conf",
        "numeric-obj-array/na03d-concat-multi-piece.conf",
        "numeric-obj-array/na03e-multi-piece-overlap.conf",
        "numeric-obj-array/na04-empty.conf",
        "numeric-obj-array/na05-non-int-keys.conf",
        "numeric-obj-array/na06-gaps.conf",
        "numeric-obj-array/na07-sort.conf",
        "numeric-obj-array/na08-leading-zero.conf",
        "numeric-obj-array/na09-negative.conf",
        "numeric-obj-array/na10a-plus-sign.conf",
        "numeric-obj-array/na10b-minus-zero.conf",
        "numeric-obj-array/na11-overflow.conf",
        "numeric-obj-array/na12-no-eligible.conf",
        // concat-errors success fixtures (regression guards: ce09 S15 bridge, ce15 optional-omission)
        "concat-errors/ce09-numeric-obj-still-works.conf",
        "concat-errors/ce15-optional-missing-suppresses-pair.conf",
        // env-var-list fixtures (S13c): processed via EnvVarListExpander with .env
        // sidecar. EnvVarListExpander pre-expands ${X[]} textually against the .env
        // values; it was originally a workaround for typesafe-config <=1.4.3 not
        // supporting ${X[]} natively, but is still used in 1.4.6+ for hermeticity —
        // the .env sidecar baked into source removes any dependency on host env vars.
        //
        // EXCEPTION (ev12c, below): the cross-source case — ${X[]} in an included
        // file with X defined as config in the including file — cannot be modeled by
        // EnvVarListExpander's per-file isDefinedInConfig check. ev12c ships WITHOUT
        // a .env sidecar and routes through the native Lightbend 1.4.6 path
        // (the `else` branch in the SUCCESS_CONFS loop), which has full resolver
        // semantics including S14c.2 original-path fallback. See xx.hocon#22.
        "env-var-list/ev01-basic.conf",
        "env-var-list/ev02-stops-at-gap.conf",
        // ev03-required-no-elements.conf is in ENV_VAR_LIST_ERROR_CONFS (expected resolve error)
        "env-var-list/ev04-optional-no-elements.conf",
        "env-var-list/ev05-config-defined-wins.conf",
        "env-var-list/ev06-concat-prepend.conf",
        "env-var-list/ev07-concat-append.conf",
        "env-var-list/ev08-self-append.conf",
        "env-var-list/ev09-whitespace-before-suffix.conf",
        "env-var-list/ev10-empty-string-element.conf",
        "env-var-list/ev11-include-context.conf",
        // ev12b: S13c.5 — list suffix suppresses scalar env-var fallback (optional form).
        // S13C_EV12_X=scalar in env (no _0), ${?S13C_EV12_X[]} → key removed → {}.
        // Companion to ev12a (required form → error in ENV_VAR_LIST_ERROR_CONFS below).
        "env-var-list/ev12b-list-suffix-suppresses-scalar-fallback-optional.conf",
        // ev12c: E6 cross-source — ${X[]} in an included file when X is
        // config-defined in the including file at root. NO .env sidecar:
        // routes through the native Lightbend 1.4.6 path (not EnvVarListExpander)
        // because cross-source config-vs-env precedence cannot be modeled by
        // EnvVarListExpander's per-file pre-expansion. Pins xx.hocon#22 spec
        // posture: config exhaust (prefixed + original-path) before env list.
        "env-var-list/ev12c-include-config-defined-wins.conf",
        // ev13: S13c — optional list expansion, direct (not inside concat).
        // Isolates ${?S13C_EV13_MY_LIST[]} with _0=a set → {"x": ["a"]}.
        // Complements ev06/ev07 (which embed ${?X[]} inside concat expressions).
        "env-var-list/ev13-optional-list-direct.conf",
        // S8.6 unquoted-string-starts fixtures (cluster 3c, E8 rewritten 2026-05-20).
        // E8 now adopts Lightbend's pragmatic reading of HOCON.md L270-276 "begin" as
        // value-position begin (not token-position at any lexer offset). See
        // docs/extra-spec-conventions.md#e8 and xx.hocon issue #31.
        //
        // Lightbend-aligned fixtures (us02/us03/us13 moved here from per-impl error overrides):
        //   - us02 (`a = -foo`) → {"a":"-foo"}  (`-` not followed by digit → unquoted)
        //   - us03 (`a = -`)    → {"a":"-"}     (same)
        //   - us13 (`a = 01`)   → {"a":1}       (Java numeric semantics, leading-zero handled)
        //
        // New concat-continuation fixtures (us17–us30, added with E8 rewrite — probe matrix
        // groups A/B/D/E): cover ${a}-bar, ${a}-, ${a}--bar, ${a}-1, ${a}1bar, ${a}.bar,
        // ${a}_bar, "foo"-bar, "foo".bar, "foo"1bar, ${a}-${a}, ${a}-${b}, foo-${a},
        // "foo"-${a}.
        //
        // SIDECAR_ERROR_CONFS still handles us15 (`a = 1e+x`) — `+` is reserved (HOCON `+=`
        // operator), error in both value-start and concat-continuation positions.
        "unquoted-starts/us01-digit-prefix-with-tail.conf",
        "unquoted-starts/us02-hyphen-no-digit.conf",
        "unquoted-starts/us03-hyphen-alone.conf",
        "unquoted-starts/us04-hyphen-with-digit.conf",
        "unquoted-starts/us05-number-then-comment.conf",
        "unquoted-starts/us06-embedded-digits.conf",
        "unquoted-starts/us07-embedded-hyphen.conf",
        "unquoted-starts/us08-numeric-key-positive.conf",
        "unquoted-starts/us09-dotted-number-key.conf",
        "unquoted-starts/us10-greedy-backtrack-exp.conf",
        "unquoted-starts/us11-greedy-backtrack-frac.conf",
        "unquoted-starts/us12-hex-prefix.conf",
        "unquoted-starts/us13-leading-zero.conf",
        "unquoted-starts/us14-multi-dot-version.conf",
        "unquoted-starts/us16-negative-with-tail.conf",
        "unquoted-starts/us17-concat-subst-dash-text.conf",
        "unquoted-starts/us18-concat-subst-dash-only.conf",
        "unquoted-starts/us19-concat-subst-double-dash.conf",
        "unquoted-starts/us20-concat-subst-dash-digit.conf",
        "unquoted-starts/us21-concat-subst-digit-text.conf",
        "unquoted-starts/us22-concat-subst-dot-text.conf",
        "unquoted-starts/us23-concat-subst-underscore.conf",
        "unquoted-starts/us24-concat-quoted-dash-text.conf",
        "unquoted-starts/us25-concat-quoted-dot-text.conf",
        "unquoted-starts/us26-concat-quoted-digit-text.conf",
        "unquoted-starts/us27-concat-subst-dash-subst.conf",
        "unquoted-starts/us28-concat-subst-dash-subst-other.conf",
        "unquoted-starts/us29-concat-unquoted-dash-subst.conf",
        "unquoted-starts/us30-concat-quoted-dash-subst.conf",
        // S8.1/S8.8 unquoted-parens — clarifying fixtures for issue #34 (@cgordon).
        // HOCON.md L274 forbidden set does NOT include `(` or `)`; parens are special
        // only inside include resource syntax (`file(...)`, `required(...)`,
        // `classpath(...)`, `url(...)`). Outside of that contextual use, `(` and `)`
        // are ordinary unquoted-string content. ts.hocon and rs.hocon already match
        // this reading; go.hocon currently emits TokenLParen/TokenRParen as standalone
        // tokens unconditionally — these fixtures pin the cross-impl spec target so
        // go.hocon's fix (tracked at o3co/go.hocon#100) has a Lightbend-faithful
        // ground truth to converge on.
        "unquoted-parens/up01-paren-mid-token.conf",
        "unquoted-parens/up02-paren-leading.conf",
        "unquoted-parens/up03-paren-real-world.conf",
        "unquoted-parens/up04-paren-nested.conf",
        "unquoted-parens/up05-paren-unbalanced-open.conf",
        "unquoted-parens/up06-paren-unbalanced-close.conf",
        // S12.5 include-reservation positive fixtures (cluster 3e). Negative fixtures
        // distributed between SIDECAR_ERROR_CONFS (Lightbend throws) and Lightbend-quirk
        // exclusions (Lightbend silently accepts dotted `include.foo`). See E9 in
        // docs/extra-spec-conventions.md and "Lightbend quirks" in docs/fixture-conventions.md.
        "include-reservation/ir05-include-statement.conf",
        "include-reservation/ir06-quoted-include.conf",
        "include-reservation/ir07-include-non-initial.conf",
        "include-reservation/ir08-include-as-value.conf",
        "include-reservation/ir09-include-file-form.conf",
        "include-reservation/ir11-quoted-include-dotted.conf",
        "include-reservation/ir14-substitution-include-path.conf",
        // S13a.13 self-ref look-back fixtures (cluster 3f). Per-impl bug (ts/rs/go produce
        // "foofoo" for `a = ${?a}foo` with no prior `a`; spec/Lightbend produce "foo").
        // All 10 positive fixtures are Lightbend-spec-conformant. sr05 (required no prior)
        // is the only error fixture and lives in SIDECAR_ERROR_CONFS below.
        "self-ref-lookback/sr01-optional-no-prior.conf",
        "self-ref-lookback/sr02-optional-no-prior-leading.conf",
        "self-ref-lookback/sr03-optional-no-prior-both-sides.conf",
        "self-ref-lookback/sr04-optional-with-prior.conf",
        "self-ref-lookback/sr06-required-with-prior.conf",
        "self-ref-lookback/sr07-array-optional-no-prior.conf",
        "self-ref-lookback/sr08-array-optional-with-prior.conf",
        "self-ref-lookback/sr09-nested-no-prior.conf",
        "self-ref-lookback/sr10-nested-with-prior.conf",
        "self-ref-lookback/sr11-mutual-ref-forward.conf",
        // S13a.x follow-ups (xx.hocon#27) — fixtures pinning the 4 cross-impl resolver
        // bugs surfaced by S13a.13 cluster 3f Round 2 review. All 5 are Lightbend-
        // spec-conformant (typesafe-config 1.4.3 probe is the authoritative oracle).
        // Bug coverage: sr12/sr13 = nested external ref (Bug #1); sr14 = cache pollution
        // on prior-with-external-ref (Bug #2); sr15 = same-field double-self-ref (Bug
        // #3); sr16 = order-dependent external-then-self-ref (Bug #4).
        "self-ref-lookback/sr12-nested-external-ref-no-prior.conf",
        "self-ref-lookback/sr13-nested-external-ref-with-prior.conf",
        "self-ref-lookback/sr14-cache-prior-external.conf",
        "self-ref-lookback/sr15-double-self-ref.conf",
        "self-ref-lookback/sr16-external-before-self-ref.conf",
        // S3.1 empty-file fixtures (cluster 3h). An empty document is valid HOCON
        // parsing to `{}` (L134 brace-omission relaxation; L130-132 is the JSON
        // baseline). The `{}` sidecars emitted here are normative — per-impl
        // conformance tests assert them directly, no override list. (The original
        // cluster 3h reject-posture was revoked 2026-07-23; see E10 in
        // docs/extra-spec-conventions.md.)
        "empty-file/ef01-empty.conf",
        "empty-file/ef02-whitespace-only.conf",
        "empty-file/ef03-newlines-only.conf",
        "empty-file/ef04-comment-only.conf",
        "empty-file/ef05-bom-only.conf",
        "empty-file/ef06-mixed-ws-comment.conf",
        // S21.4 single-letter byte abbreviation fixtures (cluster 3h). Lightbend
        // ground truth is binary (powers of two): K=1024, M=1048576, etc.
        // Per-impl tests call getBytes("b") and assert numeric byte counts;
        // the -expected.json sidecars pin only the raw string value preserved
        // through parse (the byte-count assertion is at the accessor layer).
        "byte-single-letter/bsl01-1K.conf",
        "byte-single-letter/bsl02-1k.conf",
        "byte-single-letter/bsl03-1M.conf",
        "byte-single-letter/bsl04-1G.conf",
        "byte-single-letter/bsl05-1T.conf",
        "byte-single-letter/bsl06-1P.conf",
        "byte-single-letter/bsl07-1E.conf",
        "byte-single-letter/bsl08-1024K.conf",
        "byte-single-letter/bsl09-05K.conf",
        // S8.6 in key position (xx.hocon issue #42, v1.5.3). Lightbend accepts hyphen-start
        // segments in field keys when the key is built via space-concat (S10.8), via dot
        // (S11.1), or as the first token in a key path. The S8.6 "begin with `-` requires
        // digit" rule was always intended for value-position lexing; key-position is governed
        // by path-element parsing rules. ts/rs/go previously over-enforced S8.6 on each key
        // segment regardless of position — these fixtures pin the Lightbend-aligned behavior.
        // See docs/extra-spec-conventions.md E13.
        "key-hyphen-position/kh01-space-concat-hyphen-tail.conf",
        "key-hyphen-position/kh02-dotted-then-space-hyphen-tail.conf",
        "key-hyphen-position/kh03-quoted-then-space-hyphen-tail.conf",
        "key-hyphen-position/kh04-space-concat-dot-hyphen-start.conf",
        "key-hyphen-position/kh05-first-token-hyphen-start.conf",
        "key-hyphen-position/kh06-trailing-hyphen-only.conf",
        "key-hyphen-position/kh07-dot-hyphen-start-segment.conf",
        // kh08: hyphen-then-digit branch — distinguishes "S8.6 not enforced at all in key"
        // from "S8.6 still triggers number-lex on hyphen-then-digit". Lightbend accepts
        // `foo -1bar = 1` verbatim as a single space-concat key. Without this fixture an impl
        // could pass kh01-kh07 (which all use `-` not-followed-by-digit) while still applying
        // value-side number-lex to the `-1bar` segment.
        "key-hyphen-position/kh08-space-concat-hyphen-digit-tail.conf",
        // Path-expression whitespace preservation (xx.hocon issue #42 comment, v1.5.3).
        // Lightbend preserves literal whitespace adjacent to dots in path expressions —
        // the segment between two dots (or between a dot and the key/value separator) is
        // taken verbatim from the source. ts/rs/go previously stripped leading whitespace
        // on segments following a dot. These fixtures pin the Lightbend-aligned behavior.
        // pw06 is the boundary case: a trailing dot before separator still errors (BadPath),
        // i.e. whitespace adjacency does not relax the trailing-dot rule. pw04 has no
        // whitespace adjacent to a dot — it is a combined-regression guard verifying the
        // path-WS loosening does not regress the S10.8-only case. pw07 pins HOCON_WS tab
        // coverage; HOCON_WS includes tab so the path-WS rule must preserve it the same
        // way it preserves space.
        "path-expr-whitespace/pw01-space-after-dot.conf",
        "path-expr-whitespace/pw02-space-both-sides-of-dot.conf",
        "path-expr-whitespace/pw03-space-before-dot.conf",
        "path-expr-whitespace/pw04-space-concat-both-segments.conf",
        "path-expr-whitespace/pw05-multi-whitespace-both-sides.conf",
        "path-expr-whitespace/pw07-tab-after-dot.conf",
        // include-env-fallback fixtures (go.hocon#128 cross-impl). Pin the
        // include-child `${?ENV}` env-with-default pattern: when the env var
        // is unset, the prior duplicate-key assignment must remain — the
        // include boundary is invisible to S7.1 + S13.2/S13.11 + S14b.2
        // duplicate-key + optional-substitution semantics. go.hocon v1.4.1–
        // v1.5.2 dropped this prior across a separate lenient-resolve pass
        // (priorValues stripped at the include-child boundary), fixed in
        // go.hocon v1.5.3 / #129. These fixtures route through the native
        // Lightbend path with `setUseSystemEnvironment(false)` (see HERMETIC_
        // NO_ENV_GROUPS below) so the env-unset case is bit-exact-stable
        // regardless of the generator's host environment.
        "include-env-fallback/iev01-env-unset-preserves-prior-default.conf",
    };

    // Fixture groups that must be generated with system environment access
    // disabled, so the expected JSON is bit-exact-stable across machines.
    // Used for `${?ENV}`-fallback-shaped fixtures where the bug case under
    // test is the env-UNSET branch — if a host happened to define the same
    // env name, the generator would silently bake the host value into the
    // expected JSON. setUseSystemEnvironment(false) at resolve time forces
    // the optional substitution to its undefined branch.
    static final String[] HERMETIC_NO_ENV_GROUPS = {
        "include-env-fallback/",
    };

    // S23.4 properties-conflict fixtures (cluster 3h). Lightbend's direct
    // ConfigFactory.parseFile(...properties) applies the spec L1485 "object wins"
    // rule correctly (within a single .properties file, dotted keys always win
    // over scalar conflicts regardless of input order). Lightbend's `include`
    // code path through a wrapping .conf is order-dependent and does NOT match
    // spec — we deliberately bypass `include` here and parse the .properties
    // directly to obtain spec-compliant ground truth. Per-impl tests load the
    // .properties via each impl's parseProperties() / propsToObjectVal()
    // equivalent (NOT via include), asserting the resulting nested object.
    static final String[] PROPERTIES_CONFS = {
        "properties-conflict/pc01-forward.properties",
        "properties-conflict/pc02-reverse.properties",
        "properties-conflict/pc03-deep-forward.properties",
        "properties-conflict/pc04-deep-reverse.properties",
    };

    // Conf files expected to produce a parse/resolve error and have a plain-text
    // .error sidecar written to expected/hocon/<group>/<name>.error.
    // Format: "Exception class: <fqcn>\nMessage: <message>"
    // Used by cross-impl conformance tests to assert "any HoconError raised"
    // (no exact message matching required). Shared convention for clusters 3b, 3c, 3e, 3f
    // and any future cluster using the .error sidecar pattern (see docs/fixture-conventions.md).
    static final String[] SIDECAR_ERROR_CONFS = {
        "concat-errors/ce01-array-plus-object.conf",
        "concat-errors/ce02-object-plus-array.conf",
        "concat-errors/ce03-array-plus-scalar.conf",
        "concat-errors/ce04-scalar-plus-array.conf",
        // ce05-object-plus-scalar.conf: EXCLUDED — Lightbend 1.4.3 silently accepts
        // "a = { b: 1 } x" (object wins, trailing scalar discarded). Spec S10.13 says
        // this should error, but Lightbend is permissive here. Fixture file is kept on
        // disk for documentation; conformance tests for ce05 must use a Lightbend-quirk
        // annotation rather than a .error sidecar. See docs/fixture-conventions.md §Lightbend
        // quirks.
        "concat-errors/ce06-scalar-plus-object.conf",
        "concat-errors/ce07-subst-obj-plus-array.conf",
        "concat-errors/ce08-subst-array-plus-obj.conf",
        "concat-errors/ce10-empty-array-plus-object.conf",
        "concat-errors/ce11-array-plus-empty-object.conf",
        "concat-errors/ce12-string-concat-resolved-array.conf",
        "concat-errors/ce13-string-concat-resolved-object.conf",
        "concat-errors/ce14-optional-missing-mid-concat.conf",
        // S8.6 us15: Lightbend errors on reserved `+` outside quotes in the unquoted tail.
        // Strict-spec impl would also lex-error (number(1) backtrack from `e+`, then `+` as
        // reserved char). Both produce "some error" — conformance test asserts error raised.
        "unquoted-starts/us15-incomplete-exp.conf",
        // S12.5 include-reservation negative fixtures (cluster 3e). Lightbend throws on
        // ir01/ir02/ir10/ir12/ir13 via the include-statement parser (text after `include`
        // is not a valid include argument). Lightbend EXCLUDES (quirks):
        //   - ir03 `include.foo = 1`: tokenizer joins into single unquoted, PathParser
        //     splits later → silently accepted as key path
        //   - ir04 `a = { include.bar = 1 }`: same mechanism inside nested object
        // Both are documented under §Lightbend quirks in docs/fixture-conventions.md and
        // E9 in docs/extra-spec-conventions.md. Per-impl tests load ir03/ir04 directly.
        "include-reservation/ir01-include-equals.conf",
        "include-reservation/ir02-include-colon.conf",
        "include-reservation/ir10-include-plus-equals.conf",
        "include-reservation/ir12-include-newline-arg.conf",
        "include-reservation/ir13-include-object-body.conf",
        // S13a.13 sr05: `a = ${a}foo` with no prior `a` — required self-ref to undefined
        // value. Lightbend throws UnresolvedSubstitution. Strict-spec impl behaviour is
        // identical: required substitution to no-prior is an error regardless of
        // optional/required asymmetry — the fix only changes optional from "foofoo"
        // resolution to undefined; required already errors today.
        "self-ref-lookback/sr05-required-no-prior.conf",
        // Path-expression whitespace pw06: `a b. = 1` — trailing dot before separator.
        // Lightbend throws BadPath ("path has a leading, trailing, or two adjacent period").
        // Pins that whitespace adjacency does not relax the trailing-dot rule even after
        // we loosen key parsing in companion fixtures pw01-pw05 (xx.hocon issue #42, v1.5.3).
        "path-expr-whitespace/pw06-trailing-dot-before-separator.conf",
        // S3.5 array-root fixtures: an array-root document is VALID HOCON at the
        // document level (HOCON.md L989-991: "both JSON and HOCON allow arrays as
        // root values"), but the object-rooted Config API rejects it with a TYPE
        // error, not a syntax error — Lightbend parses the document then throws
        // ConfigException.WrongType in Parseable.forceParsedToObject ("object at
        // file root" vs LIST). ar03 pins S14b.1: an INCLUDED file with an array
        // root is invalid per HOCON.md L993-994. Per-impl tests assert an error
        // of the impl's type-mismatch class raised after a successful syntax
        // parse (not "expected key").
        "array-root/ar01-basic.conf",
        "array-root/ar02-array-of-objects.conf",
        "array-root/ar03-include-array-root.conf",
    };

    // Conf files that should produce a parse/resolve error (traditional JSON error record format)
    static final String[] ERROR_CONFS = {
        "cycle.conf",
        "test13-reference-bad-substitutions.conf",
        "subst-tokenize/st-err01-invalid-escape-x.conf",
        "subst-tokenize/st-err02-invalid-escape-q.conf",
        "subst-tokenize/st-err03-invalid-unicode-short.conf",
        "subst-tokenize/st-err04-invalid-unicode-nonhex.conf",
        "subst-tokenize/st-err05-unterminated-subst.conf",
        "subst-tokenize/st-err06-unterminated-string.conf",
        "subst-tokenize/st-err07-newline-in-string.conf",
        "subst-tokenize/st-err08-empty-path.conf",
        "subst-tokenize/st-err09-empty-segment-leading-dot.conf",
        "subst-tokenize/st-err10-empty-segment-trailing-dot.conf",
        "subst-tokenize/st-err11-empty-segment-double-dot.conf",
    };

    // env-var-list error fixtures: these use ${X[]} syntax so require sidecar-based
    // expansion before we can determine if they error. The generator pre-processes the
    // source text to expand ${X[]} patterns; if the expanded form still has an unresolvable
    // substitution (no env vars set for a required subst), Lightbend throws.
    static final String[] ENV_VAR_LIST_ERROR_CONFS = {
        // ev03: required ${X[]} with no env elements → UnresolvedSubstitution error
        "env-var-list/ev03-required-no-elements.conf",
        // ev12a: S13c.5 — list suffix suppresses scalar env-var fallback (required form).
        // S13C_EV12_X=scalar in env (no _0), ${S13C_EV12_X[]} → ResolveError.
        // Pins that the bare scalar S13C_EV12_X must NOT be consulted as fallback
        // when listSuffix=true and no _0 is present.
        "env-var-list/ev12a-list-suffix-suppresses-scalar-fallback-required.conf",
    };

    static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public static void main(String[] args) throws Exception {
        Path testdataDir = Path.of("../testdata/hocon");
        Path expectedDir = Path.of("../expected/hocon");
        Files.createDirectories(expectedDir);

        System.out.println("Generating expected JSON from typesafe-config...");
        System.out.println("testdata dir: " + testdataDir.toAbsolutePath());
        System.out.println();

        int okCount = 0;
        int errCount = 0;
        int skipCount = 0;

        for (String confName : SUCCESS_CONFS) {
            Path confPath = testdataDir.resolve(confName);
            if (!Files.exists(confPath)) {
                System.err.println("SKIP (not found): " + confName);
                skipCount++;
                continue;
            }

            // Check for .env sidecar — use EnvVarListExpander if present
            Path sidecarPath = testdataDir.resolve(confName.replace(".conf", ".env"));
            boolean hasSidecar = Files.exists(sidecarPath);

            try {
                String json;
                if (hasSidecar) {
                    Map<String, String> env = EnvVarListExpander.loadEnvSidecar(sidecarPath);
                    json = EnvVarListExpander.generateJson(confPath, env);
                } else {
                    ConfigParseOptions parseOpts = ConfigParseOptions.defaults()
                        .setOriginDescription(confName);
                    boolean hermeticNoEnv = false;
                    for (String prefix : HERMETIC_NO_ENV_GROUPS) {
                        if (confName.startsWith(prefix)) { hermeticNoEnv = true; break; }
                    }
                    ConfigResolveOptions resolveOpts = hermeticNoEnv
                        ? ConfigResolveOptions.defaults().setUseSystemEnvironment(false)
                        : ConfigResolveOptions.defaults();
                    Config config = ConfigFactory.parseFile(confPath.toFile(), parseOpts).resolve(resolveOpts);
                    ConfigObject root = config.root();
                    // Filter out environment-dependent keys that differ per machine.
                    // test01.conf has a top-level `system { home = ${?HOME}, ... }` block.
                    // test03.conf transitively includes test01 via `test01 { include "test01" }`,
                    // pulling the same `system` block into `test03.test01.system`. Both need
                    // filtering to keep generated JSON stable across machines.
                    if (confName.equals("test01.conf")) {
                        root = filterPath(root, "system");
                    } else if (confName.equals("test03.conf")) {
                        root = filterPath(root, "test01.system");
                    }
                    json = toSortedJson(root) + "\n";
                }

                String outName = confName.replace(".conf", "-expected.json");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, json);
                System.out.println("  OK" + (hasSidecar ? " (sidecar)" : "") + ": " + confName + " -> " + outName);
                okCount++;
            } catch (Exception e) {
                System.err.println("  ERROR (unexpected): " + confName + ": " + e.getMessage());
                errCount++;
            }
        }

        // PROPERTIES_CONFS: .properties fixtures parsed directly (no .conf wrapper).
        // Lightbend's direct parseFile(.properties) is spec-compliant for the L1485
        // "object wins" rule, unlike the order-dependent `include "..properties"` path.
        for (String propsName : PROPERTIES_CONFS) {
            Path propsPath = testdataDir.resolve(propsName);
            if (!Files.exists(propsPath)) {
                System.err.println("SKIP (not found): " + propsName);
                skipCount++;
                continue;
            }
            try {
                Config config = ConfigFactory.parseFile(propsPath.toFile());
                ConfigObject root = config.root();
                String json = toSortedJson(root) + "\n";
                String outName = propsName.replace(".properties", "-expected.json");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, json);
                System.out.println("  OK (.properties): " + propsName + " -> " + outName);
                okCount++;
            } catch (Exception e) {
                System.err.println("  ERROR (unexpected): " + propsName + ": " + e.getMessage());
                errCount++;
            }
        }

        for (String confName : ERROR_CONFS) {
            Path confPath = testdataDir.resolve(confName);
            if (!Files.exists(confPath)) {
                System.err.println("SKIP (not found): " + confName);
                skipCount++;
                continue;
            }
            try {
                ConfigFactory.parseFile(confPath.toFile()).resolve();
                System.err.println("  UNEXPECTED SUCCESS: " + confName);
                errCount++;
            } catch (Exception e) {
                JsonObject errObj = new JsonObject();
                errObj.addProperty("error", true);
                errObj.addProperty("type", e.getClass().getSimpleName());
                errObj.addProperty("message", e.getMessage());
                String outName = confName.replace(".conf", "-expected-error.json");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, GSON.toJson(errObj) + "\n");
                System.out.println("  OK (error): " + confName + " -> " + outName);
                okCount++;
            }
        }

        // ENV_VAR_LIST_ERROR_CONFS: error fixtures that use ${X[]} syntax. Pre-process via
        // EnvVarListExpander.expandListSubstitutions so the expanded form can be evaluated
        // by Lightbend; expect Lightbend to throw. Emits a plain-text .error sidecar (same
        // format as SIDECAR_ERROR_CONFS) and applies the same UNEXPECTED-SUCCESS safety net.
        // setUseSystemEnvironment(false) ensures the expanded fixture cannot leak through
        // to host env / system properties — same hermeticity guarantee as EnvVarListExpander.
        for (String confName : ENV_VAR_LIST_ERROR_CONFS) {
            Path confPath = testdataDir.resolve(confName);
            if (!Files.exists(confPath)) {
                System.err.println("SKIP (not found): " + confName);
                skipCount++;
                continue;
            }
            Path sidecarPath = testdataDir.resolve(confName.replace(".conf", ".env"));
            Map<String, String> env;
            if (Files.exists(sidecarPath)) {
                env = EnvVarListExpander.loadEnvSidecar(sidecarPath);
            } else {
                // The safety net only catches "did NOT throw"; it cannot detect "threw the
                // wrong error". Surface missing-sidecar so a fixture author who forgot the
                // .env file gets a clear breadcrumb instead of a confusing vacuous error.
                System.err.println("  WARN: no .env sidecar for " + confName + " — proceeding with empty env");
                env = new LinkedHashMap<>();
            }
            String source = Files.readString(confPath);
            String processed = EnvVarListExpander.expandListSubstitutions(source, env);
            // Origin appears in Lightbend's exception message and lands in the .error sidecar.
            // We go through parseString (because the source is pre-expanded), so we set the
            // origin manually to the short conf path. This differs from SIDECAR_ERROR_CONFS
            // which uses parseFile and gets `../testdata/hocon/<group>/<name>.conf` as origin.
            // Per fixture-conventions.md §Semantics, conformance tests MUST NOT match on
            // message content, so the format difference is purely cosmetic.
            ConfigParseOptions parseOpts = ConfigParseOptions.defaults()
                .setOriginDescription(confName)
                .setSyntax(ConfigSyntax.CONF);
            ConfigResolveOptions resolveOpts = ConfigResolveOptions.defaults()
                .setUseSystemEnvironment(false);
            Exception caught = null;
            try {
                ConfigFactory.parseString(processed, parseOpts).resolve(resolveOpts);
            } catch (Exception e) {
                caught = e;
            }
            if (caught == null) {
                throw new RuntimeException(
                    "Expected error fixture " + confName + " did NOT throw — verify the .env sidecar "
                    + "(missing keys?) or move to SUCCESS_CONFS per docs/fixture-conventions.md.");
            }
            {
                Exception e = caught;
                String sidecar = "Exception class: " + e.getClass().getName() + "\n"
                               + "Message: " + e.getMessage() + "\n";
                String outName = confName.replace(".conf", ".error");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, sidecar);
                System.out.println("  OK (.error sidecar): " + confName + " -> " + outName);
                okCount++;
            }
        }

        // SIDECAR_ERROR_CONFS: fixtures expected to fail; write plain-text .error sidecars.
        // Sidecar format:
        //   Exception class: <fully-qualified class name>
        //   Message: <exception message verbatim>
        // Existence of the sidecar signals "expected to fail" to cross-impl test runners.
        for (String confName : SIDECAR_ERROR_CONFS) {
            Path confPath = testdataDir.resolve(confName);
            if (!Files.exists(confPath)) {
                System.err.println("SKIP (not found): " + confName);
                skipCount++;
                continue;
            }
            Exception caught = null;
            try {
                ConfigFactory.parseFile(confPath.toFile()).resolve();
            } catch (Exception e) {
                caught = e;
            }
            if (caught == null) {
                // FAIL the build: an entry in SIDECAR_ERROR_CONFS must throw under Lightbend.
                // If Lightbend silently accepts (e.g. ce05-style Lightbend quirk), the fixture
                // MUST be moved out of SIDECAR_ERROR_CONFS and documented under §Lightbend
                // quirks in docs/fixture-conventions.md. This safety net catches both regressions
                // (Lightbend used to throw, now doesn't) and typos (fixture content doesn't
                // actually trigger the expected error).
                throw new RuntimeException(
                    "Expected error fixture " + confName + " did NOT throw — verify the fixture input "
                    + "or move it to a Lightbend-quirk section per docs/fixture-conventions.md.");
            }
            {
                Exception e = caught;
                String sidecar = "Exception class: " + e.getClass().getName() + "\n"
                               + "Message: " + e.getMessage() + "\n";
                String outName = confName.replace(".conf", ".error");
                Path outPath = expectedDir.resolve(outName);
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, sidecar);
                System.out.println("  OK (.error sidecar): " + confName + " -> " + outName);
                okCount++;
            }
        }

        System.out.println();
        System.out.printf("Done. OK: %d, Errors: %d, Skipped: %d%n", okCount, errCount, skipCount);

        // --- E12 deferred-resolution scenarios ---
        // Each dr*.yaml is a multi-step scenario run through Lightbend and emits:
        //   <name>-expected.json  (success)
        //   <name>-expected.txt   (isResolved + getter assertions)
        //   <name>-expected.error (error scenarios)
        //   <name>-expected.skip  (lightbendSkip: true)
        // See DeferredResolutionRunner and testdata/hocon/deferred-resolution/README.md.
        DeferredResolutionRunner.runAll(testdataDir, expectedDir);
    }

    /**
     * Remove a dot-separated nested key path from a ConfigObject.
     *
     * Single-segment path (no dots): removes the named top-level key.
     * Nested path (e.g. "test01.system"): descends into the head segment,
     * recursively filters the tail from that subtree, and rebuilds. If any
     * intermediate segment is missing or non-object, returns input unchanged.
     *
     * Used to strip machine-dependent subtrees from generated expected JSON
     * (e.g. test01.conf's `system { home = ${?HOME}, path = ${?PATH}, ... }`
     * block, which resolves to user-specific values).
     *
     * LIMITATION: path segments are split on '.' literally. HOCON quoted-dot
     * keys (e.g. `"a.b.c" = 1` — single key `a.b.c`) are NOT supported as
     * segments; passing `"a.b.c"` here would split into 3 segments and try
     * to descend `a → b → c`. Current call sites use unquoted single-dot
     * paths only ("system", "test01.system"), so this is fine in practice;
     * if a future caller needs quoted dotted keys, refactor to accept
     * `String... segments` instead.
     */
    static ConfigObject filterPath(ConfigObject obj, String dotPath) {
        int dotIdx = dotPath.indexOf('.');
        if (dotIdx < 0) {
            Map<String, ConfigValue> filtered = new HashMap<>(obj);
            filtered.remove(dotPath);
            return ConfigValueFactory.fromMap(filtered).toConfig().root();
        }
        String head = dotPath.substring(0, dotIdx);
        String tail = dotPath.substring(dotIdx + 1);
        ConfigValue child = obj.get(head);
        if (!(child instanceof ConfigObject)) {
            return obj;
        }
        ConfigObject filteredChild = filterPath((ConfigObject) child, tail);
        Map<String, ConfigValue> rebuilt = new HashMap<>(obj);
        rebuilt.put(head, filteredChild);
        return ConfigValueFactory.fromMap(rebuilt).toConfig().root();
    }

    static String toSortedJson(ConfigObject root) {
        JsonElement el = configValueToJson(root);
        return GSON.toJson(el);
    }

    static JsonElement configValueToJson(ConfigValue value) {
        switch (value.valueType()) {
            case OBJECT: {
                ConfigObject obj = (ConfigObject) value;
                JsonObject json = new JsonObject();
                TreeMap<String, ConfigValue> sorted = new TreeMap<>(obj);
                for (Map.Entry<String, ConfigValue> e : sorted.entrySet()) {
                    json.add(e.getKey(), configValueToJson(e.getValue()));
                }
                return json;
            }
            case LIST: {
                ConfigList list = (ConfigList) value;
                JsonArray arr = new JsonArray();
                for (ConfigValue v : list) {
                    arr.add(configValueToJson(v));
                }
                return arr;
            }
            case NUMBER: {
                Number n = (Number) value.unwrapped();
                if (n instanceof Double || n instanceof Float) {
                    return new JsonPrimitive(n.doubleValue());
                }
                return new JsonPrimitive(n.longValue());
            }
            case BOOLEAN:
                return new JsonPrimitive((Boolean) value.unwrapped());
            case NULL:
                return JsonNull.INSTANCE;
            case STRING:
                return new JsonPrimitive((String) value.unwrapped());
            default:
                throw new RuntimeException("Unknown type: " + value.valueType());
        }
    }
}
