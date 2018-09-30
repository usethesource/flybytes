module lang::mujava::tests::ClassTests

import lang::mujava::Compiler;
import lang::mujava::Mirror;
import lang::mujava::api::JavaLang;
import lang::mujava::api::Object;
import Node;
import String;
import IO;
import util::Math;

public Class testClass() = 
  //public class TestClass {
  class(classType("TestClass"),
    fields=[
      // public static int staticField = 42;
      field(integer(), "staticField", \default=42, modifiers={\public(), \static()}),
      
      // public int field;
      field(integer(), "field", modifiers={\public()})
    ],
    methods=[
      // public TestClass() { }
      defaultConstructor(\public()),
      
      // public TestClass(int init) {
      //   field = init;
      // }
      constructor(\public(), [var(integer(), "init")],[
         invokeSuper([],[]), // must call to super!
         putField(integer(), "field", load("init")),
         \return() // must generate a return!
      ]),
      
      // public static void putStatic(int v) {
      //   staticField = v;
      // }
      staticMethod(\public(), \void(), "putStatic", [var(integer(), "v")], [
         putStatic(integer(), "staticField", load("v")),
         \return() // must generate a return!
      ]),
      
      // public static int getStatic() {
      //    return staticField;
      // }
      staticMethod(\public(), integer(), "getStatic", [], [
        \return(integer(), getStatic(integer(), "staticField"))
      ]),
      
      // public int getField() {
      //   return field;
      // }
      method(\public(), integer(), "getField", [], [
        \return(integer(), getField(integer(), "field"))
      ]),
      
      // public void setField(int v) {
      //   field = v;
      // }
      method(\public(), \void(), "setField", [var(integer(), "v")], [
        putField(integer(), "field", load("v")),
        \return()
      ]),
      
      // public int addField(int i) {
      //   return field + i;
      // }
      method(\public(), \integer(), "addField", [var(integer(), "v")], [
        \return(integer(), add(\integer(), getField(integer(), "field"), load("v")))
      ])
    ]
  );
  
Mirror compiledTestClass() = loadClass(testClass());
 
test bool newInstanceGetUnitializedInteger() {
  c = compiledTestClass();
  i = c.newInstance(constructorDesc([]),[]);
  return i.getField("field").toValue(#int) == 0;
}

test bool newInstanceCallGetter() {
  c = compiledTestClass();
  i = c.newInstance(constructorDesc([]),[]);
  return i.invoke(methodDesc(integer(), "getField", []), []).toValue(#int) == 0;
}

test bool newInstanceCallSetter() {
  c = compiledTestClass();
  i = c.newInstance(constructorDesc([]),[]);
  
  int tester = 32;
  
  // call method with the side-effect
  i.invoke(methodDesc(\void(), "setField", [integer()]), [integer(tester)]);
  
  // test if it worked by retrieving the field
  return i.getField("field").toValue(#int) == tester;
}

test bool staticMethod() {
  c = compiledTestClass();
  int tester = 666;
  c.invokeStatic(methodDesc(\void(), "putStatic", [integer()]), [integer(tester)]);
  r = c.getStatic("staticField").toValue(#int);
  c.invokeStatic(methodDesc(\void(), "putStatic", [integer()]), [integer(42)]); // set it back
  return r == tester;
}
  

test bool staticFieldInitializer() 
  = compiledTestClass().getStatic("staticField").toValue(#int) == 42;

public Class helloWorld = class(classType("HelloWorld"), 
    fields =[
      field( classType("java.lang.Integer"),"age", modifiers={\public()})
    ], 
    methods=[
     defaultConstructor(\public()),
     main("args", 
        block([var(classType("HelloWorld"), "hw"), var(integer(), "i")],[
          store("hw", new("HelloWorld")),
          do(\void(), invokeVirtual("HelloWorld", load("hw"), methodDesc(\void(),"f",[array(string())]), [load("args")])),
          \return()
        ])
      ),
      
     staticMethod(\public(), \integer(), "MIN", [var(integer(),"i"),var(integer(), "j")], 
        block([],[
           \if (lt(\integer(), load("i"), load("j")),[
             \return(\integer(), load("i"))
           ],[
             \return(\integer(), load("j"))
           ])
        ])),
        
     staticMethod(\public(), \integer(), "LEN", [var(array(string()),"a")], 
        block([],[
           \return(\integer(), alength(load("a")))
        ])),   
          
     method(\public(), \void(), "f", [var(array(string()), "s")], block([var(integer(),"i"), var(long(),"j"), var(float(), "k"), var(double(), "l"), var(integer(), "m")],[
       // test storing numbers in local variables
       
       store("i", const(integer(), 245)),
       store("j", const(long(), 350000)),
       store("k", const(float(), 10.5)),
       store("l", const(double(), 3456.3456)),
       store("m", const(integer(), 244)),
       
       // test loading numbers
       do(integer(), load("i")),
       do(long(), load("j")),
       do(integer(), load("i")),
       do(float(), load("k")),
       do(double(), load("l")),
       
       // print the 3 elements of the argument list:
       \if(gt(integer(), load("i"), load("m")), [
         stdout(aaload(string(), load("s"), const(integer(), 0))),
         stdout(aaload(string(), load("s"), const(integer(), 1))),
         stdout(aaload(string(), load("s"), const(integer(), 2)))
       ]),
       
       
       //\return(long(), load("j"))
       \return()
     ]))
    ]
  );
  
void main() {
  compileClass(helloWorld, |home:///HelloWorld.class|);
}