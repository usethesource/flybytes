# Flybytes - intermediate abstract programming language for fast generation of JVM bytecode

Flybytes is an intermediate language towards JVM bytecode generation for Rascal-based compilers of Domain Specific Languages and Programming Languages.

### Context:

* you are implementing a textual or graphical DSL or a programming language using Rascal
* you want to target the JVM because of its general availability and the JIT compiler
* you do not have time to get into the hairy details of JVM bytecode generation, and do not have time for debugging on the JVM bytecode level
* you do want to profit from the Just In Time (JIT) compiler, so you need idiomatic JVM bytecode that the JIT compiler understands
* you understand the Java programming language pretty well
* you could generate Java code but that would be too slow and require a JDK as a dependency, or you need `invokedynamic` support for your language which Java does not offer.

### Solution:

1. Flybytes is an intermediate abstract syntax tree format that looks a lot like abstract syntax trees for Java code
1. You translate your own abstract syntax trees for your own language directly to Flybytes ASTs using Rascal
1. The Flybytes compiler use the [ASM framework](https://asm.ow2.io/) to generate bytecode in a single pass of the Flybytes AST
   * either the code is directly streamed to a class file (and optionally loaded)
   * or a reasonably clear error message is produced due to an error in the FlyBytes AST.
   
### Presumptions:

* Flybytes does not cover a priori type checking of the input Flybytes AST. So, a proper application of the Flybytes compiler assumes:
   * Your DSL has its own type checker and the compiler is not called if the input code still has serious type errors or name resolution errors (but you could also generate error nodes for partial compilation support)
   * Your compiler to Flybytes ASTs does not introduce new type errors with respect to the JVM's type system.
* Flybytes does not cover much name resolution, so for imports, foreign names and such you have to provide fully qualified names while generating Flybytes ASTs

### Features:

* Protection from ASM and JVM crashes: the Flybytes compiler does some on-the-fly type checking and error reporting in case you generated something weird.
* Tries to generate JVM bytecode that looks like it could have come from a Java compiler
* Offers many Java-like (high-level programming language) features:
   1. local variable names
   1. formal parameter names
   1. structured control flow: if, while, do-while, try-catch-finally, for, break, continue, return, switch
   1. monitor blocks
   1. full expression language (but no short-circuiting for the booleans!)
   1. class, method, and variable annotations 
   1. method invocation specialized towards specific JVM instructions (for efficiency's sake)
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
