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
* untyped method parameter names, untyped local variable names and typed field names

muJava does not offer, by design:

* name analysis (all names must be fully qualified, all names must be unique)
* type analysis or type checking 
* overloading of operators or overloading of method names (type signatures must be provided, and specific expressions for specific types)

The design is informed by the JVM VM spec, the ASM library code and documentation and the Jitescript API:

* <https://docs.oracle.com/javase/specs/jvms/se8/jvms8.pdf>
* <https://asm.ow2.io/>
* <https://github.com/qmx/jitescript>

MuJava is verbose because its compiler does not know anything about types and names,
other than what is provided literally in a muJava program. The muJava compiler avoids 
desugaring since that would require AST construction as compile time, and it avoids allocating
any other kind of objects as much as possible. 

It is recommended to write Rascal functions which can 
serve as desugaring macros to facilitate repetitive tasks in a muJava generator. A library
of such convenience macros is present at the end of this module for general purpose JVM code
generation. 
}
@author{Jurgen J. Vinju}
module lang::mujava::Syntax

data Class
  = class(Type \type /* classType(str name) */, 
      set[Modifier] modifiers = {\public()},
      str super = "java.lang.Object",
      list[str] interfaces = [],
      list[Field] fields = [], 
      list[Method] methods = [],
      list[Annotation] annotations = [],
      list[Class] children = [],
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
  = field(Type \type, str name, value \default = \null(), set[Modifier] modifiers = {\private()});
         
data Method
  = method(Signature desc, list[Variable] formals, Block block, set[Modifier] modifiers = {\public()})
  ;

Method method(Modifier access, Type ret, str name, list[Variable] formals, Block block)
  = method(methodDesc(ret, name, [ var.\type | var <- formals]), formals, block, modifiers={access});

data Signature 
  = methodDesc(Type \return, str name, list[Type] formals)
  ;

data Type
  = byte()
  | boolean()
  | short()
  | character()
  | integer()
  | float()
  | double()
  | long()
  | classType(str name)
  | array(Type arg)
  | \void()
  ;

data Annotation; // TODO

data Block
  = block(list[Variable] variables, list[Statement] statements)
  ;
 
data Variable = var(Type \type, str name); 

@doc{Structured programming, OO primitives, JVM monitor blocks and breakpoints}
data Statement(loc src = |unknown:///|)
  = \store(str name, Expression \value)
  | \do(bool isVoid, Expression exp)
  | \putField(Expression receiver, str fieldName, Expression \value)
  | \putStatic(str class, str fieldName, Expression \value)
  | \if(Expression condition, list[Statement] block)
  | \if(Expression condition, list[Statement] thenBlock, list[Statement] elseBlock)
  | \while(Expression condition, list[Statement] block)
  | \doWhile(list[Statement] block, Expression condition)
  | \for(list[Statement] init, Expression condition, list[Statement] next, list[Statement] block)
  | \return()
  | \return(Type \type, Expression arg)
  | \try(list[Statement] tryBlock, list[Catch] \catchBlock, list[Statement] \finallyBlock)
  | label(str label)
  | \goto(str label)
  | \switch(Expression \value, list[Case] caseBlocks, list[Statement] defaultBlock)
  | \throw(Expression exception)
  | monitor(Expression lock, list[Statement] block)
  ;

data Statement(loc src = |unknown:///|)  
  = assertEquals(Expression lhs, Expression rhs)
  | assertNotEquals(Expression lhs, Expression rhs)
  | assertTrue(Expression e)
  | assertFalse(Expression e)
  ;
  
data Case = \case(int label, list[Statement] block);
  
data Catch = \catch(Type \type, str var, list[Statement] block);

data Expression(loc src = |unknown:///|, bool wide = \false())
  = null()
  | \true()
  | \false()
  | load(str name)
  | \const(Type \type, value constant)
  | block(list[Statement] block, Expression result)
  | invokeStatic(str class, Signature desc, list[Expression] args)
  | invokeSpecial(str class, Expression receiver, Signature desc, list[Expression] args)
  | invokeVirtual(str class, Expression receiver, Signature desc, list[Expression] args)
  | invokeInterface(str class, Expression receiver, Signature desc, list[Expression] args)
  | newInstance(str class, Signature desc, list[Expression] args)
  | getField(Expression object, str name)
  | getStatic(str class, Type \type, str name)
  | instanceof(Expression arg, Type \type)
  | eq(Expression lhs, Expression rhs)
  | newarray(Type \type, Expression size)
  | alength(Expression array)
  | aaload(Expression array, Expression index)
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
 
 
 // below some typical macros for the sake of convenience:
 
Type string() = classType("java.lang.String");

Type object() = classType("java.lang.Object");

Method main(str args, Block block) 
  = method(methodDesc(\void(), "main", [array(string())]), [var(array(string()), args)], block, modifiers={\public(), \static(), \final()});
  
Method defaultConstructor(Modifier access)
  = method(constructorDesc([]), [], block([], [
      do(true, invokeSuper("java.lang.Object")),
      \return()
  ]));   
 
Method defaultConstructor(Modifier access, str super)
  = method(constructorDesc([]), [], block([], [
      do(true, invokeSuper(super)),
      \return()
  ])); 
  
Signature constructorDesc(list[Type] formals) 
  = methodDesc(\void(), "\<init\>", formals);   
    
Expression invokeSuper(str super) = invokeSuper(super, [], []);
  
Expression invokeSuper(str super, list[Type] formals, list[Expression] args)
  = invokeSpecial(super, this(), constructorDesc(formals), args);  
    
Method constructor(Modifier access, str class, list[Variable] formals, Block block)
  = method(constructorDesc([ var.\type | var <- formals]), formals, block);
      
Expression new(str class, list[Type] argTypes, list[Expression] args)
  = newInstance(class, constructorDesc(argTypes), args);
  
Expression new(str class)
  = new(class, [], []);
      
Expression toString(Expression object) 
   = invokeVirtual(object, methodDesc(string(), "toString", []), []);

Expression hashCode(Expression object) 
   = invokeVirtual(object, methodDesc(integer(), "hashCode", []), []);
   
Expression equals(Expression object, Expression compared) 
   = invokeVirtual(object, methodDesc(boolean(), "equals", [object()]), [compared]);
  
Expression index(str array, int index)
   = aaload(load(array), const(integer(), index));
   
Expression index(str array, Expression index)
   = aaload(load(array), index);  
    
Statement stdout(Expression arg)
   = \do(true, println("out", arg));

Statement stderr(Expression arg)
   = \do(true, println("err", arg));
         
Expression println(str stream, Expression arg)
   = invokeVirtual("java.io.PrintStream", getStatic("java.lang.System", classType("java.io.PrintStream"), stream), 
         methodDesc(\void(), "println", [object()]), [arg]);         
 
Expression this() = load("this");