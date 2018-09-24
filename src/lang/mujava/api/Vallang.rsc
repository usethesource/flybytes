module lang::mujava::api::Vallang

import lang::mujava::Syntax;
import lang::mujava::Mirror;

// COMPILE-TIME API

@doc{generate call ValueFactoryFactory.getInstance()}
Expression getValueFactory() = invokeStatic("org.rascalmpl.values.ValueFactoryFactory", methodDesc(classType("org.rascalmpl.values.IValueFactory"), "getValueFactory", []), []);

@doc{generate call vf.integer(i)}
Expression vfInteger(Expression vf, Expression i) = invokeInterface("org.rascalmpl.values.IValueFactory", vf, methodDesc(classType("io.usethesource.vallang.IInteger"), "integer", [integer()]), [i]); 

// MIRROR API

@doc{call ValueFactoryFactory.getInstance()}
Mirror valueFactory() = classMirror("org.rascalmpl.values.ValueFactoryFactory").invokeStatic(methodDesc(classType("org.rascalmpl.values.IValueFactory"), "getValueFactory", []), []);

@doc{call vf.integer(i)}
int vfInteger(Mirror i) = valueFactory().invoke(methodDesc(classType("io.usethesource.vallang.IInteger"), "integer", [integer()]), [i]).toValue(#int);
 
@doc{call vf.string(s)} 
str vfString(Mirror s) = valueFactory().invoke(methodDesc(classType("io.usethesource.vallang.IString"), "string", [string()]), [s]).toValue(#str);
