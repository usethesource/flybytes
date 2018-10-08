module lang::mujava::api::String

import lang::mujava::Syntax;

Exp format(Exp e, str format) 
    = invokeStatic(reference("java.lang.String"), methodDesc(string(), "format", [string(), array(object())]),
           [const(string(), format), newArray(object(), [e])]);