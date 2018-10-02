module lang::mujava::api::Vallang

import lang::mujava::Syntax;
import lang::mujava::Mirror;

@doc{generate call ValueFactoryFactory.getInstance()}
Expression getValueFactory() = invokeStatic("org.rascalmpl.values.ValueFactoryFactory", methodDesc(classType("org.rascalmpl.values.IValueFactory"), "getValueFactory", []), []);

Expression getRascalFactory() = invokeStatic("org.rascalmpl.values.uptr.IRascalValueFactory", methodDesc(classType("org.rascalmpl.values.uptr.IRascalValueFactory"), "getInstance", []), []);

@doc{generate call vf.integer(i), etc.}
Expression vfInteger(Expression vf, Expression i) = invokeInterface(IValueFactory, vf, methodDesc(IInteger, "integer", [integer()]), [i]);
Expression vfReal(Expression vf, Expression d) = invokeInterface(IValueFactory, vf, methodDesc(IInteger, "real", [double()]), [d]); 

Type IValueFactory = classType("io.usethesource.vallang.IValueFactory");
Type IValue = classType("io.usethesource.vallang.IValue");
Type IInteger = classType("io.usethesource.vallang.IInteger");
Type IReal = classType("io.usethesource.vallang.IReal");
Type IRational = classType("io.usethesource.vallang.IRational");
Type INumber = classType("io.usethesource.vallang.INumber");
Type IConstructor = classType("io.usethesource.vallang.IConstructor");
Type INode = classType("io.usethesource.vallang.INode");
Type ISet = classType("io.usethesource.vallang.ISet");
Type IList = classType("io.usethesource.vallang.IList");
Type IMap = classType("io.usethesource.vallang.IMap");

Expression INumber_Add(Expression lhs, Expression rhs) = invokeInterface(lhs, methodDesc(INumber, "add", [INumber]), [rhs]); 