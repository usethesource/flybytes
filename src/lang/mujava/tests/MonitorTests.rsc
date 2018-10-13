module lang::mujava::tests::MonitorTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;
import Node;

// these are really just some smoke tests to see if the code generator does not crash.
// need to add tests which test if the monitor is left using MONITOREXIT even if a break, return or
// throw happened.

Class monitorBreakClass() 
  = class(reference("MonitorBreak"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [var(integer(), "par")],
        [ 
          \for([decl(integer(), "i", init=iconst(0))], lt(load("i"), iconst(10)), [incr("i",1)], [
             monitor(new(object()), [
               \if(eq(rem(load("i"),iconst(2)),iconst(0)), [
                 \break()
               ])
             ])
          ]),
          \return(load("par"))          
        ])
      ]
    );
    
Class monitorReturnClass() 
  = class(reference("MonitorReturn"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [var(integer(), "par")],
        [ 
          \for([decl(integer(), "i", init=iconst(0))], lt(load("i"), iconst(10)), [incr("i",1)], [
             monitor(new(object()), [
               \if(eq(rem(load("i"),iconst(2)),iconst(0)), [
                 \return(load("par"))
               ])
             ])
          ]),
          \return(iconst(-1))          
        ])
      ]
    );
        
bool testMonitorClass(Class c, int input, int result) { 
  m = loadClass(c, file=just(|project://mujava/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", [integer()]), [integer(input)]).toValue(#int) == result;
} 

test bool monitorBreakTest() = testMonitorClass(monitorBreakClass(), 10, 10);
test bool monitorReturnTest() = testMonitorClass(monitorReturnClass(), 10, 10);

