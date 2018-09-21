module lang::mujava::api::System

import lang::mujava::Syntax;

// print object to System.out (toString() is called automatically)    
Statement stdout(Expression arg)
   = \do(\void(), println("out", arg));

// print object to System.err (toString() is called automatically)
Statement stderr(Expression arg)
   = \do(\void(), println("err", arg));

// not-public because it depends on the magic constants "err" and "out" to work         
private Expression println(str stream, Expression arg)
   = invokeVirtual("java.io.PrintStream", getStatic("java.lang.System", classType("java.io.PrintStream"), stream), 
         methodDesc(\void(), "println", [object()]), [arg]);         