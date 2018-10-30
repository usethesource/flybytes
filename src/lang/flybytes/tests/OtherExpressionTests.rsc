module lang::flybytes::tests::OtherExpressionTests

import lang::flybytes::Syntax;
import lang::flybytes::Compiler;

Class incExpClass() {
  return class(object("IncTest"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
           decl(integer(), "i", init=iconst(1)),
           do(inc("i", 1)),
           \return(eq(load("i"), iconst(2)))
        ])
      ]
    );
}

test bool testInc() = loadClass(incExpClass())
  .invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);
  
Class incStatClass() {
  return class(object("IncTest"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
           decl(integer(), "i", init=iconst(1)),
           incr("i", 1),
           \return(eq(load("i"), iconst(2)))
        ])
      ]
    );
}

test bool testIncStat() = loadClass(incStatClass())
  .invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);  

Class newExpClass()
  = class(object("newExpTest"),
      methods=[
        staticMethod(\public(), boolean(), "testMethod", [], [
           // i = new Integer(42)
           decl(object("java.lang.Integer"), "i", init=new(object("java.lang.Integer"),[integer()], [iconst(42)])),
           // j = new Integer(42)
           decl(object("java.lang.Integer"), "j", init=new(object("java.lang.Integer"),[integer()], [iconst(42)])),
           // return i.equals(j)
           \return(invokeVirtual(object("java.lang.Object"), load("i"), methodDesc(boolean(), "equals", [object("java.lang.Object")]), [load("j")]))
        ])
      ]
    );  
    
test bool testNew() = loadClass(newExpClass())
   .invokeStatic(methodDesc(boolean(), "testMethod", []), []).toValue(#bool);   