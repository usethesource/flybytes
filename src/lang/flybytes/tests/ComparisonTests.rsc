module lang::flybytes::tests::ComparisonTests

import lang::flybytes::Compiler;
import lang::flybytes::Mirror;
import lang::flybytes::api::JavaLang;
import Node;
import util::Math;

alias CompOp = Exp (Exp, Exp);

Class cmpOpClass(Type t, CompOp op) {
  expr = op(load("i"), load("j"));
  name = "Comparison_<getName(expr)>_<getName(t)>";
  
  return class(object(name),
      methods=[
        staticMethod(\public(), boolean(), "op", [var(t,"i"), var(t,"j")], [
           \return(expr)
        ])
      ]
    );
}


bool testCmpOp(Class c, Type t, Mirror lhs, Mirror rhs, bool answer) { 
  m = loadClass(c);
  reply = m.invokeStatic(methodDesc(t, "op", [t, t]), [lhs, rhs]).toValue(#bool);
  return reply == answer;
}

list[Type] intTypes = [integer(), short(), byte(), long()];

test bool testEqTrue(int i) 
  = all (t <- intTypes, 
         I := prim(t, abs(i) % maxIntValue(t)),
         testCmpOp(cmpOpClass(t, eq), t, I, I, true));

test bool testEqFalse(int i) 
  = all (t <- intTypes, 
         I := abs(i) % maxIntValue(t),
         testCmpOp(cmpOpClass(t, eq), t, prim(t, I), prim(t, I - 1), false));  
         
test bool testNEqTrue(int i) 
  = all (t <- intTypes, 
         I := abs(i) % maxIntValue(t),
         testCmpOp(cmpOpClass(t, ne), t, prim(t, I), prim(t, I - 1), true));

test bool testNEqFalse(int i) 
  = all (t <- intTypes, 
         I := prim(t, abs(i) % maxValue(t)),
         testCmpOp(cmpOpClass(t, ne), t, I, I, false));                
         
test bool testLt(int i, int j) 
  = all (t <- intTypes, 
         I := (i % maxIntValue(t)),
         J := (j % maxIntValue(t)),
         testCmpOp(cmpOpClass(t, lt), t, prim(t, I), prim(t, J), I < J));
         
test bool testGt(int i, int j) 
  = all (t <- intTypes, 
         I := (i % maxIntValue(t)),
         J := (j % maxIntValue(t)),
         testCmpOp(cmpOpClass(t, gt), t, prim(t, I), prim(t, J), I > J)); 
         
test bool testGeInt(int i, int j) 
  = all (t <- intTypes, 
         I := (i % maxIntValue(t)),
         J := (j % maxIntValue(t)),
         testCmpOp(cmpOpClass(t, ge), t, prim(t, I), prim(t, J), I >= J));           

test bool testLe(int i, int j) 
  = all (t <- intTypes, 
         I := (i % maxIntValue(t)),
         J := (j % maxIntValue(t)),
         testCmpOp(cmpOpClass(t, le), t, prim(t, I), prim(t, J), I <= J));   
            
list[Type] floatTypes = [float(), double()];

test bool testEqTrue2(real r) 
  = all (t <- floatTypes, 
         I := 1. / (r + .1),
         testCmpOp(cmpOpClass(t, eq), t, prim(t, I), prim(t, I), true));
         
test bool testEqFalse2(real r) 
  = all (t <- floatTypes, 
         I := 1. / (r + .1),
         testCmpOp(cmpOpClass(t, eq), t, prim(t, I), prim(t, I + .1), false));         

test bool testLe2(real i, real j) 
  = all (t <- floatTypes, 
         I := fit(t, 1. / (i + .1)),
         J := fit(t, 1. / (j + .1)),
         testCmpOp(cmpOpClass(t, le), t, prim(t, I), prim(t, J), I <= J));
         
test bool testLt2(real i, real j) 
  = all (t <- floatTypes, 
         I := fit(t, 1. / (i + .1)),
         J := fit(t, 1. / (j + .1)),
         testCmpOp(cmpOpClass(t, lt), t, prim(t, I), prim(t, J), I < J));
 
test bool testGt2(real i, real j) 
  = all (t <- floatTypes, 
         I := fit(t, 1. / (i + .1)),
         J := fit(t, 1. / (j + .1)),
         testCmpOp(cmpOpClass(t, gt), t, prim(t, I), prim(t, J), I > J));

test bool testGeReal(real i, real j) 
  = all (t <- floatTypes, 
         I := fit(t, 1. / (i + .1)),
         J := fit(t, 1. / (j + .1)),
         testCmpOp(cmpOpClass(t, ge), t, prim(t, I), prim(t, J), I >= J));
          
                                         
list[Type] objectTypes = [object(), string(), array(integer())];

test bool testEqTrue3() 
  = all (t <- objectTypes, 
         v := make(t, 1),
         testCmpOp(cmpOpClass(t, eq), t, v, v, true));

test bool testEqFalse3() 
  = all (t <- objectTypes, 
         v1 := make(t, 1),
         v2 := make(t, 2),
         testCmpOp(cmpOpClass(t, eq), t, v1, v2, false));

private value make(object(str cl), int _) = classMirror(cl).newInstance(constructorDesc([]),[]);
private value make(string(), int i) = prim(string(), "hello<i>");
private value make(array(integer()), int i) = array(integer(), [integer(i)]);

                               
// UTILITIES FOR ROUNDING

private real fit(float(), real r) = fitFloat(r);
private real fit(double(), real r) = fitDouble(r);

