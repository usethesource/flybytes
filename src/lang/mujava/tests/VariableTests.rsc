module lang::mujava::tests::VariableTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import lang::mujava::api::Object;
import lang::mujava::api::JavaLang;
import Node;
import util::Math;
  
Class primVarTestClass(Type t, value v) {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("PrimVarTestClass_<getName(t)>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
          // t tmp;
          decl(t, "tmp"),
          
          // fail if (tmp != def)
          \if(ne(defVal(t), load("tmp")),[rf]),
           
          // tmp = v;
          \store("tmp", const(t, v)),
           
          // if (tmp != v) return false; 
          \if(ne(load("tmp"), const(t, v)), [rf]),
          
          // return true; 
          rt
        ])
      ]
    );
} 


 
bool testVarClass(Class c) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
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

Class objVarTestClass(Type t, Exp v) {
  rf = \return(\false());
  rt = \return(\true());
  
  return class(reference("ObjVarTestClass_<getName(t)>"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [],
        [
           // t tmp;
           decl(t, "tmp"),
           
          // fail if (tmp != def)
          \if(equals(load("tmp"), defVal(t)),[rf]),
           
          // tmp = v;
          \store("tmp", v),
           
          // if (!equals(tmp, v)) return false; 
          \if(neg(equals(load("tmp"), v)), [rf]),
          
          // return true; 
          rt
        ])
      ]
    );
} 

test bool stringVariable() = testVarClass(objVarTestClass(string(), sconst("Hello")));
