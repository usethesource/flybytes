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
  | classType(str name)
  | array(Type arg)
  | \void()
  | string()
  ;

data Annotation; // TODO

data Block
  = block(list[Variable] variables, list[Statement] statements)
  ;
 
data Variable = var(Type \type, str name); 

@doc{Structured programming, OO primitives, JVM monitor blocks and breakpoints}
data Statement(loc src = |unknown:///|)
  = \store(str name, Expression \value)
  | \aastore(Type \type, Expression array, Expression index, Expression arg)
  | \do(Type \type, Expression exp) // pops the result of the expression when needed
  | \return()
  | \return(Type \type, Expression arg)
  | \putField(str class, Expression receiver, Type \type, str name, Expression arg)
  | \putStatic(str class, str name, Type \type, Expression arg)
  | \if(Expression condition, list[Statement] thenBlock)
  | \if(Expression condition, list[Statement] thenBlock, list[Statement] elseBlock)
  
 // TODO: these are still to be implemented:
 //| \for(list[Statement] init, Expression condition, list[Statement] next, list[Statement] block)
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
  | aaload(Type \type, Expression array, Expression index)
  | \const(Type \type, value constant)
  | block(list[Statement] statements, Expression arg)
  | invokeStatic(str class, Signature desc, list[Expression] args)
  | invokeSpecial(str class, Expression receiver, Signature desc, list[Expression] args)
  | invokeVirtual(str class, Expression receiver, Signature desc, list[Expression] args)
  | invokeInterface(str class, Expression receiver, Signature desc, list[Expression] args)
  | invokeSuper(Signature desc, list[Expression] args)
  | newInstance(str class, Signature desc, list[Expression] args)
  | getField(str class, Expression receiver, Type \type, str name)
  | getStatic(str class, Type \type, str name)
  | instanceof(Expression arg, str class)
  | eq(Type \type, Expression lhs, Expression rhs)
  | ne(Type \type, Expression lhs, Expression rhs)
  | le(Type \type, Expression lhs, Expression rhs)
  | gt(Type \type, Expression lhs, Expression rhs)
  | ge(Type \type, Expression lhs, Expression rhs)
  | lt(Type \type, Expression lhs, Expression rhs)
  | newArray(Type \type, Expression size)
  | newArray(Type \type, list[Expression] args)
  | alength(Expression arg)
  | checkcast(Expression arg, Type \type)
  | coerce(Type from, Type to, Expression arg)
  | nonnull(Expression arg)
  | null(Expression arg)
  | shr(Type \type, Expression lhs, Expression shift)
  | shl(Type \type, Expression lhs, Expression shift)
  | ushr(Type \type, Expression lhs, Expression shift)
  | and(Type \type, Expression lhs, Expression rhs)
  | or(Type \type, Expression lhs, Expression rhs)
  | xor(Type \type, Expression lhs, Expression rhs)
  | add(Type \type, Expression lhs, Expression rhs)
  | sub(Type \type, Expression lhs, Expression rhs)
  | div(Type \type, Expression lhs, Expression rhs)
  | rem(Type \type, Expression lhs, Expression rhs)
  | mul(Type \type, Expression lhs, Expression rhs)
  | neg(Type \type, Expression arg)
  | inc(str name, int inc)
  ;
 
 // for run-time efficiency we simplify all null checks:
 Expression eq(null(), Expression arg) = null(arg);
 Expression eq(Expression arg, null()) = null(arg);
 Expression eq(nonnull(), Expression arg) = nonnull(arg);
 Expression eq(Expression arg, nonnull()) = nonnull(arg);
 Expression ne(null(), Expression arg) = nonnull(arg);
 Expression ne(Expression arg, null()) = nonnull(arg);
 Expression ne(nonnull(), Expression arg) = null(arg);
 Expression ne(Expression arg, nonnull()) = null(arg);
 
 // Below popular some convenience macros for
 // generating methods and constructors:
 
Type object() = classType("java.lang.Object");

Statement invokeSuper(list[Type] formals, list[Expression] args)
  = do(\void(), invokeSuper(constructorDesc(formals), args));
  
Statement invokeSuper()
  = invokeSuper([], []);
  
// generate a main method
Method main(str args, Block block) 
  = method(methodDesc(\void(), "main", [array(string())]), 
      [var(array(string()), args)], 
      block, 
      modifiers={\public(), \static(), \final()});
      
// generate a normal method 
Method method(Modifier access, Type ret, str name, list[Variable] args, Block block)
  = method(methodDesc(ret, name, [a.\type | a <- args]), 
           args, 
           block, 
           modifiers={access});
 
 // generate a normal method without local variables
Method method(Modifier access, Type ret, str name, list[Variable] args, list[Statement] stats)
  = method(access, ret, name, args, block([], stats));
             
// generate a static method           
Method staticMethod(Modifier access, Type ret, str name, list[Variable] args, Block block)
  = method(methodDesc(ret, name, [a.\type | a <- args]), 
           args, 
           block, 
           modifiers={static(), access});

// generate a static method without local variables
Method staticMethod(Modifier access, Type ret, str name, list[Variable] args, list[Statement] stats)
  = staticMethod(access, ret, name, args, block([], stats));
    
// generate a default constructor for classes which have no supertype  
Method defaultConstructor(Modifier access)
  = method(constructorDesc([]), [], block([], [
      invokeSuper(),
      \return()
  ]), modifiers={access});   
 
// generate a default constructor based on a given superclass
Method defaultConstructor(Modifier access, str super)
  = method(constructorDesc([]), [], block([], [
      do(\void(), invokeSuper(super)),
      \return()
  ])); 
  
// generate a constructor with argument and code 
//   NB: don't forget to generate super call in the block!    
Method constructor(Modifier access, list[Variable] formals, Block block)
  = method(constructorDesc([ var.\type | var <- formals]), formals, block);
  
Method constructor(Modifier access, list[Variable] formals, list[Statement] stats)
  = method(constructorDesc([ var.\type | var <- formals]), formals, block([], stats));

// allocate a new object using the constructor with the given argument types,
// and passing the given actual parameters      
Expression new(str class, list[Type] argTypes, list[Expression] args)
  = newInstance(class, constructorDesc(argTypes), args);
  
// allocate a new object via its nullary constructor  
Expression new(str class)
  = new(class, [], []);
     
// Load the standard "this" reference for every object. 
// NB! This works only inside non-static methods and inside constructors 
Expression this() = load("this");

// Load a field from the currently defined class
Expression getField(Type \type, str name)
  = getField("\<current\>", this(), \type, name);
 
// Load a static field from the currently defined class  
Expression getStatic(Type \type, str name)
  = getStatic("\<current\>", \type, name);
  
// Store a field in the currently defined class  
Statement putField(Type \type, str name, Expression arg)
  = putField("\<current\>", this(), \type, name, arg);  

// Store a static field in the currently defined class
Statement putStatic(Type \type, str name, Expression arg)
  = putStatic("\<current\>", name, \type, arg);
 
Expression invokeStatic(Signature desc, list[Expression] args) 
  = invokeStatic("\<current\>", desc, args);
  
Expression invokeSpecial(Expression receiver, Signature desc, list[Expression] args)
  = invokeSpecial("\<current\>", receiver, desc, args);

Expression invokeVirtual(Expression receiver, Signature desc, list[Expression] args)
  = invokeVirtual("\<current\>", receiver, desc, args);
  
Expression invokeInterface(Expression receiver, Signature desc, list[Expression] args)
  = invokeVirtual("\<current\>", receiver, desc, args);
   