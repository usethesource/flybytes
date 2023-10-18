## 0.2.3
* bumped rascal to 0.34.0 and the maven plugin to 0.22.0
## 0.2.2
* bumped rascal and rascal-maven-plugin
## 0.2.1 [maven-release-plugin] prepare release v0.2.1
* upped dependencies on rascal and the tutor
* small bug fixes
* added citation and funding 
* fixed #23
* fixed #24
## 0.2.0
* fixed #25
* added two tests for issue #25
## 0.1.10
* added missing break statement and added compilation of manual local variables for roundtripping purposes
* fixed #14
* improved accuracy of actual exceptions by not re-throwing them anymore
## 0.1.9
* added test for init array and made decompiler more flexible for init arrays
* added decompilation rule for arrays with initialization values to accomodate for missing cases of issue #22
* fixed the disassembler bug for arrays of issue #22
* fixed first bug #22
* added test for bug in issue #20
* fixed #20
* bumped asm dependency to latest version
## 0.1.8
* Fixed #21, a corner case that we did not catch before. When compiling with newer versions of Java and decompiling with newer versions of ASM, and no -g or -parameters flags are given, the list of locals is empty rather than null.
* Fixed modifier bug: `staticMethod(Type, str, list[Formal], list[Stat])` calls `staticMethod(Modifier, Type, str, list[Forma], list[Stat])` with the public modifier, but since it uses "\public" rather than "\public()", this gives a CallFailed error.
## 0.1.7
* bumped rascal-maven-plugin to 0.15.4
## 0.1.6 
* bumped maven plugin
## 0.1.5
* bumped maven-plugin for tutor
## 0.1.4
* bumped maven-plugin to 0.14.5 for prettier Package doc index page
## 0.1.3
* added References page
* initial docs
* reconfigured release plugin
## 0.1.1
*  bumped rascal-maven-plugin to 0.14.4
*  bumped to maven-plugin 0.14.3 for latest library package doc features in the tutor
## 0.1.0
* improved doc strings
* bumped rascal to 0.28.2
* enabled tutor compiler on the API
* set default class version to 11
* added version number support for Java 9 up to 18
* fixed #12, again
## 0.0.2
* added initial doc page for testing docusaurus project
* wired in sourcelocation classloaders to be able to deal with third-party dependencies. fixes #12
* added classpath parameter to mirrorClass
* removed dead metadata
* updated README.md
## 0.0.1
* commented out decompilation tests that need a fix after Java 11
* fixed getAnnotation closure in Mirror library
* added license header
* set rascal to 0.24.3
* fixed several bugs induced by changes in the IRascalValueFactory API
* fixed function wrappers but "unreflect" is still an open matter in Mirror.java
* ported flybytes project to maven and upgraded to latest Rascal API, rewrite AbstractFunction to IFunction in Mirror
* bumped junit from 4.12 to 4.13.1
* updated Mirror.rsc
* added a signaturesOnly flag to the disassembler
* minor experiment with func language
* added some documentation around invokeDynamic
* fixed several issues in the compiler
* bumped rascal-maven-plugin to 0.2.11
* added special constructor for methods which only have bytecode instructions, as produced by the disassembler for readability sake. Fixed roundtrip behavior for these special constructors as well.
* fixed many rascal compiler warnings
* added first version of invokeDynamic decompilation step, fixes #5
* fixed #4 by disassembling invokeDynamic
* made progress on issue #9
* Bank class now fully decompiles due to partial support for continue jumps
* gave tests unique names
* fixed parameter tests
* added decompilation rule for DUP
* added decompilation rule for array storage
* added decompilation rule for array of nulls allocation and cleanup rule for top-level conditional expressions
* fixed bugs in null test decompilation and added cleanup parameter to optionally remove additional nesting after the decompilation only on request. This fixes #8.
* fixed #7 by moving the implementation of invokeSuper from expressions to statements inside the compiler.
* added missing field for super interfaces 
* added rudimentary first decompilation pattern for try/finally
* added first version of try/catch recovery
* reconstructed initial expressions for declarations
* added reconstruction of local variable declarations
* implemented case-label-order independence into switch reconstruction
* re-implemented switch reconstruction with a more constructive detection method (safer and more complete)
* reasonable first version of switch reconstruction
* refactoring
* some comments added
* added short-circuit and and or to flybytes, and recover them in the decompiler. for loops with multiple loop indexes added as well
* added simple loop reconstruction patterns
* fixed bug in inline array allocation decompilation
* refactored to separate disassembling from decompiling, and started to explicitly deal with line numbers, labels and jump targets
* working on decompiler and fixing bugs pointed out by the new typechecker
* fixed warnings and errors raised by the newest version of the type checker. thanks @paulklint
* steps
* added tryctach instruction recovery
* added inline expressions and statements for instruction lists
* added local variable decompilation
* added labels and linenumber decompilation
* decompile switch instructions
* added lots of instructions to the dissambler
* added access function for decompiler from Rascal
* finished field decompilation
* started decompilation process
* started prototyping decompiler, including AST generator for temporary usage
* clean up refactoring for the Instruction set assembler
* added INVOKEDYNAMIC instruction
* fixed label instruction
* added MULTIANEWARRAY instruction
* added type instructions
* added invoke instructions
* hooked up assembler for most bytecode instructions
* added syntax for raw bytecode instructions
* moved kool comp to its own project because it needs a name analysis
* steps towards finishing the KOOL compiler
* clean up and some error handling for missing debug information
* simplified annotating protol
* Learned that for debug source lines to work correctly the _first_ bytecode of a basic block must have a label and corresponding line number.
    So for flybytes to work with debug lines it must push source lines down to the left-most bottom of every tree. If that tree has a line
    annotation, it will use this line, otherwise it will use the line of one of the earliest parents. This enables compiler writers to use
    complex macros to expand functionality, without having to distributes origin line numbers themselves.
