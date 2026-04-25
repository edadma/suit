# sysl bug repros (open at suit time)

## sysl-shadow-bug.sysl + sysl-shadow-bug2.sysl

**Bug:** A pattern-binding name extracted from an enum variant whose field
type is a closure does not shadow a global function with the same name.
The analyzer resolves the call inside the match arm to the global function
and rejects the argument types.

**Symptom:**
```
$ sbt "syslCliJVM/run run sysl-shadow-bug.sysl"
SyslAnalyzer$AnalysisError: argument 'n' of 'render' expects Node, got Fiber
```

**Why it matters here:** Scope 2's Counter component (`engine-counter.sysl`)
keeps the function-component render in a `Component(render: (Fiber) -> View)`
variant alongside the engine's own `render(n: Node) -> int`. The match arm
pattern-binds `render` and calls it on a `Fiber` — should shadow, doesn't.

The corresponding test in `SyslHostSpec` is currently `ignore` with a TODO
referencing this directory. Once the analyzer applies pattern-binding shadow
for closure-typed variant fields, un-ignore it.

## sysl-shadow-bug2.sysl

Same bug, slimmer. Closure variant field `f: (string) -> int`; global
`f(n: int) -> int`. Match arm `Closure(f) -> f("hello")` — analyzer picks
the int→int global, rejects the string arg.

Note: the simpler case where the variant-field closure has the **same**
signature as the global function compiles fine (the call type-checks against
either resolution), so the bug only surfaces when the two signatures differ.
