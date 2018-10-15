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

data Class(list[Annotation] annotations = [], loc source = |unknown:///|)
  = class(Type \type /* reference(str name) */, 
      set[Modifier] modifiers = {\public()},
      Type super              = object(),
      list[Type]   interfaces = [],
      list[Field]  fields     = [], 
      list[Method] methods    = []
      //list[Class] children = [],
    )
  | interface(Type \type /* reference(str name) */,
      list[Field]  fields  = [],
      list[Method] methods = []
    )  
   ;
    
data Modifier
   = \public()
   | \private()
   | \protected()
   | \friendly()
   | \static()
   | \final()
   | \synchronized()
   ;

data Field
  = field(Type \type, str name, Exp init = defValue(\type), set[Modifier] modifiers = {\private()});
         
data Method(list[Annotation] annotations = [])
  = method(Signature desc, list[Formal] formals, list[Stat] block, set[Modifier] modifiers = {\public()})
  | method(Signature desc, set[Modifier] modifiers={\abstract(), \public()})
  | static(list[Stat] block)
  ;

Method method(Modifier access, Type ret, str name, list[Formal] formals, list[Stat] block)
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
  | reference(str name) /* TODO: rename to "object" like it is called in the ASM framework
// | method() /* TODO: add method handles */
  | array(Type arg)
  | \void()
  | string()
  ;

data Annotation
  // values _must_ be str, int, real, list[int], list[str], list[real], or nested further: list[list[int]]
  = \anno(str annoClass, Type \type, str name, value val, RetentionPolicy retention=runtime(), list[Annotation] annotations = [])
  | \anno(str annoClass, RetentionPolicy retention=runtime())
  ;
  
data RetentionPolicy
  = class()   // store in the class file, but drop at class loading time
  | runtime() // store in the class file, and keep for reflective access
  | source()  // forget immediately
  ;
 
@doc{optional init expressions will be used at run-time if `null` is passed as actual parameter}
data Formal = var(Type \type, str name, Exp init = defValue(\type)); 

@doc{Structured programming, OO primitives, JVM monitor blocks and breakpoints}
data Stat(loc src = |unknown:///|)
  = \store(str name, Exp \value)
  | \decl(Type \type, str name, Exp init = defValue(\type))
  | \astore(Exp array, Exp index, Exp arg)
  | \do(Exp exp) 
  | \incr(str name, int inc)
  | \return()
  | \return(Exp arg)
  | \putField(Type class, Exp receiver, Type \type, str name, Exp arg)
  | \putStatic(Type class, str name, Type \type, Exp arg)
  | \if(Exp condition, list[Stat] thenBlock)
  | \if(Exp condition, list[Stat] thenBlock, list[Stat] elseBlock)
  | \for(list[Stat] init, 
         Exp condition, 
         list[Stat] next, 
         list[Stat] statements, str label = "")
  | \block(list[Stat] block, str label = "") 
  | \break(str label = "")
  | \continue(str label = "")
  | \while(Exp condition, list[Stat] block, str label = "") 
  | \doWhile(list[Stat] block, Exp condition, str label = "") 
  | \throw(Exp arg) 
  | \monitor(Exp arg, list[Stat] block)  
  | \try(list[Stat] block, list[Handler] \catch) 
  | \switch(Exp arg, list[Case] cases, SwitchOption option = lookup(/*for best performance on current JVMs*/)) 
  ;

data SwitchOption
  = table()
  | lookup()
  | auto()
  ;
  
data Case 
  = \case(int key, list[Stat] block)
  | \default(list[Stat] block)
  ;
  
data Handler 
  = \catch(Type \type, str name, list[Stat] block)
  | \finally(list[Stat] block)
  ;

data Exp(loc src = |unknown:///|)
  = null()
  | \true()
  | \false()
  | load(str name)
  | aload(Exp array, Exp index)
  | \const(Type \type, value constant)
  | sblock(list[Stat] statements, Exp arg)
  
  | /* For invoking static methods of classes or interfaces */
    invokeStatic(Type class, Signature desc, list[Exp] args)
  
  | /* If no dynamic dispatch is needed, or searching superclasses is required, and you know which class 
     * implements the method, use this to invoke a method for efficiency's sake. 
     * The invocation is checked at class load time. 
     */
    invokeSpecial(Type class, Exp receiver, Signature desc, list[Exp] args)
  
  | /* If you do need dynamic dispatch, or the method is implemented in a superclass, and this is
     * not a default method of an interface, use this invocation method. You need to be sure the method
     * exists _somewhere_ reachable from the \class reference type.
     * The invocation checked at class load time. 
     */
    invokeVirtual(Type class, Exp receiver, Signature desc, list[Exp] args)
  
  | /* For invoking methods you know only from interfaces, such as default methods. 
     * The method can even be absent at runtime in which case this throws a RuntimeException. 
     * The check occurs at the first invocation at run-time. 
     */
    invokeInterface(Type class, Exp receiver, Signature desc, list[Exp] args)
  
  | /* Invoke a super constructor, typically only used in constructor method bodies */
    invokeSuper(Signature desc, list[Exp] args)
    
  | newInstance(Type class, Signature desc, list[Exp] args)
  | getField(Type class, Exp receiver, Type \type, str name)
  | getStatic(Type class, Type \type, str name)
  | instanceof(Exp arg, Type class)
  | eq(Exp lhs, Exp rhs)
  | ne(Exp lhs, Exp rhs)
  | le(Exp lhs, Exp rhs)
  | gt(Exp lhs, Exp rhs)
  | ge(Exp lhs, Exp rhs)
  | lt(Exp lhs, Exp rhs)
  | newArray(Type \type, Exp size)
  | newArray(Type \type, list[Exp] args)
  | alength(Exp arg)
  | checkcast(Exp arg, Type \type)
  | coerce(Type from, Type to, Exp arg)
  | shr(Exp lhs, Exp shift)
  | shl(Exp lhs, Exp shift)
  | ushr(Exp lhs, Exp shift)
  | and(Exp lhs, Exp rhs)
  | or(Exp lhs, Exp rhs)
  | xor(Exp lhs, Exp rhs)
  | add(Exp lhs, Exp rhs)
  | sub(Exp lhs, Exp rhs)
  | div(Exp lhs, Exp rhs)
  | rem(Exp lhs, Exp rhs)
  | mul(Exp lhs, Exp rhs)
  | neg(Exp arg)
  | inc(str name, int inc)
  ;
 
Exp defVal(boolean()) = const(boolean(), false);
Exp defVal(integer()) = const(integer(), 0);
Exp defVal(long()) = const(long(), 0);
Exp defVal(byte()) = const(byte(), 0);
Exp defVal(character()) = const(character(), 0);
Exp defVal(short()) = const(short(), 0);
Exp defVal(float()) = const(float(), 0.0);
Exp defVal(double()) = const(double(), 0.0);
Exp defVal(reference(str _)) = null();
Exp defVal(array(Type _)) = null();
Exp defVal(string()) = null();
 
 // Below popular some convenience macros for
 // generating methods and constructors:
 
Type object() = reference("java.lang.Object");

Stat invokeSuper(list[Type] formals, list[Exp] args)
  = do(invokeSuper(constructorDesc(formals), args));
  
Stat invokeSuper()
  = invokeSuper([], []);
  
// main method shorthand
Method main(str args, list[Stat] block) 
  = method(methodDesc(\void(), "main", [array(string())]), 
      [var(array(string()), args)], 
      block, 
      modifiers={\public(), \static(), \final()});
      
// normal method shorthand
Method method(Modifier access, Type ret, str name, list[Formal] args, list[Stat] block)
  = method(methodDesc(ret, name, [a.\type | a <- args]), 
           args, 
           block, 
           modifiers={access});
 
// static method shorthand           
Method staticMethod(Modifier access, Type ret, str name, list[Formal] args, list[Stat] block)
  = method(methodDesc(ret, name, [a.\type | a <- args]), 
           args, 
           block, 
           modifiers={static(), access});

// constructor shorthand with arguments and code 
//   NB: don't forget to generate super call in the block!    
Method constructor(Modifier access, list[Formal] formals, list[Stat] block)
  = method(constructorDesc([ var.\type | var <- formals]), formals, block);
  
// "new" short-hand with parameters
Exp new(Type class, list[Type] argTypes, list[Exp] args)
  = newInstance(class, constructorDesc(argTypes), args);
  
// "new" short-hand, without parameters  
Exp new(Type class)
  = new(class, [], []);
     
// Load the standard "this" reference for every object. 
// NB! This works only inside non-static methods and inside constructors 
Exp this() = load("this");

// the "<current>" class refers to the class currently being generated
private Type CURRENT = reference("\<current\>");

// Load a field from the currently defined class
Exp getField(Type \type, str name) = getField(CURRENT, this(), \type, name);
 
// Load a static field from the currently defined class  
Exp getStatic(Type \type, str name) = getStatic(CURRENT, \type, name);
  
// Store a field in the currently defined class  
Stat putField(Type \type, str name, Exp arg) = putField(CURRENT, this(), \type, name, arg);  

// Store a static field in the currently defined class
Stat putStatic(Type \type, str name, Exp arg) = putStatic(CURRENT, name, \type, arg);
 
Exp invokeStatic(Signature desc, list[Exp] args) = invokeStatic(CURRENT, desc, args);
  
Exp invokeSpecial(Exp receiver, Signature desc, list[Exp] args)
  = invokeSpecial(CURRENT, receiver, desc, args);

Exp invokeVirtual(Exp receiver, Signature desc, list[Exp] args)
  = invokeVirtual(CURRENT, receiver, desc, args);
  
Exp invokeInterface(Exp receiver, Signature desc, list[Exp] args)
  = invokeVirtual(CURRENT, receiver, desc, args);
   
Exp iconst(int i) = const(integer(), i);
Exp sconst(int i) = const(short(), i);
Exp bconst(int i) = const(byte(), i);
Exp cconst(int i) = const(character(), i);
Exp zconst(bool i) = const(boolean(), i);
Exp jconst(int i) = const(long(), i);
Exp sconst(str i) = const(string(), i);
Exp dconst(real i) = const(double(), i);
Exp fconst(real i) = const(float(), i);

   