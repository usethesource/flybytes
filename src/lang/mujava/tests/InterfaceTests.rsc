module lang::mujava::tests::InterfaceTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;

Class interfA() 
  = interface(object("Interface_A"),
      methods=[
        method(methodDesc(\integer(), "methodA", []))
      ]
    );
    
Class interfB() 
  = interface(object("Interface_B"),
      methods=[
        method(methodDesc(\integer(), "methodB", []))
      ]
    );    
    
Class implInvokeInterface() 
  = class(object("Impl_A"),
      methods=[
        method(\public(), \boolean(), "testMethod", [], [
          \return(and(eq(invokeInterface(this(), methodDesc(\integer(), "methodA",[]), []), iconst(1)),
                      eq(invokeInterface(this(), methodDesc(\integer(), "methodB",[]), []), iconst(2))
                 ))
        ]), 
        
        // override
        method(\public(), \integer(), "methodA", [], [
          \return(iconst(1))
        ]),
        
        // override
        method(\public(), \integer(), "methodB", [], [
          \return(iconst(2))
        ])
      ]
   );
   
Class implInvokeVirtual() 
  = class(object("Impl_A"),
      interfaces=[object("Interface_A"), object("Interface_B")],
      methods=[
        method(\public(), \boolean(), "testMethod", [], [
          \return(and(eq(invokeVirtual(this(), methodDesc(\integer(), "methodA",[]), []), iconst(1)),
                      eq(invokeVirtual(this(), methodDesc(\integer(), "methodB",[]), []), iconst(2))
                 ))
        ]), 
        
        // override
        method(\public(), \integer(), "methodA", [], [
          \return(iconst(1))
        ]),
        
        // override
        method(\public(), \integer(), "methodB", [], [
          \return(iconst(2))
        ])
      ]
   );   
   
test bool implementAbstractMethodInvokeVirtual() {
  // load the classes together
  cs = loadClasses([interfA(), interfB(), implInvokeVirtual()], prefix=just(|project://mujava/generated/|));
  
  // get a mirror instance of the class that implements the two interfaces
  c = cs["Impl_A"];
  i = c.newInstance(constructorDesc([]),[]);
  
  // call test method which uses two implemented interface methods
  return i.invoke(methodDesc(\void(), "testMethod", []), []).toValue(#bool);
}  

test bool implementAbstractMethodInvokeInterface() {
  // load the classes together
  cs = loadClasses([interfA(), interfB(), implInvokeInterface()], prefix=just(|project://mujava/generated/|));
  
  // get a mirror instance of the class that implements the two interfaces
  c = cs["Impl_A"];
  i = c.newInstance(constructorDesc([]),[]);
  
  // call test method which uses two implemented interface methods
  return i.invoke(methodDesc(\void(), "testMethod", []), []).toValue(#bool);
}  

Class interfDefault() 
  = interface(object("Interface_Default"),
      methods=[
        method(\public(), \integer(), "defaultMethod", [], [
          \return(iconst(-17))
        ])
      ]
    );

Class implInvokeDefault() 
  = class(object("Impl_B"),
     interfaces=[object("Interface_Default")],
      methods=[
        method(\public(), \boolean(), "testMethod", [], [
          \return(eq(invokeInterface(this(), methodDesc(\integer(), "defaultMethod",[]), []), iconst(-17))
                 )
        ]) 
      ]
   );   
   
test bool testInterfaceDefaultMethod() {
  // load the classes together, default methods require version 1.8
  cs = loadClasses([interfDefault(), implInvokeDefault()], prefix=just(|project://mujava/generated/|), version=v1_8());
  
  // get a mirror instance of the class that implements the two interfaces
  c = cs["Impl_B"];
  i = c.newInstance(constructorDesc([]),[]);
  
  // call test method which uses two implemented interface methods
  return i.invoke(methodDesc(\void(), "testMethod", []), []).toValue(#bool);
}   
   
