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

* <https://docs.oracle.com/javase/specs/jvms/se8/jvms8.pdf>
* <https://asm.ow2.io/>
* <https://github.com/qmx/jitescript>
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

@doc{Structured programming, JVM monitor blocks and breakpoints}
data Statement(loc src = |unknown:///|)
  = \store(str var, Expression \value)
  | \fieldAssign(str class, str var, Expression \value)
  | \if(Expression condition, list[Statement] block)
  | \if(Expression condition, list[Statement] thenBlock, list[Statement] elseBlock)
  | \while(Expression condition, list[Statement] block)
  | \doWhile(list[Statement] block, Expression condition)
  | \for(list[Statement] init, Expression condition, list[Statement] next, list[Statement] block)
  | \return()
  | \return(Expression arg)
  | \invokeSuper(Signature desc, list[Expression] args)
  | \invokeThis(Signature desc, list[Expression] args)
  | \try(list[Statement] tryBlock, list[Catch] \catchBlock, list[Statement] \finallyBlock)
  | label(str label)
  | \goto(str label)
  | \switch(Expression label, list[Case] caseBlocks, list[Statement] defaultBlock)
  | \throw(Expression exception)
  | monitor(Expression lock, list[Statement] block)
  ;

@doc{Some basic macros for the convenience of the code generator}  
data Statement(loc src = |unknown:///|)  
  = stdout(Expression e) // short hand for debug println to stdout
  | stderr(Expression e) // short hand for debug println to stderr
  | assertEquals(Expression lhs, Expression rhs)
  | assertNotEquals(Expression lhs, Expression rhs)
  | assertTrue(Expression e)
  | assertFalse(Expression e)
  ;
  
data Case = \case(int label, list[Statement] block);
  
data Catch = \catch(Type \type, str var, list[Statement] block);

data Expression(loc src = |unknown:///|)
  = load(str name)
  | null()
  | \true()
  | \false()
  | \const(Type \type, value \value)
  | block(list[Statement] block, Expression result)
  | invokeStatic(str class, Signature desc, list[Expression] args)
  | invokeSpecial(Expression receiver, Signature desc, list[Expression] args)
  | invokeVirtual(Expression receiver, Signature desc, list[Expression] args)
  | invokeInterface(Signature desc, Expression receiver, list[Expression] args)
  | newInstance(str class, Signature desc, list[Expression] arguments)
  | field(Expression object, str name)
  | instanceof(Expression arg, Type \type)
  | eq(Expression lhs, Expression rhs)
  | newarray(Type \type, Expression size)
  | alength(Expression array)
  | aload(Type \type, Expression array, Expression index)
  | astore(Type \type, Expression array, Expression index, Expression \value)
  | cmple(Type \type, Expression lhs, Expression rhs)
  | cmpgt(Type \type, Expression lhs, Expression rhs)
  | cmpge(Type \type, Expression lhs, Expression rhs)
  | cmplt(Type \type, Expression lhs, Expression rhs)
  | cmpne(Expression lhs, Expression rhs)
  | cmpeq(Expression lhs, Expression rhs)
  | checkcast(Expression lhs, Type \type)
  | coerce(Type from, Type to, Expression arg)
  | nonnull(Expression lhs)
  | null(Expression lhs)
  | shr(Type \type, Expression lhs, Expression shift)
  | shl(Type \type, Expression lhs, Expression shift)
  | ushl(Type \type, Expression lhs, Expression shift)
  | ushr(Type \type, Expression lhs, Expression shift)
  | cmp(Type \type, Expression lhs, Expression rhs)
  | and(Type \type, Expression lhs, Expression rhs)
  | or(Type \type, Expression lhs, Expression rhs)
  | xor(Type \type, Expression lhs, Expression rhs)
  | add(Type \type, Expression lhs, Expression rhs)
  | sub(Type \type, Expression lhs, Expression rhs)
  | div(Type \type, Expression lhs, Expression rhs)
  | rem(Type \type, Expression lhs, Expression rhs)
  | mul(Type \type, Expression lhs, Expression rhs)
  | neg(Type \type, Expression arg)
  | inc(Expression arg, Expression inc)
  ;
  
