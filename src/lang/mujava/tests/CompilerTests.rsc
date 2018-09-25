module lang::mujava::tests::CompilerTests

import lang::mujava::Compiler;
import lang::mujava::Mirror;
import lang::mujava::api::JavaLang;
import lang::mujava::api::Object;
import Node;
import String;
import IO;

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
      //    return i < field;
      // }
      method(\public(), \string(), "ifEqual", [var(integer(), "i")], [
         \if (eq(load("i"), getStatic(integer(), "staticField")), [
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

alias BinOp = Expression (Type, Expression, Expression);
alias UnOp = Expression (Type, Expression);

Class binOpClass(Type t, BinOp op) {
  expr = op(t, load("i"), load("j"));
  name = "Operator_<getName(expr)>_<getName(t)>";
  
  return class(classType(name),
      methods=[
        staticMethod(\public(), boolean(), "op", [var(t,"i"), var(t,"j"), var(t,"a")], [
           \return(boolean(), eq(expr, load("a")))
        ])
        ,
        staticMethod(\public(), t, "result", [var(t,"i"), var(t,"j")], [
           \return(t, expr)
        ])
      ]
    );
}
  
@memo  
private Mirror compileLoadClass(Class c) {
  compileClass(c, |project://mujava/generated| + "<c.\type.name>.class"); 
  return loadClass(c);
}

bool testBinOp(Class c, Type t, value lhs, value rhs, value answer) { 
  m = compileLoadClass(c);
  r = m.invokeStatic(methodDesc(boolean(), "op", [t, t, t]), [prim(t, lhs), prim(t,rhs), prim(t,answer)]);
  
  if (!r.toValue(#bool)) {
    println("<lhs> op <rhs> != <m.invokeStatic(methodDesc(boolean(), "result", [t, t]), [prim(t, lhs), prim(t,rhs)]).toValue(#str)>");
    return false;
  }
  
  return true;
}

list[Type] arithmeticTypes = [integer(), short(), byte()];

test bool testAdd(int i, int j) 
  = all (t <- arithmeticTypes,
         I := (i % maxValue(t)) / 2,
         J := (j % maxValue(t)) / 2, 
         testBinOp(binOpClass(t, add), t, I, J, I + J));
         
test bool testMul(int i, int j) 
  = all (t <- arithmeticTypes,
         I := (i % 10),
         J := (j % 10), 
         testBinOp(binOpClass(t, mul), t, I, J, I * J)); 
         
test bool testSub(int i, int j) 
  = all (t <- arithmeticTypes,
         I := (i % maxValue(t)) / 2,
         J := (j % maxValue(t)) / 2, 
         testBinOp(binOpClass(t, sub), t, I, J, I - J));                 
         
