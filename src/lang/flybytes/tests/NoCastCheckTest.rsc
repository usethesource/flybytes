module lang::flybytes::tests::NoCastCheckTest

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;
import lang::flybytes::api::Vallang;
import lang::flybytes::api::Object;

// do we need to downcast to call methods or not?

private Exp VF = load("VF");

Class noUpCastTestClass() =
  class(object("NoUpcastTestClass"), 
        methods=[
          staticMethod(\public(), boolean(), "testMethod", [], 
          [
            decl(IValueFactory, "VF", init=getValueFactory()),
            decl(IInteger, "i", init=vfInteger(VF, 8)),
            \return(equals(load("i"), INumber_Add(vfInteger(VF, 4), vfInteger(VF, 4))))
          ])
        ]
       );
       
bool testCastClass(Class c) { 
  m = loadClass(c, file=just(|project://flybytes/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
}      

test bool upcastTest() = testCastClass(noUpCastTestClass());

Class noDownCastTestClass() =
  class(object("NoDowncastTestClass"), 
        methods=[
          staticMethod(\public(), boolean(), "testMethod", [],
          [
            decl(IValueFactory, "VF", init=getValueFactory()),
            decl(object(), "i", init=vfInteger(VF, 8)),
            decl(object(), "j", init=vfInteger(VF, 4)),
            \return(equals(load("i"), INumber_Add(load("j"), load("j"))))
          ])
        ]
       );  
       
 test bool downcastTest() = testCastClass(noDownCastTestClass());      
