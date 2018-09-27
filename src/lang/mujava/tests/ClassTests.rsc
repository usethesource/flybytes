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
      ]),
      
      // public boolean lessThanField(int i) {
      //    return i < field;
      // }
      method(\public(), \boolean(), "lessThan", [var(integer(), "i")], [
        \return(boolean(), lt(integer(), load("i"), getField(integer(), "field")))
      ]),
      
      // public String ifEqual(int i) {
      //    if (i == staticField) {
      //      return "parameter is equal to the static field";
      //    } else {
      //      return "parameter is not equal to the static field";
      //    }
      // }
      method(\public(), \string(), "ifEqual", [var(integer(), "i")], [
         \if (eq(integer(), load("i"), getStatic(integer(), "staticField")), [
           \return(string(), const(string(), "parameter is equal to the static field"))         
         ],[
           \return(string(), const(string(), "parameter is not equal to the static field"))
         ])
      ])
    ]
  );
  
Mirror compiledTestClass() = compileLoadClass(testClass());
 
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

test bool ifEqual() {
  c = compiledTestClass();
  i = c.newInstance(constructorDesc([]),[]);
  
  int tester = 32;
  
  // call method which returns a string which should contain "not" because 32 != 42;
  result1 = i.invoke(methodDesc(\string(), "ifEqual", [integer()]), [integer(tester)]);
  
  // change the field to 32
  i.invoke(methodDesc(\void(), "setField", [integer()]), [integer(32)]);
  
  // call method which returns a string which should contain "not" because 32 != 42;
  result2 = i.invoke(methodDesc(\string(), "lessThan", [integer()]), [integer(tester)]);
  
  return /not/ := result1.toValue(#str) && /not/ !:= result2.toValue(#str); 
}  

test bool lessThan() {
  c = compiledTestClass();
  i = c.newInstance(constructorDesc([]),[]);
  
  int tester = 32;
  
  // change the field to 42
  i.invoke(methodDesc(\void(), "setField", [integer()]), [integer(42)]);
  
  // call method which returns a string which should contain "not" because 32 < 42;
  result1 = i.invoke(methodDesc(\string(), "lessThan", [integer()]), [integer(tester)]);
  
  // change the field to 12
  i.invoke(methodDesc(\void(), "setField", [integer()]), [integer(12)]);
  
  // call method which returns a string which should contain "not" because 32 != 12;
  result2 = i.invoke(methodDesc(\string(), "lessThan", [integer()]), [integer(tester)]);
  
  return  result1.toValue(#bool) && !result2.toValue(#bool); 
}  

