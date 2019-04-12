# Flybytes - intermediate abstract programming language for fast generation of JVM bytecode

Flybytes is an intermediate language towards JVM bytecode generation for Rascal-based compilers of Domain Specific Languages and Programming Languages.

### Context:

* you are implementing a textual or graphical DSL or a programming language (using Rascal)
* and, you want to target the JVM because of its general availability and the JIT compiler, 
* or you want to target the JVM to interact with other JVM languages and libraries,
* or you need to analyze and rewrite bytecode,
* and, you do not have time or want to spend time to get into JVM bytecode analysis or generation, 
* and, you do want to profit from the Just In Time (JIT) compiler,
* and, you understand the Java programming language pretty well
* and, you could generate Java code as well 
* and, you require JVM debugging support for your language
* or you need `invokedynamic` support for your language (which Java does not offer).

### Solution:

1. Flybytes is an intermediate abstract syntax tree format that looks a lot like abstract syntax trees for Java code
1. You translate your own abstract syntax trees for your own language directly to Flybytes ASTs using Rascal
1. The Flybytes compiler use the [ASM framework](https://asm.ow2.io/) to generate bytecode in a single pass of the Flybytes AST
   * either the code is directly streamed to a class file (and optionally loaded)
   * or a reasonably clear error message is produced due to an error in the FlyBytes AST.
1. Also it can deconpile JVM bytecode back to statememts and expressions (almost done)
1. Flybytes does not require a JDK as a dependency. It uses only ASM to generate JVM bytecode, very quickly.
   
### Presumptions:

* Flybytes does not cover a priori type checking of the input Flybytes AST. So, a proper application of the Flybytes compiler assumes:
   * Your DSL has its own type checker and the compiler is not called if the input code still has serious type errors or name resolution errors (but you could also generate error nodes for partial compilation support)
   * Your compiler to Flybytes ASTs does not introduce new type errors with respect to the JVM's type system.
* Flybytes does not cover much name resolution, and thus also no overloading:
   * so no `import` statement
   * field names are always fully qualified
   * method names are always fully qualified and carry full descriptions of their parameter types
* Flybytes does not do short-circuit evaluation of the boolean operators `or` and `and`, so you have to use `cond` expressions to guarantee short-circuit evaluation where necessary yourself.
* Flybytes has (almost) the same type system as the JVM:
   * object types with fully qualified names (no generics) 
   * array types (which are restricted and final object types)
   * the root object type is `object("java.lang.Object")`
   * Flybytes has some kind of type system:
      * method resolution, like the JVM has, that method signatures are used as method names, and methods and fields are sought through an inheritance relation at run-time.
      * but otherwise Flybytes has no sub-typing or any static typing rules, that feature is left to your own language design
   * primitive types (integer, short, byte, long, character)
   * additionally Flybytes has a boolean type
   * additionally Flybytes has the string() types which is equivalent to `object("java.lang.String")`
   * additionally Flybytes offers a safe syntax for constructing and calling "bootstrap" methods for `invokedynamic`

### Features:

* Protection from ASM and JVM crashes: the Flybytes compiler does some on-the-fly type checking and error reporting in case you generated something weird.
* Tries to generate JVM bytecode that looks like it could have come from a Java compiler
* Offers many Java-like (high-level programming language) features:
   1. local variable names
   1. formal parameter names
   1. structured control flow: if, while, do-while, try-catch-finally, for, break, continue, return, switch
   1. monitor blocks
   1. full expression language (fully hides stack operations of JVM bytecode)
   1. class, method, and variable annotations 
   1. method invocation specialized towards specific JVM instructions (for efficiency's sake)
* Offers symbolic types and method descriptors (as opposed to mangled strings in JVM bytecode)
* Can generate JVM bytecode which would be type-incorrect for Java, but type-correct for the JVM.
* Additional dynamic language support via `invokedynamic` and construction and invocation of bootstrap methods
* Incrementally growing library of macros for typical program element snippets, such as loops over arrays and loops over iterables, etc.

### Status:

* Flybytes is experimental and currently in alpha stage. 
* Expect renamings and API changes
* The language is fully implemented, with the noted exception of nested classes
* The language is fully tested, with the noted exception of the invokedynamic feature

### TODO:

* add support for the JVM debugger (line and symbol information)
* add support for error nodes (to support partial compilation and running partially compiled classes)
* refactor compiler exceptions to Rascal exceptions (to help debugging Flybytes AST generators)
* add support for nested classes (helps in generating code for lambda expressions)

### Citations

The design of Flybtyes was informed by the JVM VM spec, the ASM library code and documentation and the Jitescript API:

* <https://docs.oracle.com/javase/specs/jvms/se8/jvms8.pdf>
* <https://asm.ow2.io/>
* <https://github.com/qmx/jitescript>
