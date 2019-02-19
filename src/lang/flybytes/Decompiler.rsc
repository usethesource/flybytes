module lang::flybytes::Decompiler

extend lang::flybytes::Syntax;
import Exception;
import String;

Exp load("this") = this();

@synopsis{Decompile a JVM classfile to Flybytes ASTs, optionally recovering statement and expression structure.}
Class decompiler(loc classFile, bool statements=true, bool expressions=statements) {
  cls = decompile(classFile);
  
  // statement recovery requires expression recovery.
  if (statements || expressions) {
    cls = recoverExpressions(cls);
  }
  
  if (statements) {
    cls = statements(cls);
  }
  
  return cls;
}

@javaClass{lang.flybytes.internal.ClassDecompiler}
@synopsis{reverses the flybytes compiler, but recovers only lists of instructions from the methods' bodies.}
java Class decompile(loc classFile) throws IO;

Class recoverExpressions(Class class) = visit(class) {
  case method(desc, formals, [asm(instrs)]) =>
       method(desc, formals, [asm(exprs(instrs, ms))])
};



@synopsis{exprs nullary instructions}
list[Instruction] exprs([*pre, Instruction instr, *post]) {
  switch (instr) {
    case /[IFLDA]LOAD/(int var) : 
      if ([*_, LOCALVARIABLE(name, _, _, _, var), *_] := post) {
        return exprs([*pre, exp(load(name)), *post], ms);
      }
      else {
        fail;
      }
    case NOP() :
        return exprs([*pre, *post], ms);
    case ACONST_NULL():
        return exprs([*pre, exp(null()), *post], ms);
    case /<t:[IFLD]>CONST_<i:[0-5]>/():
        return exprs([*pre, exp(const(typ(t), toInt(i))), *post], ms);
    default:
      fail exprs;
  }
}

@synopsis{exprs unary instructions}
list[Instruction] exprs([*list[Instruction] pre, exp(a), Instruction unOp, *list[Instruction] post]) {
  switch(unOp) {
    case ARRAYLENGTH():
      return exprs([*pre, exp(alength(a)), *post], ms);
    case /[IFLD]NEG/():
      return exprs([*pre, exp(neg(a)), *post], ms);
    default:
      fail exprs;
  }
}
 

@synopsis{exprs binary instructions}
list[Instruction] exprs([*list[Instruction] pre, exp(a), exp(b), Instruction binOp, *list[Instruction] post]) {
  switch (binOp) {
  case /[LFDI]ADD/():
    return exprs([*pre, exp(add(a, b)), *post], ms);
  case /[LFDI]SUB/():
    return exprs([*pre, exp(sub(a, b)), *post], ms);
  case /[LFDI]MUL/():
    return exprs([*pre, exp(mul(a, b)), *post], ms);
  case /[LFDI]DIV/():
    return exprs([*pre, exp(div(a, b)), *post], ms);
  case /[LFDI]REM/():
    return exprs([*pre, exp(rem(a, b)), *post], ms);
  case /[IL]SHL/():
    return exprs([*pre, exp(shl(a, b)), *post], ms);
  case /[IL]SHR/():
    return exprs([*pre, exp(shr(a, b)), *post], ms);  
  case /[IL]AND/():
    return exprs([*pre, exp(and(a, b)), *post], ms);
  case /[IL]OR/():
    return exprs([*pre, exp(or(a, b)), *post], ms);
  case /[IL]XOR/():
    return exprs([*pre, exp(xor(a, b)), *post], ms);
  case /[LFDI]ALOAD/():
    return exprs([*pre, exp(aload(a, b)), *post], ms);
  default:
    fail exprs;
  }  
}

@synopsis{fixed point of exprs has been reached}
default list[Instruction] exprs(list[Instruction] instr, set[Modifier] _) = instr;

Type typ("I") = integer();
Type typ("F") = float();
Type typ("L") = long();
Type typ("D") = double();
Type typ("S") = short();
Type typ("B") = byte();
Type typ("Z") = boolean();