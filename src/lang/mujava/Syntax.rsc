@doc{

.Synopsis muJava is an intermediate language just above the abstraction level of the JVM bytecode language.

.Description

Its design goal is to serve as a code generation back-end:

* to be easily generated from (abstract) syntax trees of programming/domain-specific languages
* to be mapped really fast to JVM bytecode using the ASM library
* to support all of the JVM via what the ASM library has to offer 

The main utility of the language above the JVM language is:

* symbolic types and method descriptors (as opposed to mangled strings)
* nested expression language (as opposed to stack operations)
* structured statement language (as opposed to a flat list of bytecodes)

muJava does not offer, by design:

* name analysis (all names must be fully qualified, all names must be unique)
* type analysis or type checking 
* overloading of operators or overloading of method names (type signatures must be provided, and specific expressions for specific types)

The design is informed by the JVM VM spec, the ASM library code and documentation and the Jitescript API:
* https://docs.oracle.com/javase/specs/jvms/se8/jvms8.pdf
* https://asm.ow2.io/
* https://github.com/qmx/jitescript
}
@author{Jurgen J. Vinju}
module lang::mujava::Syntax

data Class
  = class(str name, 
      set[Modifier] modifiers = {\public()},
      str super = "java.lang.Object",
      set[str] interfaces = [],
      set[Field] fields = [], 
      set[Method] methods = [],
      set[Annotation] annotations = [],
      set[Class] children = [],
      loc source = |unknown:///|
    );
    
 data Modifier
   = \public()
   | \private()
   | \protected()
   | \friendly()
   | \static()
   | \final()
   ;

data Field
  = field(Type \type, str name, Expression \default = \null(), set[Modifier] modifiers = {\private()});
         
data Method
  = method(Signature desc, Block block, set[Modifier] modifiers = {\public()})
  ;

data Signature = methodDesc(Type \return, str name, list[Type] formals);
 
data Type
  = byte()
  | boolean()
  | short()
  | character()
  | integer()
  | float()
  | double()
  | long()
  | class(str name)
  | array(Type arg)
  ;

data Annotation; // TODO

data Block
  = block(list[Variable] variables, list[Statement] statements)
  ;
 
data Variable = var(Type \type, str name); 

data Statement(loc src = |unknown:///|)
  = \varAssign(str var, Expression \value)
  | \fieldAssign(str class, str var, Expression \value)
  | \if(Expression condition, list[Statement] block)
  | \if(Expression condition, list[Statement] thenBlock, list[Statement] elseBlock)
  | \while(Expression condition, list[Statement] block)
  | \doWhile(list[Statement] block, Expression condition)
  | \for(list[Statement] init, Expression condition, list[Statement] next, list[Statement] block)
  | \return()
  | \invokeSuper(Signature desc, list[Expression] args)
  | \invokeThis(Signature desc, list[Expression] args)
  | \return(Expression \value)
  | \try(list[Statement] tryBlock, list[Catch] \catchBlock, list[Statement] \finallyBlock)
  | label(str label)
  | stdout(Expression e) // short hand for debug println to stdout
  | stderr(Expression e) // short hand for debug println to stderr
  | panic(Expession e)   // not a user exception, but a VM exception due to badly generated code
  | \goto(str label)
  | \switch(Expression label, list[Case] caseBlocks, list[Statement] defaultBlock)
  | \throw(Expression exception)
  | monitor(Expression lock, list[Statement] block)
  ;
  
data Case = \case(int label, list[Statement] block);
  
data Catch = \catch(Type \type, str var, list[Statement] block);

data Expression(loc src = |unknown:///|)
  = var(str name)
  | null()
  | \true()
  | \false()
  | \iconst(int i)
  | \fconst(real f)
  | \dconst(real d)
  | block(list[Statement] block, Expression result)
  | invokeStatic(Signature desc, list[Expression] args)
  | invokeSpecial(Signature desc, Expression receiver, list[Expression] args)
  | invokeVirtual(Signature desc, Expression receiver, list[Expression] args)
  | invokeInterface(Signature desc, Expression receiver, list[Expression] args)
  | newInstance(str class, list[Expression] arguments)
  | field(Expression object, str name)
  | instanceof(Expression arg, Type \type)
  | eq(Expression lhs, Expression rhs)
  | acmpne(Expression lhs, Expression rhs)
  | acmpeq(Expression lhs, Expression rhs)
  | icmple(Expression lhs, Expression rhs)
  | icmpgt(Expression lhs, Expression rhs)
  | icmplt(Expression lhs, Expression rhs)
  | icmpne(Expression lhs, Expression rhs)
  | icmpeq(Expression lhs, Expression rhs)
  | fcmpl(Expression lhs, Expression rhs)
  | fcmpg(Expression lhs, Expression rhs)
  | dcmpl(Expression lhs, Expression rhs)
  | dcmpg(Expression lhs, Expression rhs)
  | icmpge(Expression lhs, Expression rhs)
  | cast(Expression lhs, Type \type)
  | nonnull(Expression lhs)
  | null(Expression lhs)
  | lt(Expression lhs, Expression rhs)
  | le(Expression lhs, Expression rhs)
  | gt(Expression lhs, Expression rhs)
  | ge(Expression lhs, Expression rhs)
  | length(Expression array)
  | ishr(Expression lhs, Expression shift)
  | ishl(Expression lhs, Expression shift)
  | ushl(Expression lhs, Expression shift)
  | lshr(Expression lhs, Expression shift)
  | lshl(Expression lhs, Expression shift)
  | lshr(Expression lhs, Expression shift)
  | lcmp(Expression lhs, Expression rhs)
  | iand(Expression lhs, Expression rhs)
  | ior(Expression lhs, Expression rhs)
  | ixor(Expression lhs, Expression rhs)
  | land(Expression lhs, Expression rhs)
  | lor(Expression lhs, Expression rhs)
  | lxor(Expression lhs, Expression rhs)
  | iadd(Expression lhs, Expression rhs)
  | ladd(Expression lhs, Expression rhs)
  | fadd(Expression lhs, Expression rhs)
  | dadd(Expression lhs, Expression rhs)
  | isub(Expression lhs, Expression rhs)
  | lsub(Expression lhs, Expression rhs)
  | dsub(Expression lhs, Expression rhs)
  | fsub(Expression lhs, Expression rhs)
  | idiv(Expression lhs, Expression rhs)
  | irem(Expression lhs, Expression rhs)
  | ineg(Expression arg)
  | i2d(Expression arg)
  | i2l(Expression arg)
  | i2f(Expression arg)
  | i2s(Expression arg)
  | i2b(Expression arg)
  | ldiv(Expression lhs, Expression rhs)
  | lrem(Expression lhs, Expression rhs)
  | lneg(Expression arg)
  | l2d(Expression arg)
  | l2i(Expression arg)
  | l2f(Expression arg)
  | fdiv(Expression lhs, Expression rhs)
  | frem(Expression lhs, Expression rhs)
  | fneg(Expression arg)
  | f2d(Expression arg)
  | f2i(Expression arg)
  | f2l(Expression arg)
  | ddiv(Expression lhs, Expression rhs)
  | drem(Expression lhs, Expression rhs)
  | dneg(Expression lhs, Expression rhs)
  | d2f(Expression arg)
  | d2i(Expression arg)
  | d2l(Expression arg)
  | imul(Expression lhs, Expression rhs)
  | fmul(Expression lhs, Expression rhs)
  | dmul(Expression lhs, Expression rhs)
  | iinc(Expression inc)
  ;
  
