module lang::mujava::tests::ComparisonTests

import lang::mujava::Compiler;
import lang::mujava::Mirror;
import lang::mujava::api::JavaLang;
import lang::mujava::api::Object;
import Node;
import String;
import IO;
import util::Math;

alias CompOp = Expression (Type, Expression, Expression);

Class cmpOpClass(Type t, CompOp op) {
  expr = op(t, load("i"), load("j"));
  name = "Comparison_<getName(expr)>_<getName(t)>";
  
  return class(classType(name),
      methods=[
        staticMethod(\public(), boolean(), "op", [var(t,"i"), var(t,"j")], [
           \return(boolean(), expr)
        ])
      ]
    );
}

bool DEBUG = true;

@memo
Mirror lc(Class c) { 
  if (DEBUG) 
    compileClass(c, |project://mujava/generated| + "<c.\type.name>.class"); 
  return loadClass(c); 
}

bool testCmpOp(Class c, Type t, value lhs, value rhs, bool answer) { 
  m = lc(c);
  reply = m.invokeStatic(methodDesc(t, "op", [t, t]), [prim(t, lhs), prim(t,rhs)]).toValue(#bool);
  
  return reply == answer;
}

list[Type] intTypes = [integer(), short(), byte(), long()];

test bool testEqTrue(int i) 
  = all (t <- intTypes, 
         I := abs(i) % maxValue(t),
         testCmpOp(cmpOpClass(t, eq), t, I, I, true));

test bool testEqFalse(int i) 
  = all (t <- intTypes, 
         I := abs(i) % maxValue(t),
         testCmpOp(cmpOpClass(t, eq), t, I, I - 1, false));  
         
test bool testNEqTrue(int i) 
  = all (t <- intTypes, 
         I := abs(i) % maxValue(t),
         testCmpOp(cmpOpClass(t, ne), t, I, I - 1, true));

test bool testNEqFalse(int i) 
  = all (t <- intTypes, 
         I := abs(i) % maxValue(t),
         testCmpOp(cmpOpClass(t, ne), t, I, I, false));                
         
test bool testLt(int i, int j) 
  = all (t <- intTypes, 
         I := i % maxValue(t),
         J := j % maxValue(t),
         testCmpOp(cmpOpClass(t, lt), t, I, J, I < J));
         
test bool testGt(int i, int j) 
  = all (t <- intTypes, 
         I := i % maxValue(t),
         J := j % maxValue(t),
         testCmpOp(cmpOpClass(t, gt), t, I, J, I > J)); 
         
test bool testGe(int i, int j) 
  = all (t <- intTypes, 
         I := i % maxValue(t),
         J := j % maxValue(t),
         testCmpOp(cmpOpClass(t, ge), t, I, J, I >= J));           

test bool testLe(int i, int j) 
  = all (t <- intTypes, 
         I := i % maxValue(t),
         J := j % maxValue(t),
         testCmpOp(cmpOpClass(t, le), t, I, J, I <= J));   
                      
// UTILITIES FOR ROUNDING

private real fit(float(), real r) = fitFloat(r);
private real fit(double(), real r) = fitDouble(r);

private real round(float(),  real f) = precision(f, 0);
private real round(double(), real f) = precision(f, 0);
private default int round(Type t, int f) = f;

private real val(float(), Mirror r) = r.toValue(#real);
private real val(double(), Mirror r) = r.toValue(#real);
private default int val(Type _, Mirror r) = r.toValue(#int);
