module lang::mujava::tests::VariableTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import lang::mujava::api::Object;
import lang::mujava::api::JavaLang;
import Node;
import util::Math;
  
Class primVarTestClass(Type t, value v) {
  rf = \return(boolean(), \false());
  rt = \return(boolean(), \true());
  
  return class(classType("PrimVarTestClass_<getName(t)>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        block([var(t, "tmp")],
        [
          // fail if (tmp != def)
          \if(ne(t, defVal(t), load("tmp")),[rf]),
           
          // tmp = v;
          \store("tmp", const(t, v)),
           
          // if (tmp != v) return false; 
          \if(ne(t, load("tmp"), const(t, v)), [rf]),
          
          // return true; 
          rt
        ]))
      ]
    );
} 

Expression defVal(boolean()) = const(boolean(), false);
Expression defVal(integer()) = const(integer(), 0);
Expression defVal(long()) = const(long(), 0);
Expression defVal(byte()) = const(byte(), 0);
Expression defVal(character()) = const(character(), 0);
Expression defVal(short()) = const(short(), 0);
Expression defVal(float()) = const(float(), 0.0);
Expression defVal(double()) = const(double(), 0.0);
Expression defVal(classType(str _)) = null();
Expression defVal(array(Type _)) = null();
Expression defVal(string()) = null();
 
bool testVarClass(Class c) { 
  m = loadClass(c);
  compileClass(c, |project://mujava/generated/<c.\type.name>.class|);
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
} 

list[Type] intTypes = [integer(), long(), short(), character(), byte()];

test bool intVariables(int i)
  = all(t <- intTypes, I := i % maxValue(t), testVarClass(primVarTestClass(t, I))); 
 
list[Type] floatTypes = [float(), double()];
  
private real fit(float(), real r) = fitFloat(r);
private real fit(double(), real r) = fitDouble(r);
  
test bool floatVariables(real i)
  = all(t <- floatTypes, I := fit(t, i), testVarClass(primVarTestClass(t, I)));  
  
test bool boolVariableTrue() = testVarClass(primVarTestClass(boolean(), true));
test bool boolVariableFalse() = testVarClass(primVarTestClass(boolean(), false));

Class objVarTestClass(Type t, Expression v) {
  rf = \return(boolean(), \false());
  rt = \return(boolean(), \true());
  
  return class(classType("ObjVarTestClass_<getName(t)>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        block([var(t, "tmp")],
        [
          // fail if (tmp != def)
          \if(equals(load("tmp"), defVal(t)),[rf]),
           
          // tmp = v;
          \store("tmp", v),
           
          // if (!equals(tmp, v)) return false; 
          \if(neg(boolean(), equals(load("tmp"), v)), [rf]),
          
          // return true; 
          rt
        ]))
      ]
    );
} 

test bool stringVariable() = testVarClass(objVarTestClass(string(), const(string(), "Hello")));
