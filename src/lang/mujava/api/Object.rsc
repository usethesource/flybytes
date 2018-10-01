module lang::mujava::api::Object

import lang::mujava::Syntax;


// call toString() on an object      
Expression toString(Expression obj) 
   = invokeVirtual(object(), obj, methodDesc(string(), "toString", []), []);

// call hashCode() on an object
Expression hashCode(Expression obj) 
   = invokeVirtual(object(), obj, methodDesc(integer(), "hashCode", []), []);

// call equals(Object a) on an object   
Expression equals(Expression obj, Expression compared) 
   = invokeVirtual(object(), obj, methodDesc(boolean(), "equals", [object()]), [compared]);