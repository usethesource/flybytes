module lang::mujava::Compiler

import lang::mujava::Syntax;

data JDKVersion = v1_6() | v1_7() | v1_8();

@javaClass{lang.mujava.internal.ClassCompiler}
@reflect{for stdout}
java void compile(Class cls, loc classFile, bool enableAsserts=false, JDKVersion version=v1_6());

@javaClass{lang.mujava.internal.ClassRunner}
java void runMain(loc classfile, list[str] args=[], list[loc] classpath=[]);

@javaClass{lang.mujava.internal.ClassTestRunner}
java void runTests(loc classfile, list[loc] classpath=[]);

void main() {
  compile(class("HelloWorld", 
    fields=[field(integer(),"age", \default=42, modifiers={\public()})], 
    methods=[method(methodDesc(integer(), "getAge", []), block([],[]), modifiers={\public()})]
  ), |home:///HelloWorld.class|);
}


