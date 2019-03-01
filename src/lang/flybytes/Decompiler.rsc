module lang::flybytes::Decompiler

extend lang::flybytes::Disassembler;

import Exception;
import String;
import IO;
import List; 

@synopsis{Decompile a JVM classfile to Flybytes ASTs, recovering statement and expression structures.}
Class decompile(loc classFile) throws IO { 
  cls = disassemble(classFile);
  
  return cls[methods = [decompile(m) | m <- cls.methods]];
}

Method decompile(Method m:method(_, _, [asm(list[Instruction] instrs)])) {  
  withoutLines = lines(instrs);
  withJumps = jumps(withoutLines);
  withoutLabels = labels(withJumps);
  withExp = exprs(withoutLabels);
  withStat = stmts(withExp);
  cleanStats = clean([asm(withStat)]);

  return m[block=cleanStats];  
}

// LINES
data Instruction(int line = -1);
data Exp(int line = -1);
data Stat(int line = -1);

list[Instruction] lines([*Instruction pre, LINENUMBER(lin, lab), Instruction next:!LINENUMBER(_,_), *Instruction post])
  = lines([*pre, next[line=lin], *lines([LINENUMBER(lin, lab), *post])]);

list[Instruction] lines([*Instruction pre, LINENUMBER(_, _), Instruction next:LINENUMBER(_,_), *Instruction post])
  = lines([*pre, *lines([next, *post])]);
  
list[Instruction] lines([*Instruction pre, LINENUMBER(_, _)])
  = pre;  
 
default list[Instruction] lines(list[Instruction] l) = l;
  
// JUMP LABEL PROTECTION
data Instruction(bool jumpTarget = false);

list[Instruction] jumps([*Instruction pre, Instruction jump:/IF_|GOTO|IFNULL|IFNONNULL|JSR/(str l1), *Instruction mid, LABEL(l1, jumpTarget=false), *Instruction post]) 
  = jumps([*pre, jump, *jumps([*mid, LABEL(l1, jumpTarget=true), *post])]);  
  
list[Instruction] jumps([*Instruction pre, LABEL(l1, jumpTarget=false), *Instruction mid, Instruction jump:/IF_|GOTO|IFNULL|IFNONNULL|JSR/(str l1), *Instruction post]) 
  = labels([*pre, LABEL(l1, jumpTarget=true), *jumps([*mid, jump, *post])]);    

default list[Instruction] jumps(list[Instruction] l) = l;
 
// LABEL REMOVAL
list[Instruction] labels([*Instruction pre,  LABEL(_, jumpTarget=false), *Instruction post]) 
  = [*pre, *labels(post)];  

default list[Instruction] labels(list[Instruction] l) = l;

  
// STATEMENTS
  
list[Instruction] stmts([*Instruction pre, exp(a), /[ILFDA]RETURN/(), *Instruction post]) 
  = stmts([*pre, stat(\return(a)), *post]);

list[Instruction] stmts([*Instruction pre, exp(rec), exp(arg), PUTFIELD(cls, name, typ), *Instruction post]) 
  = stmts([*pre, stat(putField(cls, rec, typ, name, arg)), *post]);
              
list[Instruction] stmts([*Instruction pre, RETURN(), *Instruction post]) 
  = stmts([*pre, stat(\return()), *post]);

list[Instruction] exprs([*Instruction pre, exp(a), exp(b), /IF_<op:EQ|NE|LT|GE|GT|LE|ICMP(EQ|NE|LT|GE|LE)|ACMP(EQ|NE)>/(str l1), *Instruction thenPart, LABEL(l1), *Instruction post]) 
  = exprs([*pre, stat(\if(invertedCond(op)(a, b), [asm(stmts(thenPart))])), *post]);

list[Instruction] exprs([*Instruction pre, exp(a), /IF_<op:NULL|NONNULL>/(l1), *Instruction thenPart, LABEL(l1), *Instruction post]) 
  = exprs([*pre, stat(\if(invertedCond(op)(a), [asm(stmts(thenPart))])), *post]);

