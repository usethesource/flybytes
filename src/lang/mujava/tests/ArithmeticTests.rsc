module lang::mujava::tests::ArithmeticTests

import lang::mujava::Compiler;
import lang::mujava::Mirror;
import lang::mujava::api::JavaLang;
import lang::mujava::api::Object;
import Node;
import String;
import IO;
import util::Math;

alias BinOp = Expression (Type, Expression, Expression);
alias UnOp = Expression (Type, Expression);

Class binOpClass(Type t, BinOp op) {
  expr = op(t, load("i"), load("j"));
  name = "Operator_<getName(expr)>_<getName(t)>";
  
  return class(classType(name),
      methods=[
        staticMethod(\public(), t, "op", [var(t,"i"), var(t,"j")], [
           \return(t, expr)
        ])
      ]
    );
}

Class unOpClass(Type t, UnOp op) {
  expr = op(t, load("i"));
  name = "Operator_<getName(expr)>_<getName(t)>";
  
  return class(classType(name),
      methods=[
        staticMethod(\public(), t, "op", [var(t,"i")], [
           \return(t, expr)
        ])
      ]
    );
}
  
@memo  
private Mirror compileLoadClass(Class c) {
  //compileClass(c, |project://mujava/generated| + "<c.\type.name>.class"); 
  return loadClass(c);
}

bool testBinOp(Class c, Type t, num lhs, num rhs, num answer) { 
  m = compileLoadClass(c);
  reply = val(t, m.invokeStatic(methodDesc(t, "op", [t, t]), [prim(t, lhs), prim(t,rhs)]));
  
  if (answer != reply) {
    println("op(<lhs>,<rhs>) == <round(t, answer)> != <round(t,reply)>");
    return false;
  }
  
  return true;
}

bool testUnOp(Class c, Type t, num arg, num answer) { 
  m = compileLoadClass(c);
  reply = val(t, m.invokeStatic(methodDesc(t, "op", [t]), [prim(t, arg)]));
  
  if (answer != reply) {
    println("op(<arg>) == <round(t, answer)> != <round(t,reply)>");
    return false;
  }
  
  return true;
}

bool testBinOpRange(Class c, Type t, num lhs, num rhs, real answer) { 
  m = compileLoadClass(c);
  real reply = val(t, m.invokeStatic(methodDesc(t, "op", [t, t]), [prim(t, lhs), prim(t,rhs)]));
  
  if (abs(answer - reply) > 0.1) {
    println("op(<lhs>,<rhs>) == <answer> != <reply> (diff: <abs(answer - reply)>)");
    return false;
  }
  
  return true;
}

bool testUnOpRange(Class c, Type t, num arg, real answer) { 
  m = compileLoadClass(c);
  real reply = val(t, m.invokeStatic(methodDesc(t, "op", [t]), [prim(t, arg)]));
  
  if (abs(answer - reply) > 0.1) {
    println("op(<lhs>,<rhs>) == <answer> != <reply> (diff: <abs(answer - reply)>)");
    return false;
  }
  
  return true;
}

list[Type] exactArithmeticTypes = [integer(), short(), byte(), long()];

test bool testNeg(int i)
  = all(t <- exactArithmeticTypes,
        I := i % maxValue(t), testUnOp(unOpClass(t, neg), t, I, -1 * I));
        
test bool testAdd(int i, int j) 
  = all (t <- exactArithmeticTypes,
         I := (i % maxValue(t)) / 2,
         J := (j % maxValue(t)) / 2, 
         testBinOp(binOpClass(t, add), t, I, J, I + J));
         
test bool testMul(int i, int j) 
  = all (t <- exactArithmeticTypes,
         I := (i % 10),
         J := (j % 10), 
         testBinOp(binOpClass(t, mul), t, I, J, I * J)); 
         
test bool testSub(int i, int j) 
  = all (t <- exactArithmeticTypes,
         I := (i % maxValue(t)) / 2,
         J := ((j % maxValue(t)) / 2),
         testBinOp(binOpClass(t, sub), t, I, J, I - J));
         
test bool testDiv(int i, int j) 
  = all (t <- exactArithmeticTypes,
         I := (i % maxValue(t)),
         J := abs(((j % maxValue(t)) / 2)) + 1, // never 0, 
         testBinOp(binOpClass(t, div), t, I, J, I / J));
         
test bool testRem(int i, int j) 
  = all (t <- exactArithmeticTypes,
         I := (i % maxValue(t)),
         J := abs(((j % maxValue(t)) / 2)) + 1, // never 0, 
         testBinOp(binOpClass(t, rem), t, I, J, I % J));                                   
         
list[Type] floatingPointTypes = [float(), double()];

test bool testAdd(real i, real j) 
  = all (t <- floatingPointTypes,
         I := fit(t, 1. / (i + .1)), // stick with numbers in +/-[0,1] we can manage
         J := fit(t, 1. / (j + .1)), // stick with numbers in +/-[0,1] we can manage
         testBinOpRange(binOpClass(t, add), t, I, J, fit(t, I + J)));
         
test bool testMul(real i, real j) 
  = all (t <- floatingPointTypes,
         I := fit(t, 1. / (i + .1)), // stick with numbers in +/-[0,1] we can manage
         J := fit(t, 1. / (j + .1)), // stick with numbers in +/-[0,1] we can manage
         testBinOpRange(binOpClass(t, mul), t, I, J, fit(t, I * J))); 
         
test bool testSub(real i, real j) 
  = all (t <- floatingPointTypes,
         I := fit(t, 1. / (i + .1)), // stick with numbers in +/-[0,1] we can manage
         J := fit(t, 1. / (j + .1)), // stick with numbers in +/-[0,1] we can manage
         testBinOpRange(binOpClass(t, sub), t, I, J, fit(t, I - J)));
         
test bool testDiv(real i, real j) 
  = all (t <- floatingPointTypes,
         I := fit(t, 1. / (i + .1)), // stick with numbers in [0,1] we can manage,
         J := abs(fit(t, (1. / (j + .1)) + 1.)), // // stick with numbers in [1,2] we can manage 
         testBinOpRange(binOpClass(t, div), t, I, J, fit(t, I / J)));
         
test bool testNeg(real i)
  = all(t <- floatingPointTypes,
        I := fit(t, 1. / (i + .1)), // stick with numbers in [0,1] we can manage, 
        testUnOpRange(unOpClass(t, neg), t, I, -1 * I));         

// UTILITIES FOR ROUNDING

private real fit(float(), real r) = fitFloat(r);
private real fit(double(), real r) = fitDouble(r);

private real round(float(),  real f) = precision(f, 0);
private real round(double(), real f) = precision(f, 0);
private default int round(Type t, int f) = f;

private real val(float(), Mirror r) = r.toValue(#real);
private real val(double(), Mirror r) = r.toValue(#real);
private default int val(Type _, Mirror r) = r.toValue(#int);

