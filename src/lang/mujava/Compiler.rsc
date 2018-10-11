module lang::mujava::Compiler

extend lang::mujava::Mirror;
extend lang::mujava::Syntax;
import lang::mujava::api::Object;
import lang::mujava::api::System;
import IO;
extend util::Maybe;

data JDKVersion = v1_6() | v1_7() | v1_8();

@javaClass{lang.mujava.internal.ClassCompiler}
@reflect{for stdout}
@doc{compiles a mujava class to a JVM bytecode class and saves the result to the target location}
java void compileClass(Class cls, loc classFile, bool enableAsserts=false, JDKVersion version=v1_6(), bool debugMode=false);

@javaClass{lang.mujava.internal.ClassCompiler}
@reflect{for stdout}
@doc{compiles a mujava class to a JVM bytecode class and loads the result as a class Mirror value.}
//@memo
java Mirror loadClass(Class cls, Maybe[loc] file=nothing(), list[loc] classpath=[], bool enableAsserts=false, JDKVersion version=v1_6(), bool debugMode=false);

@javaClass{lang.mujava.internal.ClassCompiler}
@reflect{for stdout}
@doc{compiles a list of mujava classes to JVM bytecode classes and loads the results as a class Mirror values (into the same classloader such
that the classes can see eachother.}
java map[str,Mirror] loadClasses(list[Class] classes, Maybe[loc] prefix=nothing(), list[loc] classpath=[], bool enableAsserts=false, JDKVersion version=v1_6(), bool debugMode=false);





