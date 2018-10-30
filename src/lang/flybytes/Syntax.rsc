@doc{

.Synopsis Flybytes is an intermediate language just above the abstraction level of the JVM bytecode language.

.Description

Flybytes is an intermediate language towards JVM bytecode generation for Rascal-based compilers of Domain Specific Languages and Programming Languages.

### Context:

* you are implementing a textual or graphical DSL or a programming language using Rascal
* you want to target the JVM because of its general availability and the JIT compiler
* you do not have time to get into the hairy details of JVM bytecode generation, and do not have time for debugging on the JVM bytecode level
* you do want to profit from the Just In Time (JIT) compiler, so you need idiomatic JVM bytecode that the JIT compiler understands
* you understand the Java programming language pretty well
* you could generate Java code but that would be too slow and require a JDK as a dependency, or you need `invokedynamic` support for your language which Java does not offer.

### Solution:

1. Flybytes is an intermediate abstract syntax tree format that looks a lot like abstract syntax trees for Java code
1. You translate your own abstract syntax trees for your own language directly to Flybytes ASTs using Rascal
1. The Flybytes compiler use the [ASM framework](https://asm.ow2.io/) to generate bytecode in a single pass of the Flybytes AST
   * either the code is directly streamed to a class file (and optionally loaded)
   * or a reasonably clear error message is produced due to an error in the FlyBytes AST.
   
### Presumptions:

* Flybytes does not cover a priori type checking of the input Flybytes AST. So, a proper application of the Flybytes compiler assumes:
   * Your DSL has its own type checker and the compiler is not called if the input code still has serious type errors or name resolution errors (but you could also generate error nodes for partial compilation support)
   * Your compiler to Flybytes ASTs does not introduce new type errors with respect to the JVM's type system.
* Flybytes does not cover much name resolution, so for imports, foreign names and such you have to provide fully qualified names while generating Flybytes ASTs

### Features:

* Protection from ASM and JVM crashes: the Flybytes compiler does some on-the-fly type checking and error reporting in case you generated something weird.
* Tries to generate JVM bytecode that looks like it could have come from a Java compiler
* Offers many Java-like (high-level programming language) features:
   1. local variable names
   1. formal parameter names
   1. structured control flow: if, while, do-while, try-catch-finally, for, break, continue, return, switch
   1. monitor blocks
   1. full expression language (fully hides stack operations of JVM bytecode)
   1. class, method, and variable annotations 
   1. method invocation specialized towards specific JVM instructions (for efficiency's sake)
* Offers symbolic types and method descriptors (as opposed to mangled strings in JVM bytecode)
* Can generate JVM bytecode which would be type-incorrect for Java, but type-correct for the JVM.
* Additional dynamic language support via `invokedynamic` and construction and invocation of bootstrap methods
* Incrementally growing library of macros for typical program element snippets, such as loops over arrays and loops over iterables, etc.

### Status:

* Flybytes is experimental and currently in alpha stage. 
* Expect renamings and API changes
* The language is fully implemented, with the noted exception of nested classes
* The language is fully tested, with the noted exception of the invokedynamic feature

### TODO:

* add support for the JVM debugger (line and symbol information)
* add support for error nodes (to support partial compilation and running partially compiled classes)
* refactor compiler exceptions to Rascal exceptions (to help debugging Flybytes AST generators)
* add support for nested classes (helps in generating code for lambda expressions)

### Citations

The design of Flybtyes was informed by the JVM VM spec, the ASM library code and documentation and the Jitescript API:

* <https://docs.oracle.com/javase/specs/jvms/se8/jvms8.pdf>
* <https://asm.ow2.io/>
* <https://github.com/qmx/jitescript>
}
@author{Jurgen J. Vinju}
module lang::flybytes::Syntax

import List;

data Class(list[Annotation] annotations = [], loc source = |unknown:///|)
  = class(Type \type /* object(str name) */, 
      set[Modifier] modifiers = {\public()},
      Type super              = object(),
      list[Type]   interfaces = [],
      list[Field]  fields     = [], 
      list[Method] methods    = []
      //list[Class] children = [],
    )
  | interface(Type \type /* object(str name) */,
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

data Field(list[Annotation] annotations = [], set[Modifier] modifiers = {\private()})
  = field(Type \type, str name, Exp init = defValue(\type));
         
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
  | object(str name)  
  | array(Type arg)
  | \void()
  | string()
  ;

data Annotation(RetentionPolicy retention=runtime())
  // values _must_ be str, int, real, list[int], list[str], list[real]
  = \anno(str annoClass, Type \type, value val, str name = "value")
  | \tag(str annoClass) /* tag annotation */
  ;
  
data RetentionPolicy
  = class()   // store in the class file, but drop at class loading time
  | runtime() // store in the class file, and keep for reflective access
  | source()  // forget immediately
  ;
 
@doc{optional init expressions will be used at run-time if `null` is passed as actual parameter}
data Formal
  = var(Type \type, str name, Exp init = defValue(\type)); 

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
    
  | /* Generate a call site using a static "bootstrap" method, cache it and invoke it */
    invokeDynamic(BootstrapCall handle, Signature desc, list[Exp] args)
      
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
  | cond(Exp condition, Exp thenExp, Exp elseExp)
  ;
 
Exp defVal(boolean()) = const(boolean(), false);
Exp defVal(integer()) = const(integer(), 0);
Exp defVal(long()) = const(long(), 0);
Exp defVal(byte()) = const(byte(), 0);
Exp defVal(character()) = const(character(), 0);
Exp defVal(short()) = const(short(), 0);
Exp defVal(float()) = const(float(), 0.0);
Exp defVal(double()) = const(double(), 0.0);
Exp defVal(object(str _)) = null();
Exp defVal(array(Type _)) = null();
Exp defVal(string()) = null();
 
 // Below popular some convenience macros for
 // generating methods and constructors:
 
Type object() = object("java.lang.Object");

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
private Type CURRENT = object("\<current\>");

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

// dynamic invoke needs a lot of extra detail, which is all below this line:

@doc{
A bootstrap handle is a name of a static method (as defined by its host class,
its name and its type signature), and a list of constant str arguments (for convenience).

It's advised to use the convenience function below:
  * `bootstrap(Type class, str name, list[BootstrapInfo] args)`
  
That function makes sure to line up any additional information about the call site with
the type of the static bootstrap method.
} 
data BootstrapCall = bootstrap(Type class, str name, Signature desc, list[CallSiteInfo] args);
 
BootstrapCall bootstrap(Type class, str name, list[CallSiteInfo] args)
  = bootstrap(class, name, 
      methodDesc(object("java.lang.invoke.CallSite"),
                 name,
                 [
                    object("java.lang.invoke.MethodHandlers.Lookup"),
                    string(),
                    object("java.lang.invoke.MethodType"),
                    *[callsiteInfoType(a) | a <- args]
                 ]),
       args);

BootstrapCall bootstrap(Type class, str name, list[CallSiteInfo] args)
  = bootstrap(object("\<CURRENT\>"), name, args);
  
@doc{
Convenience function to use existing BootstrapCall information to generate a fitting bootstrap 
method to call.
}  
Method bootstrapMethod(BootstrapCall b, list[Stat] body)
  = method(b.desc, 
      [
         var(object("java.lang.invoke.MethodHandlers.Lookup"), "callerClass"),
         var(string(), "dynMethodName"),
         var(object("java.lang.invoke.MethodType"), "dynMethodType"),
         *[var(callsiteInfoType(args[i]), "info_<i>") | i <- index(args), csi <- args]
      ], 
      block, {\public(), \static()});
      
     
data CallSiteInfo
  = stringInfo(str s)
  | classInfo(str name)
  | integerInfo(int i)
  | longInfo(int l)
  | floatInfo(int f)
  | doubleInfo(int d)
  | methodTypeInfo(Signature desc)
  | // see MethodHandles.lookup().findVirtual for more information
    virtualHandle(Type class, str name, Signature desc)
  | // see MethodHandles.lookup().findSpecial for more information
    specialHandle(Type class, str name, Signature desc, Type caller)
  | // see MethodHandles.lookup().findGetter for more information
    getterHandle(Type class, str name, Type \type)
  | // see MethodHandles.lookup().findSetter for more information
    setterHandle(Type class, str name, Type \type)
  | // see MethodHandles.lookup().findStaticGetter for more information
    staticGetterHandle(Type class, str name, Type \type)
  | // see MethodHandles.lookup().findStaticSetter for more information
    staticSetterHandle(Type class, str name, Type \type)
  | // see MethodHandles.lookup().findConstructor for more information
    constructorHandle(Type class, Signature desc)
  ;
  
Type callsiteInfoType(stringInfo(_))             = string();
Type callsiteInfoType(classInfo(_))              = object("java.lang.Class");
Type callsiteInfoType(integerInfo(_))            = integer();
Type callsiteInfoType(longInfo(_))               = long();
Type callsiteInfoType(floatInfo(_))              = float();
Type callsiteInfoType(doubleInfo(_))             = double();
Type callsiteInfoType(virtualHandle(_,_,_))      = object("java.lang.invoke.MethodHandle");
Type callsiteInfoType(specialHandle(_,_,_,_))    = object("java.lang.invoke.MethodHandle");
Type callsiteInfoType(getterHandle(_,_,_))       = object("java.lang.invoke.MethodHandle");
Type callsiteInfoType(setterHandle(_,_,_))       = object("java.lang.invoke.MethodHandle");
Type callsiteInfoType(staticGetterHandle(_,_,_)) = object("java.lang.invoke.MethodHandle");
Type callsiteInfoType(staticSetterHandle(_,_,_)) = object("java.lang.invoke.MethodHandle");
Type callsiteInfoType(constructorHandle(_,_))    = object("java.lang.invoke.MethodHandle");
Type callsiteInfoType(methodTypeInfo())          = object("java.lang.invoke.MethodType");   
