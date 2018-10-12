module lang::mujava::tests::SwitchTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import Node;

Class switchClass(SwitchOption option) 
  = class(reference("SwitchClass_<getName(option)>"),
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
          ],option=option),
          \return(const(integer(), 0))          
        ])
      ]
    );
    
bool testSwitchClass(Class c, int input, int result) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", [integer()]), [integer(input)]).toValue(#int) == result;
} 

test bool simpleSwitch1table() = testSwitchClass(switchClass(table()), 42, 42);
test bool simpleSwitch2table() = testSwitchClass(switchClass(table()), 12, 12);
test bool simpleSwitch3table() = testSwitchClass(switchClass(table()), 18, 0);

test bool simpleSwitch1lookup() = testSwitchClass(switchClass(lookup()), 42, 42);
test bool simpleSwitch2lookup() = testSwitchClass(switchClass(lookup()), 12, 12);
test bool simpleSwitch3lookup() = testSwitchClass(switchClass(lookup()), 18, 0);

test bool simpleSwitch1auto() = testSwitchClass(switchClass(auto()), 42, 42);
test bool simpleSwitch2auto() = testSwitchClass(switchClass(auto()), 12, 12);
test bool simpleSwitch3auto() = testSwitchClass(switchClass(auto()), 18, 0);


Class switchDefaultClass(SwitchOption option) 
  = class(reference("SwitchDefaultClass_<getName(option)>"),
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
          ],option=option),
          \return(const(integer(), 0))          
        ])
      ]
    );
    

test bool simpleDefaultSwitch1Table() = testSwitchClass(switchDefaultClass(table()), 42, 42);
test bool simpleDefaultSwitch2Table() = testSwitchClass(switchDefaultClass(table()), 12, 12);
test bool simpleDefaultSwitch3Table() = testSwitchClass(switchDefaultClass(table()), 0, -1);

test bool simpleDefaultSwitch1Lookup() = testSwitchClass(switchDefaultClass(lookup()), 42, 42);
test bool simpleDefaultSwitch2Lookup() = testSwitchClass(switchDefaultClass(lookup()), 12, 12);
test bool simpleDefaultSwitch3Lookup() = testSwitchClass(switchDefaultClass(lookup()), 0, -1);

test bool simpleDefaultSwitch1Auto() = testSwitchClass(switchDefaultClass(auto()), 42, 42);
test bool simpleDefaultSwitch2Auto() = testSwitchClass(switchDefaultClass(auto()), 12, 12);
test bool simpleDefaultSwitch3Auto() = testSwitchClass(switchDefaultClass(auto()), 0, -1);

Class switchCompactClass(SwitchOption option) 
  = class(reference("SwitchDefaultClass_<getName(option)>"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [var(integer(), "par")],
        [ 
          \switch(load("par"), [
            \case(0, [
              \return(const(integer(), 0))
            ]),
            \case(1, [
              \return(const(integer(), 1))
            ]),
            \case(2, [
              \return(const(integer(), 2))
            ]),
             \case(3, [
              \return(const(integer(), 3))
            ]),
             \case(4, [
              \return(const(integer(), 4))
            ]),
             \case(5, [
              \return(const(integer(), 5))
            ]),
            \default([
              \return(sub(load("par"), const(integer(), 1)))
            ])
          ],option=option),
          \return(const(integer(), 0))          
        ])
      ]
    );
    

test bool compactDefaultSwitch1Table() = testSwitchClass(switchCompactClass(table()), 0, 0);
test bool compactDefaultSwitch2Table() = testSwitchClass(switchCompactClass(table()), 1, 1);
test bool compactDefaultSwitch3Table() = testSwitchClass(switchCompactClass(table()), 2, 2);
test bool compactDefaultSwitch4Table() = testSwitchClass(switchCompactClass(table()), 6, 5);

//test bool compactDefaultSwitch1Lookup() = testSwitchClass(switchCompactClass(lookup()),  0, 0);
//test bool compactDefaultSwitch2Lookup() = testSwitchClass(switchCompactClass(lookup()),  1, 1);
//test bool compactDefaultSwitch3Lookup() = testSwitchClass(switchCompactClass(lookup()),  2, 2);
//test bool compactDefaultSwitch4Lookup() = testSwitchClass(switchCompactClass(lookup()),  6, 5);
//
//test bool compactDefaultSwitch1Auto() = testSwitchClass(switchCompactClass(auto()), 0, 0);
//test bool compactDefaultSwitch2Auto() = testSwitchClass(switchCompactClass(auto()), 1, 1);
//test bool compactDefaultSwitch3Auto() = testSwitchClass(switchCompactClass(auto()), 2, 2);
//test bool compactDefaultSwitch4Auto() = testSwitchClass(switchCompactClass(auto()), 6, 5);


