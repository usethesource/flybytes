@license{Copyright (c) 2019-2022, NWO-I Centrum Wiskunde & Informatica (CWI) 
All rights reserved. 
 
Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: 
 
1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
  
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
}
@contributor{Jurgen J. Vinju}
module lang::flybytes::Compiler

extend lang::flybytes::Mirror;
extend lang::flybytes::Syntax;
extend util::Maybe;

data JDKVersion 
    = v1_6() | v1_7() | v1_8() | v9() | v10() 
    | v11() | v12() | v13() | v14() | v15()
    | v16() | v17() | v18()
    ;

@javaClass{lang.flybytes.internal.ClassCompiler}
@doc{compiles a flybytes class to a JVM bytecode class and saves the result to the target location}
java void compileClass(Class cls, loc classFile, bool enableAsserts=false, JDKVersion version=v11(), bool debugMode=false);

@javaClass{lang.flybytes.internal.ClassCompiler}
@doc{compiles a flybytes class to a JVM bytecode class and loads the result as a class Mirror value.}
//@memo
java Mirror loadClass(Class cls, Maybe[loc] file=nothing(), list[loc] classpath=[], bool enableAsserts=false, JDKVersion version=v11(), bool debugMode=false);

@javaClass{lang.flybytes.internal.ClassCompiler}
@doc{compiles a list of flybytes classes to JVM bytecode classes and loads the results as a class Mirror values (into the same classloader such
that the classes can see eachother.}
java map[str,Mirror] loadClasses(list[Class] classes, Maybe[loc] prefix=nothing(), list[loc] classpath=[], bool enableAsserts=false, JDKVersion version=v11(), bool debugMode=false);





