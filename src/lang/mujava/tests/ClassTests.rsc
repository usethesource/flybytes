module lang::mujava::tests::ClassTests

import lang::mujava::Compiler;
import lang::mujava::Mirror;
import lang::mujava::api::JavaLang;
import lang::mujava::api::Object;
import lang::mujava::api::System;
import Node;
import String;
import IO;
import util::Math;

public Class testClass() = 
  //public class TestClass {
  class(reference("TestClass"),
    fields=[
      // public static int staticField = 42;
      field(integer(), "staticField", \default=const(integer(), 42), modifiers={\public(), \static()}),
      
      // public int field;
      field(integer(), "field", modifiers={\public()})
    ],
    methods=[
      // public TestClass(int init) {
      //   field = init;
      // }
      //constructor(\public(), [],[invokeSuper([],[]), \return()]),
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
        \return(getStatic(integer(), "staticField"))
      ]),
      
      // public int getField() {
      //   return field;
      // }
      method(\public(), integer(), "getField", [], [
        \return(getField(integer(), "field"))
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
        \return(add(getField(integer(), "field"), load("v")))
      ])
    ]
  );
  
Mirror compiledTestClass() = loadClass(testClass(), file=just(|project://mujava/generated/TestClass.class|));
 
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


public Class extendClass() 
  = class(reference("ExtendedClass"),
       super=reference("TestClass"),
       methods=[
       //  method(Modifier access, Type ret, str name, list[Variable] args, list[Statement] stats)
         method(\public(), boolean(), "testMethod", [], [
            // call a super method
            do(invokeVirtual(this(), methodDesc(\void(), "setField", [integer()]), [const(integer(), 32)])),
            
            // get a super field
            \return(eq(getField(integer(), "field"), const(integer(), 32)))
         ])
       ]
  );
  
test bool extendTest() {
  // load the classes together
  cs = loadClasses([extendClass(), testClass()], prefix=just(|project://mujava/generated/|));
  
  // get a mirror instance of the subclass
  c = cs["ExtendedClass"];
  i = c.newInstance(constructorDesc([]),[]);
  
  // call super method with a side-effect
  return i.invoke(methodDesc(\void(), "testMethod", []), []).toValue(#bool);
}

private Type HELLO = reference("HelloWorld");

public Class helloWorld = class(HELLO, 
    fields =[
      field(Integer(), "age", modifiers={\public()})
    ], 
    methods=[
     main("args",  [
          decl(HELLO, "hw", \default=new(HELLO)),
          do(invokeVirtual(load("hw"), methodDesc(\void(),"f",[array(string())]), [load("args")])),
          \return()
        ]
      ),
      
     staticMethod(\public(), \integer(), "MIN", [var(integer(),"i"),var(integer(), "j")], 
        [
           \if (lt(load("i"), load("j")),[
             \return(load("i"))
           ],[
             \return(load("j"))
           ])
        ]),
        
     staticMethod(\public(), \integer(), "LEN", [var(array(string()),"a")], 
        [
           \return(alength(load("a")))
        ]),   
          
     method(\public(), \void(), "f", [var(array(string()), "s")], [
       // test declarations
       decl(integer(), "i"), 
       decl(long(), "j"), 
       decl(float(), "k"), 
       decl(double(), "l"), 
       decl(integer(), "m"),
       
       // test storing numbers in local variables
       store("i", const(integer(), 245)),
       store("j", const(long(), 350000)),
       store("k", const(float(), 10.5)),
       store("l", const(double(), 3456.3456)),
       store("m", const(integer(), 244)),
       
       // test loading numbers
       do(load("i")),
       do(load("j")),
       do(load("i")),
       do(load("k")),
       do(load("l")),
       
       // print the 3 elements of the argument list:
       \if(gt(load("i"), load("m")), [
         stdout(aload(load("s"), const(integer(), 0))),
         stdout(aload(load("s"), const(integer(), 1))),
         stdout(aload(load("s"), const(integer(), 2)))
       ]),
       
       //\return(long(), load("j"))
       \return()
     ])
    ]
  );
  
void main() {
  compileClass(helloWorld, |home:///HelloWorld.class|);
}