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
  = class(Type \type /* reference(str name) */, 
      set[Modifier] modifiers = {\public()},
      Type super = object(),
      list[Type] interfaces = [],
      list[Field] fields = [], 
      list[Method] methods = [],
      //list[Annotation] annotations = [],
      //list[Class] children = [],
      loc source = |unknown:///|
    )
  | interface(Type \type /* reference(str name) */,
      list[Field] fields = [],
      list[Method] methods = [],
      //list[Annotation] annotations = [],
      loc source = |unknown:///|
    )  
   ;
    
 data Modifier
   = \public()
   | \private()
   | \protected()
   | \friendly()
   | \static()
   | \final()
   ;

data Field
  = field(Type \type, str name, Expression init = defValue(\type), set[Modifier] modifiers = {\private()});
         
data Method
  = method(Signature desc, list[Formal] formals, list[Statement] block, set[Modifier] modifiers = {\public()})
  | method(Signature desc, set[Modifier] modifiers={\abstract(), \public()})
  | static(list[Statement] block)
  ;

Method method(Modifier access, Type ret, str name, list[Formal] formals, list[Statement] block)
  = method(methodDesc(ret, name, [ var.\type | var <- formals]), formals, block, modifiers={access});

data Signature 
  = methodDesc(Type \return, str name, list[Type] formals)
  | constructorDesc(list[Type] formals)
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
  | reference(str name)
  | array(Type arg)
  | \void()
  | string()
  ;

data Annotation; // TODO

data Formal = var(Type \type, str name, Expression init = defValue(\type)); 

