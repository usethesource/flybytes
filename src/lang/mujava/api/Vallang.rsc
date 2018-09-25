module lang::mujava::api::Vallang

import lang::mujava::Syntax;
import lang::mujava::Mirror;

// COMPILE-TIME API

@doc{generate call ValueFactoryFactory.getInstance()}
Expression getValueFactory() = invokeStatic("org.rascalmpl.values.ValueFactoryFactory", methodDesc(classType("org.rascalmpl.values.IValueFactory"), "getValueFactory", []), []);

@doc{generate call vf.integer(i)}
Expression vfInteger(Expression vf, Expression i) = invokeInterface("org.rascalmpl.values.IValueFactory", vf, methodDesc(classType("io.usethesource.vallang.IInteger"), "integer", [integer()]), [i]); 


