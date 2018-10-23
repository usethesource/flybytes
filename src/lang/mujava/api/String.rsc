module lang::mujava::api::String

import lang::mujava::Syntax;

Exp String_format(Exp e, str format) 
    = invokeStatic(object("java.lang.String"), methodDesc(string(), "format", [string(), array(object())]),
           [const(string(), format), newArray(object(), [e])]);
           
           
Exp String_concat(Exp l, Exp r) 
  = invokeVirtual(object("java.lang.String"), l, methodDesc(string(), "concat", [object("java.lang.String")]), [r]);           