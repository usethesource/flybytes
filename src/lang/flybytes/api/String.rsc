module lang::flybytes::api::String

import lang::flybytes::Syntax;

Exp String_format(Exp e, str format) 
    = invokeStatic(object("java.lang.String"), methodDesc(string(), "format", [string(), array(object())]),
           [const(string(), format), newInitArray(object(), [e])]);
           
           
Exp String_concat(Exp l, Exp r) 
  = invokeVirtual(object("java.lang.String"), l, methodDesc(string(), "concat", [object("java.lang.String")]), [r]);           
