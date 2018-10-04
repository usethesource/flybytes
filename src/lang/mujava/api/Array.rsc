module lang::mujava::api::Array

import lang::mujava::Syntax;

// index an array variable using a constant  
Expression index(str array, int index)
   = index(array, const(integer(), index));
   
// index an array variable using the result of an expression as index
Expression index(str array, Expression index)
   = aaload(load(array), index); 
   
Expression toString(Type elemType, Expression array)
   = invokeStatic(reference("java.util.Arrays"), methodDesc(string(), "toString", [array(elemType)]), [array]);