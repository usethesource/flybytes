module lang::mujava::tests::SwitchTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;

Class switchClass() 
  = class(reference("SwitchClass"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [var(integer(), "par")],
        [ 
          \switch(load("par"), [
            \case(42, [
              \return(const(integer(), 42))
            ]),
            \case(12, [
              \return(const(integer(), 12))
            ])
          ]),
          \return(const(integer(), 0))          
        ])
      ]
    );
    
bool testSwitchClass(Class c, int input, int result) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", [integer()]), [integer(input)]).toValue(#int) == result;
} 

test bool simpleSwitch1() = testSwitchClass(switchClass(), 42, 42);
test bool simpleSwitch2() = testSwitchClass(switchClass(), 42, 12);
test bool simpleSwitch3() = testSwitchClass(switchClass(), 18, 0);

Class switchDefaultClass() 
  = class(reference("SwitchDefaultClass"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [var(integer(), "par")],
        [ 
          \switch(load("par"), [
            \case(42, [
              \return(const(integer(), 42))
            ]),
            \case(12, [
              \return(const(integer(), 12))
            ]),
            \default([
              \return(sub(load("par"), const(integer(), 1)))
            ])
          ]),
          \return(const(integer(), 0))          
        ])
      ]
    );
    

test bool simpleDefaultSwitch1() = testSwitchClass(switchDefaultClass(), 42, 42);
test bool simpleDefaultSwitch2() = testSwitchClass(switchDefaultClass(), 12, 12);
test bool simpleDefaultSwitch3() = testSwitchClass(switchDefaultClass(), 0, -1);