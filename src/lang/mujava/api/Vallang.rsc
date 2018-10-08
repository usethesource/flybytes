module lang::mujava::api::Vallang

import lang::mujava::Syntax;
import lang::mujava::Mirror;

@doc{generate call ValueFactoryFactory.getInstance()}
Exp getValueFactory() = invokeStatic(ValueFactoryFactory, methodDesc(IValueFactory, "getValueFactory", []), []);

Exp getRascalFactory() = invokeStatic(IRascalValueFactory, methodDesc(IRascalValueFactory, "getInstance", []), []);

@doc{generate call vf.integer(i), etc.}
Exp vfInteger(Exp vf, Exp i) = invokeInterface(IValueFactory, vf, methodDesc(IInteger, "integer", [integer()]), [i]);
Exp vfInteger(Exp vf, int i) = vfInteger(vf, const(integer(), i));
Exp vfReal(Exp vf, Exp d) = invokeInterface(IValueFactory, vf, methodDesc(IInteger, "real", [double()]), [d]); 

public Type ValueFactoryFactory = reference("org.rascalmpl.values.ValueFactoryFactory");
public Type IRascalValueFactory = reference("org.rascalmpl.values.uptr.IRascalValueFactory");
public Type IValueFactory = reference("io.usethesource.vallang.IValueFactory");
public Type IValue = reference("io.usethesource.vallang.IValue");
public Type IInteger = reference("io.usethesource.vallang.IInteger");
public Type IReal = reference("io.usethesource.vallang.IReal");
public Type IRational = reference("io.usethesource.vallang.IRational");
public Type INumber = reference("io.usethesource.vallang.INumber");
public Type IConstructor = reference("io.usethesource.vallang.IConstructor");
public Type INode = reference("io.usethesource.vallang.INode");
public Type ISet = reference("io.usethesource.vallang.ISet");
public Type IList = reference("io.usethesource.vallang.IList");
public Type IMap = reference("io.usethesource.vallang.IMap");

Exp INumber_Add(Exp lhs, Exp rhs) = invokeInterface(INumber, lhs, methodDesc(INumber, "add", [INumber]), [rhs]); 