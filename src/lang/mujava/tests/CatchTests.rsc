module lang::mujava::tests::CatchTests

import lang::mujava::Syntax;
import lang::mujava::Compiler;

Class catchClass() {
  return class(reference("CatchTest"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
           \try([
             do(div(const(integer(),1), const(integer(), 0))),
             \return(const(boolean(), false))
           ],
           [ // Type \type, str name, list[Stat] block
             \catch(reference("java.lang.ArithmeticException"), "e", [
               \return(const(boolean(), true))
             ])
           ],
           [
             // finally
           ]
           ),
           \return(const(boolean(), false))
        ])
      ]
    );
}

test bool testCatch() = loadClass(catchClass(), file=just(|project://mujava/generated/CatchTest.class|))
  .invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
  
