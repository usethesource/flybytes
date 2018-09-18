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
     defaultConstructor(\public()),
     main("args", 
        block([var(classType("HelloWorld"), "hw")],[
          stderr(index("args", 0)),
          store("hw", new("HelloWorld")),
          do(true, invokeVirtual("HelloWorld", load("hw"), methodDesc(\void(),"f",[string()]), [index("args", 0)])),
          \return()
        ])
      ),
     
     method(\public(), \void(), "f", [var(string(), "s")], block([],[
       stdout(load("s")),
       stdout(this()),
       \return()
     ]))
    ]
  );
  iprintln(cl);
  
  compile(cl, |home:///HelloWorld.class|);
}