list[Instruction] stmts([*Instruction pre, stat(\return(Exp e)), NOP(), *Instruction post]) 
  = stmts([*pre, stat(\return(e)), *post]);

list[Instruction] stmts([*Instruction pre, stat(\return(Exp e)), ATHROW(), *Instruction post]) 
  = stmts([*pre, stat(\return(e)), *post]);

default list[Instruction] stmts(list[Instruction] st) = st;

// EXPRESSIONS

list[Instruction] exprs([*Instruction pre, /[AIFL]LOAD/(int var), *Instruction mid, Instruction lv:LOCALVARIABLE(str name, _, _, _, var), *Instruction post]) 
  = exprs([*pre, exp(load(name)), *mid, lv, *post]);

list[Instruction] exprs([*Instruction pre, NOP(), *Instruction post]) 
  = exprs([*pre, *exprs(post)]);

list[Instruction] exprs([*Instruction pre, ACONST_NULL(), *Instruction post]) 
  = exprs([*pre, exp(null()), *exprs(post)]);
  
list[Instruction] exprs([*Instruction pre, /<t:[IFLD]>CONST_<i:[0-5]>/(), *Instruction post]) 
  = exprs([*pre, exp(const(typ(t), toInt(i))), *exprs(post)]);

list[Instruction] exprs([*Instruction pre, exp(a), ARRAYLENGTH(), *Instruction post]) 
  = exprs([*pre, exp(alength(a)), *exprs(post)]);
  
list[Instruction] exprs([*Instruction pre, exp(a), /[IFLD]NEG/(), *Instruction post]) 
  = exprs([*pre, exp(neg(a)), *exprs(post)]);

list[Instruction] exprs([*Instruction pre, exp(Exp a), exp(Exp b), /[LFDI]<op:(ADD|SUB|MUL|DIV|REM|SHL|SHR|AND|OR|XOR|ALOAD)>/(), *Instruction post]) 
  = exprs([*pre, exp(binOp(op)(a,b)), *exprs(post)]);

list[Instruction] exprs([*Instruction pre, exp(Exp r), *Instruction args, INVOKEVIRTUAL(cls, methodDesc(ret, name, formals), _), *Instruction post]) 
  = exprs([*pre, exp(invokeVirtual(cls, r, methodDesc(ret, name, formals), [e | exp(Exp e) <- args])), *post])
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

list[Instruction] exprs([*Instruction pre, exp(load("this")), *Instruction args, INVOKESPECIAL(cls, constructorDesc(formals), _), *Instruction post]) 
  = exprs([*pre, exp(invokeSuper(constructorDesc(formals), [e | exp(e) <- args])), *post])
  when (args == [] && formals == []) || all(a <- args, a is exp), size(args) == size(formals);

list[Instruction] exprs([*Instruction pre, *Instruction args, INVOKESTATIC(cls, methodDesc(ret, name, formals), _), *Instruction post]) 
  = exprs([*pre, exp(invokeStatic(cls, methodDesc(ret, name, formals), [e | exp(e) <- args])), *post])
  when (args == [] && formals == []) || all(a <- args, a is exp), size(args) == size(formals);
    
list[Instruction] exprs([*Instruction pre, exp(const(integer(), int arraySize)), ANEWARRAY(typ), *Instruction elems, *Instruction post]) 
  = exprs([*pre, exp(newArray(typ, [e | [*_, DUP(), *l1, exp(const(integer(), _)), exp(e), AASTORE(), *_] := elems])), *post])
  when size(elems) == 4 * arraySize;

list[Instruction] exprs([*Instruction pre, GETSTATIC(cls, name, typ), *Instruction post]) 
  = exprs([*pre, exp(getStatic(cls, typ, name)), *post]);
            
list[Instruction] exprs([*Instruction pre, exp(a), GETFIELD(cls, name, typ), *Instruction post]) 
  = exprs([*pre, exp(getField(cls, a, typ, name)), *post]);
    
list[Instruction] exprs([*Instruction pre, exp(a), CHECKCAST(typ), *Instruction post]) 
  = exprs([*pre, exp(checkcast(a, typ)), *post]);  
  
