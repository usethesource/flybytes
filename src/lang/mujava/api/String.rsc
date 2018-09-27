module lang::mujava::api::String

import lang::mujava::Syntax;

Expression format(Expression e, str format) 
    = invokeStatic("java.lang.String", methodDesc(string(), "format", [string(), array(object())]),
           [const(string(), format), newArray(object(), [e])]);