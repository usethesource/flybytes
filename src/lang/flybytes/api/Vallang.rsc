module lang::flybytes::api::Vallang

import lang::flybytes::Syntax;
import lang::flybytes::Mirror;

@doc{generate call ValueFactoryFactory.getInstance()}
Exp getValueFactory() = invokeStatic(ValueFactoryFactory, methodDesc(IValueFactory, "getValueFactory", []), []);

Exp getRascalFactory() = invokeStatic(IRascalValueFactory, methodDesc(IRascalValueFactory, "getInstance", []), []);

@doc{generate call vf.integer(i), etc.}
Exp vfInteger(Exp vf, Exp i) = invokeInterface(IValueFactory, vf, methodDesc(IInteger, "integer", [integer()]), [i]);
Exp vfInteger(Exp vf, int i) = vfInteger(vf, const(integer(), i));
Exp vfReal(Exp vf, Exp d) = invokeInterface(IValueFactory, vf, methodDesc(IInteger, "real", [double()]), [d]); 

public Type ValueFactoryFactory = object("org.rascalmpl.values.ValueFactoryFactory");
public Type IRascalValueFactory = object("org.rascalmpl.values.uptr.IRascalValueFactory");
public Type IValueFactory = object("io.usethesource.vallang.IValueFactory");
public Type IValue = object("io.usethesource.vallang.IValue");
public Type IInteger = object("io.usethesource.vallang.IInteger");
public Type IReal = object("io.usethesource.vallang.IReal");
public Type IRational = object("io.usethesource.vallang.IRational");
public Type INumber = object("io.usethesource.vallang.INumber");
public Type IConstructor = object("io.usethesource.vallang.IConstructor");
public Type INode = object("io.usethesource.vallang.INode");
public Type ISet = object("io.usethesource.vallang.ISet");
public Type IList = object("io.usethesource.vallang.IList");
public Type IMap = object("io.usethesource.vallang.IMap");

Exp INumber_Add(Exp lhs, Exp rhs) = invokeInterface(INumber, lhs, methodDesc(INumber, "add", [INumber]), [rhs]); 
