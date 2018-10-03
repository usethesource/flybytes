module lang::mujava::api::Vallang

import lang::mujava::Syntax;
import lang::mujava::Mirror;

@doc{generate call ValueFactoryFactory.getInstance()}
Expression getValueFactory() = invokeStatic(ValueFactoryFactory, methodDesc(IValueFactory, "getValueFactory", []), []);

Expression getRascalFactory() = invokeStatic(IRascalValueFactory, methodDesc(IRascalValueFactory, "getInstance", []), []);

@doc{generate call vf.integer(i), etc.}
Expression vfInteger(Expression vf, Expression i) = invokeInterface(IValueFactory, vf, methodDesc(IInteger, "integer", [integer()]), [i]);
Expression vfInteger(Expression vf, int i) = vfInteger(vf, const(integer(), i));
Expression vfReal(Expression vf, Expression d) = invokeInterface(IValueFactory, vf, methodDesc(IInteger, "real", [double()]), [d]); 

public Type ValueFactoryFactory = class("org.rascalmpl.values.ValueFactoryFactory");
public Type IRascalValueFactory = class("org.rascalmpl.values.uptr.IRascalValueFactory");
public Type IValueFactory = class("io.usethesource.vallang.IValueFactory");
public Type IValue = class("io.usethesource.vallang.IValue");
public Type IInteger = class("io.usethesource.vallang.IInteger");
public Type IReal = class("io.usethesource.vallang.IReal");
public Type IRational = class("io.usethesource.vallang.IRational");
public Type INumber = class("io.usethesource.vallang.INumber");
public Type IConstructor = class("io.usethesource.vallang.IConstructor");
public Type INode = class("io.usethesource.vallang.INode");
public Type ISet = class("io.usethesource.vallang.ISet");
public Type IList = class("io.usethesource.vallang.IList");
public Type IMap = class("io.usethesource.vallang.IMap");

Expression INumber_Add(Expression lhs, Expression rhs) = invokeInterface(INumber, lhs, methodDesc(INumber, "add", [INumber]), [rhs]); 