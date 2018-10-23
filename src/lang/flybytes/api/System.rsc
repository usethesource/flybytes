module lang::flybytes::api::System

import lang::flybytes::Syntax;

// print object to System.out (toString() is called automatically)    
Stat stdout(Exp arg)
   = \do(println("out", arg));

// print object to System.err (toString() is called automatically)
Stat stderr(Exp arg)
   = \do(println("err", arg));

// not-public because it depends on the magic constants "err" and "out" to work         
private Exp println(str stream, Exp arg)
   = invokeVirtual(object("java.io.PrintStream"), getStatic(object("java.lang.System"), object("java.io.PrintStream"), stream), 
         methodDesc(\void(), "println", [object()]), [arg]);         
