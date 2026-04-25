package io.github.edadma.suit.sysl

import io.github.edadma.trisc.{SyslDriver, SyslInterpreter, Value, TProgram, TFunDecl}

import scala.collection.mutable

// SyslHost is the JVM-side bridge that lets a Sysl program drive the suit
// toolkit. It owns a SyslInterpreter, lets the host install JVM-implemented
// "extern" functions the Sysl source can call (register), and exposes a
// minimal compile/run API.
//
// Scope 0 — builtins only. No closure callbacks from JVM yet; if a Sysl
// program needs to wire a click handler, the round-trip happens through
// either polling (sysl asks the host "did button N change?") or a
// sysl-side event-pump loop where the host returns a closure value via a
// builtin and sysl invokes it itself. Both work without any patch to the
// interpreter.
//
// The bridge deliberately re-exports nothing about View / Node / Engine —
// those stay on the suit core side. A future SuitSyslBridge can layer on
// top to install builtins that build View trees from sysl calls.
final class SyslHost(baseDir: String):

  private val driver = new SyslDriver(baseDirs = List(baseDir))
  val interpreter: SyslInterpreter = new SyslInterpreter(s => print(s))

  // Compile the given source paths (anything driver.compile accepts) into
  // a single typed program. Tests are stripped — this is for "run my app"
  // not "run my test suite".
  def compile(sources: Map[String, String]): TProgram =
    val result   = driver.compile(sources)
    val typed    = TProgram(result.units.flatMap(_.typed.decls))
    stripTestDecls(typed)

  // Read a single file off disk and compile it. The path is taken relative
  // to the host's baseDir; the file extension can be `.sysl` or `.lsysl`.
  def compileFile(relativePath: String): TProgram =
    val full = baseDir + "/" + relativePath
    val raw  = scala.io.Source.fromFile(full).mkString
    val key  = relativePath.stripSuffix(".sysl").stripSuffix(".lsysl")
    compile(Map(key -> raw))

  // Install a JVM function as an extern. The `name` must match the name the
  // Sysl source uses; argument values are passed in source order, the
  // returned `Value` becomes the call's result.
  def register(name: String, fn: List[Value] => Value): Unit =
    interpreter.registerBuiltins(Map(name -> fn))

  def registerAll(fns: Map[String, List[Value] => Value]): Unit =
    interpreter.registerBuiltins(fns)

  // Run a typed program — this evaluates top-level declarations and then
  // calls `main` (the interpreter's entry-point convention).
  def run(program: TProgram): Long =
    interpreter.run(program)

  private def stripTestDecls(p: TProgram): TProgram =
    TProgram(p.decls.filter {
      case f: TFunDecl => !f.attributes.exists(_.name == "test")
      case _           => true
    })


// Convenience extractors for builtin handlers. Each takes the typical
// shape and unpacks Value variants so user code doesn't need to pattern
// match the whole enum manually.
object SyslHost:
  import Value.*

  def asString(v: Value): String = v match
    case StringVal(bytes) => new String(bytes, "UTF-8")
    case _                => sys.error(s"expected string, got $v")

  def asLong(v: Value): Long = v match
    case IntVal(n) => n
    case _         => sys.error(s"expected i64, got $v")

  def asBool(v: Value): Boolean = v match
    case IntVal(n) => n != 0
    case _         => sys.error(s"expected bool, got $v")

  // Common convenience returns.
  val unit:  Value = IntVal(0)
  def long(n: Long): Value = IntVal(n)
  def bool(b: Boolean): Value = IntVal(if b then 1 else 0)
  def string(s: String): Value = StringVal(s.getBytes("UTF-8"))
