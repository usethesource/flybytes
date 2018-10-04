module lang::mujava::tests::NoCastCheckTest

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import lang::mujava::api::Vallang;
import lang::mujava::api::Object;
import lang::mujava::api::System;
import IO;

// do we need to downcast to call methods or not?

private Expression VF = load("VF");

Class noUpCastTestClass() =
  class(reference("NoUpcastTestClass"), 
        methods=[
          staticMethod(\public(), boolean(), "testMethod", [], block(
          [
            var(IValueFactory, "VF"),
            var(IInteger, "i")
          ],
          [
            store("VF", getValueFactory()),
            store("i", vfInteger(VF, 8)),
            \return(equals(load("i"), INumber_Add(vfInteger(VF, 4), vfInteger(VF, 4))))
          ]))
        ]
       );
       
bool testCastClass(Class c) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
}      

test bool upcastTest() = testCastClass(noUpCastTestClass());

Class noDownCastTestClass() =
  class(reference("NoDowncastTestClass"), 
        methods=[
          staticMethod(\public(), boolean(), "testMethod", [], block(
          [
            var(IValueFactory, "VF"),
            var(object(), "i"),
            var(object(), "j")
          ],
          [
            store("VF", getValueFactory()),
            store("i", vfInteger(VF, 8)),
            store("j", vfInteger(VF, 4)),
            \return(equals(load("i"), INumber_Add(load("j"), load("j"))))
          ]))
        ]
       );  
       
 test bool downcastTest() = testCastClass(noDownCastTestClass());      