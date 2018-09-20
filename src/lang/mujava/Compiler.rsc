module lang::mujava::Compiler

import lang::mujava::Syntax;
import IO;

data JDKVersion = v1_6() | v1_7() | v1_8();

@javaClass{lang.mujava.internal.ClassCompiler}
@reflect{for stdout}
java void compile(Class cls, loc classFile, bool enableAsserts=false, JDKVersion version=v1_6());

@javaClass{lang.mujava.internal.ClassRunner}
java void runMain(loc classfile, list[str] args=[], list[loc] classpath=[]);

@javaClass{lang.mujava.internal.ClassTestRunner}
java void runTests(loc classfile, list[loc] classpath=[]);

void main() {
  cl = class(classType("HelloWorld"), 
    fields =[
      field( classType("java.lang.Integer"),"age", modifiers={\public()})
    ], 
    methods=[
     defaultConstructor(\private()),
     main("args", 
        block([var(classType("HelloWorld"), "hw"), var(integer(), "i")],[
          store("hw", new("HelloWorld")),
          do(\void(), invokeVirtual("HelloWorld", load("hw"), methodDesc(\void(),"f",[array(string())]), [load("args")])),
          \return()
        ])
      ),
     
     method(\public(), \void(), "f", [var(array(string()), "s")], block([var(integer(),"i"), var(long(),"j"), var(float(), "k"), var(double(), "l")],[
       // test storing numbers in local variables
       store("i", const(integer(), 243)),
       store("j", const(long(), 350000)),
       store("k", const(float(), 10.5)),
       store("l", const(double(), 3456.3456)),
       
       // test loading numbers
       do(integer(), load("i")),
       do(long(), load("j")),
       do(integer(), load("i")),
       do(float(), load("k")),
       do(double(), load("l")),
       
       // print the 3 elements of the argument list:
       stdout(aaload(load("s"), const(integer(), 0))),
       stdout(aaload(load("s"), const(integer(), 1))),
       stdout(aaload(load("s"), const(integer(), 2))),
       
       
       //\return(long(), load("j"))
       \return()
     ]))
    ]
  );
  
  compile(cl, |home:///HelloWorld.class|);
}


