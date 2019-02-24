module lang::flybytes::tests::MonitorTests

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

// These test check if the monitor is properly closed in all kinds of situations; break, throw and return.
// If not, than the bytecode verifier should throw a IllegalMonitorStateException.

Class monitorBreakClass() 
  = class(object("MonitorBreak"),
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
  = class(object("MonitorReturn"),
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
    
Class monitorThrowClass() 
  = class(object("MonitorThrow"),
      methods=[
        staticMethod(\public(), integer(), "testMethod", [var(integer(), "par")],
        [ 
          \try([
            \for([decl(integer(), "i", init=iconst(0))], lt(load("i"), iconst(10)), [incr("i",1)], [
               monitor(new(object()), [
                 \if(eq(rem(load("i"),iconst(2)),iconst(0)), [
                   \throw(new(object("java.lang.IllegalArgumentException")))
                 ])
               ])
            ]),
            \return(iconst(-1))
          ],[
            \catch(object("java.lang.IllegalArgumentException"), "e", [
              \return(load("par"))
            ])
          ])          
        ])
      ]
    );    
        
bool testMonitorClass(Class c, int input, int result) { 
  m = loadClass(c, file=just(|project://flybytes/generated/<c.\type.name>.class|));
  return m.invokeStatic(methodDesc(boolean(), "testMethod", [integer()]), [integer(input)]).toValue(#int) == result;
} 

test bool monitorBreakTest() = testMonitorClass(monitorBreakClass(), 10, 10);
test bool monitorReturnTest() = testMonitorClass(monitorReturnClass(), 10, 10);
test bool monitorUncaughtThrowTest() = testMonitorClass(monitorThrowClass(), 10, 10);

