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
          do(true, invokeVirtual("HelloWorld", load("hw"), methodDesc(\void(),"f",[array(string())]), [load("args")])),
          \return()
        ])
      ),
     
     method(\public(), \void(), "f", [var(array(string()), "s")], block([var(integer(),"i"), var(long(),"j")],[
       store("i", const(integer(), 243)),
       //store("j", const(long(), 243)),
       //store("j", const(integer(), 2)),
       //stdout(load("s")),
       //stdout(aaload(load("s"), const(integer(), 0))),
       //stdout(aaload(load("s"), const(integer(), 1))),
        //stdout(aaload(load("s"), const(integer(), 2))),
       //\return(long(), load("j"))
       \return()
     ]))
    ]
  );
  
  compile(cl, |home:///HelloWorld.class|);
}