@doc{Structured programming, OO primitives, JVM monitor blocks and breakpoints}
data Statement(loc src = |unknown:///|)
  = \store(str name, Expression \value)
  | \decl(Type \type, str name, Expression init = defValue(\type))
  | \astore(Expression array, Expression index, Expression arg)
  | \do(Expression exp) // pops the result of the expression when needed
  | \return()
  | \return(Expression arg)
  | \putField(Type class, Expression receiver, Type \type, str name, Expression arg)
  | \putStatic(Type class, str name, Type \type, Expression arg)
  | \if(Expression condition, list[Statement] thenBlock)
  | \if(Expression condition, list[Statement] thenBlock, list[Statement] elseBlock)
  | \for(list[Statement] init, Expression condition, list[Statement] next, list[Statement] statements)
  
  //| \while(Expression condition, list[Statement] block)
 // TODO: these are still to be implemented:
  
  //| \while(Expression condition, list[Statement] block)
  //| \doWhile(list[Statement] block, Expression condition)
  
  //| \try(list[Statement] tryBlock, list[Catch] \catchBlock, list[Statement] \finallyBlock)
  //| label(str label)
  //| \goto(str label)
  //| \switch(Expression \value, list[Case] caseBlocks, list[Statement] defaultBlock)
  //| \throw(Expression exception)
  //| monitor(Expression lock, list[Statement] block)
  ;

// TODO:
//data Statement(loc src = |unknown:///|)  
//  = assertEquals(Expression lhs, Expression rhs)
//  | assertNotEquals(Expression lhs, Expression rhs)
//  | assertTrue(Expression e)
//  | assertFalse(Expression e)
//  ;
  
data Case = \case(int label, list[Statement] block);
  
data Catch = \catch(Type \type, str var, list[Statement] block);

data Expression(loc src = |unknown:///|, bool wide = \false())
  = null()
  | \true()
  | \false()
  | load(str name)
  | aload(Expression array, Expression index)
  | \const(Type \type, value constant)
  | block(list[Statement] statements, Expression arg)
  
  | /* For invoking static methods of classes or interfaces */
    invokeStatic(Type class, Signature desc, list[Expression] args)
  
  | /* If no dynamic dispatch is needed, or searching superclasses is required, and you know which class 
     * implements the method, use this to invoke a method for efficiency's sake. 
     * The invocation is checked at class load time. 
     */
    invokeSpecial(Type class, Expression receiver, Signature desc, list[Expression] args)
  
  | /* If you do need dynamic dispatch, or the method is implemented in a superclass, and this is
     * not a default method of an interface, use this invocation method. You need to be sure the method
     * exists _somewhere_ reachable from the \class reference type.
     * The invocation checked at class load time. 
     */
    invokeVirtual(Type class, Expression receiver, Signature desc, list[Expression] args)
  
  | /* For invoking methods you know only from interfaces, such as default methods. 
     * The method can even be absent at runtime in which case this throws a RuntimeException. 
     * The check occurs at the first invocation at run-time. 
     */
    invokeInterface(Type class, Expression receiver, Signature desc, list[Expression] args)
  
  | /* Invoke a super constructor, typically only used in constructor method bodies */
    invokeSuper(Signature desc, list[Expression] args)
    
  | newInstance(Type class, Signature desc, list[Expression] args)
  | getField(Type class, Expression receiver, Type \type, str name)
  | getStatic(Type class, Type \type, str name)
  | instanceof(Expression arg, Type class)
  | eq(Expression lhs, Expression rhs)
  | ne(Expression lhs, Expression rhs)
  | le(Expression lhs, Expression rhs)
  | gt(Expression lhs, Expression rhs)
  | ge(Expression lhs, Expression rhs)
  | lt(Expression lhs, Expression rhs)
  | newArray(Type \type, Expression size)
  | newArray(Type \type, list[Expression] args)
  | alength(Expression arg)
  | checkcast(Expression arg, Type \type)
  | coerce(Type from, Type to, Expression arg)
  | shr(Expression lhs, Expression shift)
  | shl(Expression lhs, Expression shift)
  | ushr(Expression lhs, Expression shift)
  | and(Expression lhs, Expression rhs)
  | or(Expression lhs, Expression rhs)
  | xor(Expression lhs, Expression rhs)
  | add(Expression lhs, Expression rhs)
  | sub(Expression lhs, Expression rhs)
  | div(Expression lhs, Expression rhs)
  | rem(Expression lhs, Expression rhs)
  | mul(Expression lhs, Expression rhs)
  | neg(Expression arg)
  | inc(str name, int inc)
  ;
 
Expression defVal(boolean()) = const(boolean(), false);
Expression defVal(integer()) = const(integer(), 0);
Expression defVal(long()) = const(long(), 0);
Expression defVal(byte()) = const(byte(), 0);
Expression defVal(character()) = const(character(), 0);
Expression defVal(short()) = const(short(), 0);
Expression defVal(float()) = const(float(), 0.0);
Expression defVal(double()) = const(double(), 0.0);
Expression defVal(reference(str _)) = null();
Expression defVal(array(Type _)) = null();
Expression defVal(string()) = null();
 
 // Below popular some convenience macros for
 // generating methods and constructors:
 
Type object() = reference("java.lang.Object");

Statement invokeSuper(list[Type] formals, list[Expression] args)
  = do(invokeSuper(constructorDesc(formals), args));
  
Statement invokeSuper()
  = invokeSuper([], []);
  
// generate a main method
Method main(str args, list[Statement] block) 
  = method(methodDesc(\void(), "main", [array(string())]), 
      [var(array(string()), args)], 
      block, 
      modifiers={\public(), \static(), \final()});
      
// generate a normal method 
Method method(Modifier access, Type ret, str name, list[Formal] args, list[Statement] block)
  = method(methodDesc(ret, name, [a.\type | a <- args]), 
           args, 
           block, 
           modifiers={access});
 
// generate a static method           
Method staticMethod(Modifier access, Type ret, str name, list[Formal] args, list[Statement] block)
  = method(methodDesc(ret, name, [a.\type | a <- args]), 
           args, 
           block, 
           modifiers={static(), access});

// generate a constructor with argument and code 
//   NB: don't forget to generate super call in the block!    
Method constructor(Modifier access, list[Formal] formals, list[Statement] block)
  = method(constructorDesc([ var.\type | var <- formals]), formals, block);
  
// allocate a new object using the constructor with the given argument types,
// and passing the given actual parameters      
Expression new(Type class, list[Type] argTypes, list[Expression] args)
  = newInstance(class, constructorDesc(argTypes), args);
  
// allocate a new object via its nullary constructor  
Expression new(Type class)
  = new(class, [], []);
     
// Load the standard "this" reference for every object. 
// NB! This works only inside non-static methods and inside constructors 
Expression this() = load("this");

private Type CURRENT = reference("\<current\>");

// Load a field from the currently defined class
Expression getField(Type \type, str name)
  = getField(CURRENT, this(), \type, name);
 
// Load a static field from the currently defined class  
Expression getStatic(Type \type, str name)
  = getStatic(CURRENT, \type, name);
  
// Store a field in the currently defined class  
Statement putField(Type \type, str name, Expression arg)
  = putField(CURRENT, this(), \type, name, arg);  

// Store a static field in the currently defined class
Statement putStatic(Type \type, str name, Expression arg)
  = putStatic(CURRENT, name, \type, arg);
 
Expression invokeStatic(Signature desc, list[Expression] args) 
  = invokeStatic(CURRENT, desc, args);
  
Expression invokeSpecial(Expression receiver, Signature desc, list[Expression] args)
  = invokeSpecial(CURRENT, receiver, desc, args);

Expression invokeVirtual(Expression receiver, Signature desc, list[Expression] args)
  = invokeVirtual(CURRENT, receiver, desc, args);
  
Expression invokeInterface(Expression receiver, Signature desc, list[Expression] args)
  = invokeVirtual(CURRENT, receiver, desc, args);
   