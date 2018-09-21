module lang::mujava::api::Object

import lang::mujava::Syntax;


// call toString() on an object      
Expression toString(Expression object) 
   = invokeVirtual(object, methodDesc(string(), "toString", []), []);

// call hashCode() on an object
Expression hashCode(Expression object) 
   = invokeVirtual(object, methodDesc(integer(), "hashCode", []), []);

// call equals(Object a) on an object   
Expression equals(Expression object, Expression compared) 
   = invokeVirtual(object, methodDesc(boolean(), "equals", [object()]), [compared]);