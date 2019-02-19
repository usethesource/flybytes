module lang::flybytes::Decompiler

extend lang::flybytes::Syntax;
import Exception;
import String;
import IO;

data Exp(int line = -1, str label = "");
data Stat(int line = -1, str label = "");
data Instruction(int line = -1, str label="");

@synopsis{Decompile a JVM classfile to Flybytes ASTs, optionally recovering statement and expression structure.}
Class decompiler(loc classFile, bool statements=true, bool expressions=statements) throws IO {
  cls = decompile(classFile);
  
  // statement recovery requires expression recovery.
  if (statements || expressions) {
    println("recovering expressions");
    cls = recoverExpressions(cls);
  }
  
  if (statements) {
    cls = recoverStatements(cls);
  }
  
  return visit (cls) {
    case method(desc, formals, stats) 
      => method(desc, formals, clean(stats))
  }
}

@javaClass{lang.flybytes.internal.ClassDecompiler}
@synopsis{reverses the flybytes compiler, but recovers only lists of instructions from the methods' bodies.}
java Class decompile(loc classFile) throws IO;

Class recoverExpressions(Class class) = visit(class) {
  case method(desc, formals, [asm(instrs)]) =>
       method(desc, formals, [asm(exprs(instrs))])
};

Class recoverStatements(Class class) = visit(class) {
  case method(desc, formals, [asm(instrs)]) =>
       method(desc, formals, [asm(stmts(instrs))])
};

list[Stat] clean([*Stat pre, asm([stat(s), LOCALVARIABLE(_,_,_,_,_), *_]), *Stat post]) 
  = clean([*pre, s, *post]);
 
default list[Stat] clean(list[Stat] x) = x; 
  
list[Instruction] stmts([*Instruction pre, exp(a), /[ILFDA]RETURN/(), *Instruction post]) 
  = stmts([*pre, stat(\return(a)), *post]);

list[Instruction] stmts([*Instruction pre, RETURN(), *Instruction post]) 
  = stmts([*pre, stat(\return()), *post]);

default list[Instruction] stmts(list[Instruction] st) = st;

list[Instruction] exprs([*Instruction pre, LINENUMBER(int l, _), exp(a), *Instruction post]) 
  = exprs([*pre, exp(a[line=l]), *post]);
  
list[Instruction] exprs([*Instruction pre, LINENUMBER(int l, _), Instruction i, *Instruction post]) 
  = exprs([*pre, i[line=l], *post]);  

list[Instruction] exprs([*Instruction pre, LABEL(str l), exp(a), *Instruction post]) 
  = exprs([*pre, exp(a[label=l]), *post]);
  
  list[Instruction] exprs([*Instruction pre, LABEL(str l), Instruction i, *Instruction post]) 
  = exprs([*pre, i[label=l], *post]);

@synopsis{nullary instructions}
list[Instruction] exprs([*Instruction pre, /[AIFL]LOAD/(int var), *Instruction mid, Instruction lv:LOCALVARIABLE(str name, _, _, _, var), *Instruction post]) 
  = exprs([*pre, exp(load(name)), *mid, lv, *post]);
  
list[Instruction] exprs([*Instruction pre, Instruction _:str nullOp(), *Instruction post]) {
  switch (nullOp) {  
    case "NOP":
        return exprs([*pre, *exprs(post)]);
    case "ACONST_NULL":
        return exprs([*pre, exp(null()), *exprs(post)]);
    case /<t:[IFLD]>CONST_<i:[0-5]>/:
        return exprs([*pre, exp(const(typ(t), toInt(i))), *exprs(post)]);
    default:
      fail exprs;
  }
}

@synopsis{unary instructions}
list[Instruction] exprs([*Instruction pre, exp(a), Instruction _:str unOp(), *Instruction post]) {
  switch(unOp) {
    case "ARRAYLENGTH":
      return exprs([*pre, exp(alength(a)), *exprs(post)]);
    case /[IFLD]NEG/:
      return exprs([*pre, exp(neg(a)), *exprs(post)]);
    default:
      fail exprs;
  }
}
 

@synopsis{binary instructions}
list[Instruction] exprs([*Instruction pre, exp(Exp a), exp(Exp b), Instruction _:str binOp(), *Instruction post]) {
  switch (binOp) {
  case /[LFDI]ADD/:
    return exprs([*pre, exp(add(a, b)), *post]);
  case /[LFDI]SUB/:
    return exprs([*pre, exp(sub(a, b)), *post]);
  case /[LFDI]MUL/:
    return exprs([*pre, exp(mul(a, b)), *post]);
  case /[LFDI]DIV/:
    return exprs([*pre, exp(div(a, b)), *post]);
  case /[LFDI]REM/:
    return exprs([*pre, exp(rem(a, b)), *post]);
  case /[IL]SHL/:
    return exprs([*pre, exp(shl(a, b)), *post]);
  case /[IL]SHR/:
    return exprs([*pre, exp(shr(a, b)), *post]);  
  case /[IL]AND/:
    return exprs([*pre, exp(and(a, b)), *post]);
  case /[IL]OR/:
    return exprs([*pre, exp(or(a, b)), *post]);
  case /[IL]XOR/:
    return exprs([*pre, exp(xor(a, b)), *post]);
  case /[LFDI]ALOAD/:
    return exprs([*pre, exp(aload(a, b)), *post]);
  default:
    fail exprs;
  }  
}

list[Instruction] exprs([*Instruction pre, exp(Exp r), *Instruction args, INVOKEVIRTUAL(cls, methodDesc(ret, name, formals), _), *Instruction post]) 
  = exprs([*pre, exp(invokeVirtual(cls, r, methodDesc(ret, name, formals), [e | exp(e) <- args])), *post])
  when (args == [] && formals == []) || all(a <- args, a is exp), size(args) == size(formals);

list[Instruction] exprs([*Instruction pre, exp(Exp r), *Instruction args, INVOKEINTERFACE(cls, methodDesc(ret, name, formals), _), *Instruction post]) 
  = exprs([*pre, exp(invokeInterface(cls, r, methodDesc(ret, name, formals), [e | exp(e) <- args])), *post])
  when (args == [] && formals == []) || all(a <- args, a is exp), size(args) == size(formals);

list[Instruction] exprs([*Instruction pre, exp(Exp r), *Instruction args, INVOKESPECIAL(cls, methodDesc(ret, name, formals), _), *Instruction post]) 
  = exprs([*pre, exp(invokeSpecial(cls, r, methodDesc(ret, name, formals), [e | exp(e) <- args])), *post])
  when (args == [] && formals == []) || all(a <- args, a is exp), size(args) == size(formals);
  
list[Instruction] exprs([*Instruction pre, NEW(typ), DUP(), *Instruction args, INVOKESPECIAL(cls, constructorDesc(formals), _), *Instruction post]) 
  = exprs([*pre, exp(newInstance(typ, constructorDesc(formals), [e | exp(e) <- args])), *post])
  when (args == [] && formals == []) || all(a <- args, a is exp), size(args) == size(formals);  


list[Instruction] exprs([*Instruction pre, *Instruction args, INVOKESTATIC(cls, methodDesc(ret, name, formals), _), *Instruction post]) 
  = exprs([*pre, exp(invokeStatic(cls, methodDesc(ret, name, formals), [e | exp(e) <- args])), *post])
  when (args == [] && formals == []) || all(a <- args, a is exp), size(args) == size(formals);
    
list[Instruction] exprs([*Instruction pre, exp(const(integer(), int arraySize)), ANEWARRAY(typ), *Instruction elems, *Instruction post]) 
  = exprs([*pre, exp(newArray(typ, [e | [*_, DUP(), exp(const(integer(), _)), exp(e), AASTORE(), *_] := elems])), *post])
  when size(elems) == 4 * arraySize;
            
list[Instruction] exprs([*Instruction pre, exp(a), GETFIELD(cls, name, typ), *Instruction post]) 
  = exprs([*pre, exp(getField(cls, a, typ, name)), *post]);
    
list[Instruction] exprs([*Instruction pre, exp(a), CHECKCAST(typ), *Instruction post]) 
  = exprs([*pre, exp(checkcast(a, typ)), *post]);      

@synopsis{fixed point of exprs has been reached}
default list[Instruction] exprs(list[Instruction] instr) = instr;

Type typ("I") = integer();
Type typ("F") = float();
Type typ("L") = long();
Type typ("D") = double();
Type typ("S") = short();
Type typ("B") = byte();
Type typ("Z") = boolean();