list[Instruction] exprs([*Instruction pre, LDC(typ, constant), *Instruction post]) 
  = exprs([*pre, exp(const(typ, constant)), *post]);        

list[Instruction] exprs([*Instruction pre, exp(a), exp(b), /IF_<op:EQ|NE|LT|GE|GT|LE|ICMP(EQ|NE|LT|GE|LE)|ACMP(EQ|NE)>/(l1), LABEL(_), LINENUMBER(_,_), exp(ifBranch), GOTO(l2), LABEL(l1), LINENUMBER(_,_), exp(elseBranch), LABEL(l2), LINENUMBER(_,_), *Instruction post]) 
  = exprs([*pre, exp(cond(invertedCond(op)(a, b), ifBranch, elseBranch)), *post]);

list[Instruction] exprs([*Instruction pre, exp(a), /IF_<op:NULL|NONNULL>/(l1), LABEL(_), LINENUMBER(_,_), exp(ifBranch), GOTO(l2), LABEL(l1), LINENUMBER(_,_), exp(elseBranch), LABEL(l2), LINENUMBER(_,_), *Instruction post]) 
  = exprs([*pre, exp(cond(invertedCond(op)(a), ifBranch, elseBranch)), *post]);

default list[Instruction] exprs(list[Instruction] instr) = instr;


// MAPS

Type typ("I") = integer();
Type typ("F") = float();
Type typ("L") = long();
Type typ("D") = double();
Type typ("S") = short();
Type typ("B") = byte();
Type typ("Z") = boolean();

alias BinOp = Exp (Exp, Exp);

BinOp invertedCond("EQ") = ne;
BinOp invertedCond("NE") = eq;
BinOp invertedCond("LT") = ge;
BinOp invertedCond("GE") = lt;
BinOp invertedCond("GT") = le;
BinOp invertedCond("LE") = gt;
BinOp invertedCond("ICMPEQ") = ne;
BinOp invertedCond("ICMPNE") = eq;
BinOp invertedCond("ICMPLT") = ge;
BinOp invertedCond("ICMPGE") = lt;
BinOp invertedCond("ICMPLE") = gt;
BinOp invertedCond("ACMPEQ") = ne;
BinOp invertedCond("ACMPNE") = eq;

BinOp binOp("ADD") = add;
BinOp binOp("SUB") = sub;
BinOp binOp("MUL") = mul;
BinOp binOp("DIV") = div;
BinOp binOp("REM") = rem;
BinOp binOp("SHL") = shl;
BinOp binOp("SHR") = shr;
BinOp binOp("AND") = and;
BinOp binOp("OR") = or;
BinOp binOp("XOR") = xor;
BinOp binOp("ALOAD") = aload;

alias UnOp = Exp (Exp);

UnOp invertedCond("NULL") = nonnull;
UnOp invertedCond("NONNULL") = null;

Exp nonnull(Exp e) = ne(e, null());
Exp null(Exp e)    = eq(e, null());

// CLEANING UP LEFT-OVER STRUCTURES

list[Stat] clean([*Stat pre, asm([*Instruction preI, LOCALVARIABLE(_,_,_,_,_), *Instruction postI]), *Stat post]) 
  = clean([*pre, asm([*preI, *postI]), *post]);
  
list[Stat] clean([*Stat pre, asm([*Instruction preI, LABEL(_), *Instruction postI]), *Stat post]) 
  = clean([*pre, asm([*preI, *postI]), *post]);  

list[Stat] clean([*Stat pre, asm([*Instruction preI, stat(s), *Instruction postI]), *Stat post])
  = clean([*pre, asm(preI), s, asm(postI), *post]);

list[Stat] clean([*Stat pre, asm([*Instruction preI, exp(a), *Instruction postI]), *Stat post])
  = clean([*pre, asm(preI), do(a), asm(postI), *post]); 
  
list[Stat] clean([*Stat pre, asm([]), *Stat post])
  = clean([*pre, *post]);
   
default list[Stat] clean(list[Stat] x) = x; 