* added comment syntax to Protol and also added debugMode to Protol
* replaced name mangling by name priming for shadowing
* finetuning debug linenumbers
* added debug info to Func compiler and fixed conditional jump debug info
* fixed debug locations for conditional jumps
* changed debugMode flag to mean: to include or not include debug line numbers in the generated bytecode
* removed debug printlns because line numbers and source files now work
* trying to get java debugger pick up source file info for pico compiler
* added settings
* added line number feature for debugger information
* resolved many errors detected by the new TypePal-enabled Rascal type checker
* added source code comments
* adapted doc string
* added some comments
* added exp statement
* added for loop
* added minimal implementation of KOOL assignments
* added bare-bones monitor exit and monitor enter feature which does not offer any help in managing exceptions or break and continue jumps in cleaning up locks, but can be very fast in implementing multi-threaded languages
* started KOOL compiler
* fixed comment
* added comments to explain caching of invokedynamic
* made fact more interesting;
* fixed bug in literal arrays
* added comment
* fixed final issue with dynamic call sites
* added comment
* added comment
* removed unused functions
* added example
* fixed fields
* improved demo programs and fixed missing methods
* improved demo programs and fixed missing methods
* working on prototype semantics, falling through to templates does not work yet
* simplifications
* messing around
* fixed several issues and added string concat for debugging presentations
* method missing works
* fixed method invocation for now, next up is method_missing
* fixed name of Lookup class
* fixed typo
* fixed class name bug in checkcast
* added toString for all generated classes
* fixed cast expression, and used it in the protol compiler
* fixed calling methods dynamically
* another one
* more silly things
* lots of things I did not think about at first in protol
* fixed bug in bootstrap handler generator
* fixed bug in new
* added failing test for new
* fixed more issues in protol compiler
* more simple bugs
* added support for fields
* added example protol program
* finished typing in initial version of protol compiler
* added smarter resolution cache
* initial design of completely dynamic method calls and object prototyping templates based on cloning at object allocation time
* refactored KOOL syntax
* added a syntax definition of KOOL'
* started on a mini prototype-based demo language
* ignore generated folder
* removed debug code
* Create README.md
* added explanation of what Flybytes is and what it does.
* renamed mujava to flybytes
* renamed project
* added asm libs
* renamed reference types to object types to align better with the JVM and ASM documentation
* fixed bugs in invokedynamic implementation
* added some doc comment and added more tests for when we expect an actual object reference and not a primitive type
* finished full design of invokedynamic with support for all call site specific information types, including method handles and method types loaded from the class constant pool
* added two more examples
* fixed let example
* fixed alpha renaming in Func
* func language compiler can make good use of conditional expression in mujava
* added conditional expression, which was hard because it needs to suddenly thread types up the two conditional branches
* added a bytecode compiler for func, a strict functional language from the demo folder
* pico now reads variables from the commandline args
* fixed bug in string type treatment if it was used as a receiver type
* added a working Pico to bytecode compiler for demo purposes
* bootstrap methods can also receive extra (constant) int arguments now, next to strings
* fixed upperbound for extra arguments bootstrap methods
* added invokedynamic language feature to mujava. tests have to be added
* fixed annotation bugs
* added support for annotations on local variables
* added support for annotations on method parameters
* added field annotations
* renaming
* fixed array annotations
* file with annotated class to test with
* fixed weird nesting of annotations. not ready here yet.
* added a first version for class annotations
* fixed compilation issue
* added getting annotations of classes to mirror interface
* started small library of control flow macros, for looping over arrays and iterables, etc.
* added shorthands for all simple constants and applied through all the test code for readability
* added more comments
* added comments for explaning complex control flow around monitors
* monitor now deals well with all kinds of block exit scenarios
* added failing monitor tests (throw inside a monitor block)
* improved monitor block, still have to handle exceptions though
* rewrote monitor block to be able to deal with break, continue and return and execute the MONITOREXIT in all possible scenarios properly
* better testing and removed some debug prints
* lookup switch needed the cases sorted. of course. oops
* testing switch better
* added heuristic for choosing between LOOKUP and TABLE switch instructions
* choose if you want a table switch a lookup switch or a heuristic algorithm to choose it
* labels have to be reversed for lookupswitch
* added rudimentary LOOKUPSWITCH generator
* removed debug prints
* fixed TABLESWITCH
* first rudimentary version of TABLESWITCH generator
* refactored design of try/catch/finally, such that finally is one of the handlers (it helps to see the word \finally\ in the source text
* added break out of finally and for test
* changed order of local variable declarations for finally blocks and range checks to avoid crash in ASM
* removed label and goto and replaced by a code block which can be jumped to using break and continue
* fixed a goto hole in for, while and doWhile
* consolidated
* improved debugging features
* refactored finally stack
* fixed some tests, added nesting level for finally blocks to all functions, implemented rudimentary try-finally implementation without support for break, continue and return.
* multiple test cases support added
* removed finally support to experiment with try/catch implementation
* added new test modules
* fixed while and dowhile
* added simple while and do-while tests
* added failing try/catch tests
* fixed inc and added incr and tests
* added initial version of try catch, not supporting break, continue and goto out of a try block
* added monitor blocks with support for exceptions, but not break, continue and goto (fails at compile time with an error message)
* better names for compile methods (easier to search for)
* added do while loop
* added while loop
* fixed for looop
* renamed internal methods to make them easier to find, and rewrote control flow generator to generate labels for each statement and to reuse join labels and avoid goto chains
* Expression -> Exp, Statement -> Stat
* activated break nested loop test
* fixed break outer loop tests
* added nested for loop test. works
* added test (failing) for labeled break statement
* added support for labeled break and continue statements
* added goto/label implementation, and break and continue for for loops
* fixed two tests
* formal parameters can also have initializers, if somebody passed null then this expression is used instead
* rename default to init
* added interface and loop tests
* mujava now supports declare-before-use local variables
* renamed class type to reference type
* added support for default methods in interfaces and test to go with it
* interface fields are always public static and final
* added support for interfaces and abstract methods to go with them
* added some funny tests that show you do not have to cast
* fixed calling interfaces
* better generation of default constructor
* added automatic field initializers and static field initializers for complex expressions
* classType -> class
* removed null and nonnull, can be written now as != null and == null
* optimize if(\!expr)
* aaload -> aload, aastore -> astore
* two big simultaneous changes: expressions do not have to declare their result type anymore, and all class name str are now wrapped as a classType
* fixed branching bug (forgot continuations after true/false constants) also shorter implementation for
* testing and debugging
* getting more details right around array access. longs still do not work
* more array testing enabled
* fixed issues with aaload
* added array tests
* added variable tests
* added if-then-else tests
* testing comparisons
* also test neg
* lots of testing, added newArray with initial values
* fixed eq and neq for arrays, objects and longs
* eq and neq should also dispatch on type
* making a test factory for operators
* added the missing expressions
* fixed copy/paste issue
* added tests for if-then-else and == and <
* fixed some bugs with static modifiers and added more tests
* starting to test the compiler
* testing array mirrors
* fixed long mapping
* testing and fixing conversions
* simplifying value<->object isomorphism, adding tests, improving error handling
* start of vallang api
* improved error handling wrt array element types, and added some first real tests
* fixed array issue
* fixed issues with arrays
* added convenience functions and some accesibility debugging
* added a fully functional reflective interface to any kind of Java objects, for testing purposes. May come in handy for other stuff as well.
* hide <init> constructor name from mujava
* invokeSuper is now builtin
* added support for string constants and factored out standard library API to api Rascal modules
* added specializations for if (null) and if (nonnull)
* documented the macros and added optimizations for eq(null,a)
* refactoring to generalize the conditional expressions, and special casing if-then-else such that we can jump immediately instead of via an intermediate boolean value on the stack
* rewrote statement and expression ordering in continuation passing style, taking a hint from the RVM implementation
* factored out conditional framework
* first version of if-then-else
* added putfield and putstatic
* commented unimplemented statements
* added array support
* local variables are initialized with default numbers to avoid extremely weird JVM crashes using uninitialized variables (which would be hard to diagnose)
* more stack width stuff
* stuff to do with long numbers
* fixed some load and store instructions for larger int values
* added comparison expressions
* invokeInterface
* added rudimentary value coercion
* removed debug print and fixed default constructor visibility
* newInstance, constructors, virtual calls
* added return statement
* fixed return bug
* modifier bug
* added invokevirtual, invokestatic, getstatic, index, load, etc
* added empty method generator
* added field compiler
* simplification
* debugged class skeleton generator
* initial compiler generates class skeleton
* set up skeleton for compiler and class runners
* more alignment in names with the JVM standard
* simplified expression language by factoring out types as arguments rather than operator names
* fixed callers
* revived lost return with arg
* added fconst and dconst
* initial design of a JVM language for code generation
