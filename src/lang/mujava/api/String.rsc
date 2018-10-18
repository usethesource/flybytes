module lang::mujava::api::String

import lang::mujava::Syntax;

Exp String_format(Exp e, str format) 
    = invokeStatic(reference("java.lang.String"), methodDesc(string(), "format", [string(), array(object())]),
           [const(string(), format), newArray(object(), [e])]);
           
           
Exp String_concat(Exp l, Exp r) 
  = invokeVirtual(reference("java.lang.String"), l, methodDesc(string(), "concat", [reference("java.lang.String")]), [r]);           