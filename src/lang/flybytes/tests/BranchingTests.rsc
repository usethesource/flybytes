module lang::flybytes::tests::BranchingTests

import lang::flybytes::Compiler;
import lang::flybytes::Mirror;
import lang::flybytes::api::JavaLang;
import lang::flybytes::api::Object;
import Node;
import String;
import IO;
import util::Math;

public Class ifClass(Exp cond) {
  name = "IfCmp_<getName(cond)>";
  
  return class(object(name),
      methods=[
        staticMethod(\public(), boolean(), "ifTest", [], [
           \if (cond, [\return(\true())]),
           \return(\false())
        ]),
        staticMethod(\public(), boolean(), "ifElseTest", [], [
           \if (cond, [\return(\true())], [\return(\false())])
        ]),
        staticMethod(\public(), boolean(), "methodTrue", [], [
           \return(\true())
        ]),
        staticMethod(\public(), boolean(), "methodFalse", [], [
           \return(\false())
        ])
      ]
    );
}

bool testIf(Class c, bool answer) { 
  m = loadClass(c, file=just(|project://flybytes/generated| + "<c.\type.name>.class"));
  ifReply = m.invokeStatic(methodDesc(boolean(), "ifTest", []), []).toValue(#bool);
  ifElseReply = m.invokeStatic(methodDesc(boolean(), "ifElseTest", []), []).toValue(#bool);
  
  return answer == ifReply && answer == ifElseReply;
}

test bool testIfTrue() = testIf(ifClass(\true()), true);
test bool testIfFalse() = testIf(ifClass(\false()), false);

test bool testIfMethodTrue() = testIf(ifClass(invokeStatic(methodDesc(boolean(),"methodTrue",[]),[])), true);
test bool testIfMethodFalse() = testIf(ifClass(invokeStatic(methodDesc(boolean(),"methodFalse",[]),[])), false);

test bool testIfEqTrue() = testIf(ifClass(eq(iconst(1),iconst(1))), true);
test bool testIfEqFalse() = testIf(ifClass(eq(iconst(2),iconst(1))), false);

test bool testIfEqBoolTrue() = testIf(ifClass(eq(\true(), \true())), true);
test bool testIfEqBoolFalse() = testIf(ifClass(eq(\true(), \false())), false);

// now some special tests to see if `if(eq(a,b))` which is optimized to `ifeq(a,b)`,
// and also for the other comparison operators, is compiled correctly:
private alias BinOp = Exp (Exp, Exp);

private Class ifCmpClass(Type t, BinOp op) {
  expr = op(load("i"), load("j"));
  name = "IfCmp_<getName(expr)>_<getName(t)>";
  
  return class(object(name),
      methods=[
        staticMethod(\public(), boolean(), "ifThenTest", [var(t,"i"), var(t,"j")], [
           \if (expr /* should be short-cut to IFCMP internally */, [
             \return(\true())         
           ]),
           \return(\false())
        ])
        ,
        staticMethod(\public(), boolean(), "ifThenElseTest", [var(t,"i"), var(t,"j")], [
           \if (expr /* should be short-cut to IFCMP internally */, [
             \return(\true())         
           ],[
             \return( \false())
           ])   
        ])
      ]
    );
}

bool testIf(Class c, Type t, str mn, Mirror lhs, Mirror rhs, bool answer) { 
  m = loadClass(c);
  reply = m.invokeStatic(methodDesc(boolean(), mn, [t, t]), [lhs, rhs]).toValue(#bool);
  
  return answer == reply;
}

list[Type] intTypes = [integer(), short(), byte(), long()];
list[str] condTypes = ["ifThenTest", "ifThenElseTest"];

test bool testEqTrue(int i) 
  = all (t <- intTypes, 
         I := prim(t, abs(i) % maxValue(t)), cl <- condTypes,
         testIf(ifCmpClass(t, eq), t, cl, I, I, true));

test bool testEqFalse(int i) 
  = all (t <- intTypes, 
         I := abs(i) % maxValue(t), cl <- condTypes,
         testIf(ifCmpClass(t, eq), t, cl, prim(t, I), prim(t, I - 1), false));  
         
test bool testNEqTrue(int i) 
  = all (t <- intTypes, 
         I := abs(i) % maxValue(t), cl <- condTypes,
         testIf(ifCmpClass(t, ne), t, cl, prim(t, I), prim(t, I - 1), true));

test bool testNEqFalse(int i) 
  = all (t <- intTypes, 
         I := prim(t, abs(i) % maxValue(t)), cl <- condTypes,
         testIf(ifCmpClass(t, ne), t, cl, I, I, false));                
         
test bool testLt(int i, int j) 
  = all (t <- intTypes, 
         I := (i % maxValue(t)),
         J := (j % maxValue(t)), cl <- condTypes,
         testIf(ifCmpClass(t, lt), t, cl, prim(t, I), prim(t, J), I < J));
         
test bool testGt(int i, int j) 
  = all (t <- intTypes, 
         I := (i % maxValue(t)),
         J := (j % maxValue(t)), cl <- condTypes,
         testIf(ifCmpClass(t, gt), t, cl, prim(t, I), prim(t, J), I > J)); 
         
test bool testGe(int i, int j) 
  = all (t <- intTypes, 
         I := (i % maxValue(t)),
         J := (j % maxValue(t)), cl <- condTypes,
         testIf(ifCmpClass(t, ge), t, cl, prim(t, I), prim(t, J), I >= J));           

test bool testLe(int i, int j) 
  = all (t <- intTypes, 
         I := (i % maxValue(t)),
         J := (j % maxValue(t)), cl <- condTypes,
         testIf(ifCmpClass(t, le), t, cl, prim(t, I), prim(t, J), I <= J));

         
