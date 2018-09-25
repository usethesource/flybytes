package lang.mujava.internal;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.interpreter.TypeReifier;
import org.rascalmpl.interpreter.control_exceptions.MatchFailed;
import org.rascalmpl.interpreter.control_exceptions.Throw;
import org.rascalmpl.interpreter.env.Environment;
import org.rascalmpl.interpreter.result.AbstractFunction;
import org.rascalmpl.interpreter.result.ICallableValue;
import org.rascalmpl.interpreter.result.Result;
import org.rascalmpl.interpreter.result.ResultFactory;
import org.rascalmpl.interpreter.types.FunctionType;
import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.values.uptr.RascalValueFactory;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.type.ITypeVisitor;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeStore;
import lang.mujava.internal.ClassCompiler.AST;
import lang.mujava.internal.ClassCompiler.Signature;
import lang.mujava.internal.ClassCompiler.Switch;

/**
 * Representations of java objects and java classes as rascal values
 * with callbacks into the reflection API. For testing purposes.
 */
public class Mirror {
	private final IValueFactory vf;
	private final TypeReifier tr;
	private final Type Mirror;
	private final Type classCons;
	private final FunctionType invokeStaticFunc;
	private final FunctionType getStaticFunc;
	private final FunctionType newInstanceFunc;
	private final Type objectCons;
	private final FunctionType invokeFunc;
	private final FunctionType getFieldFunc;
	private final FunctionType toValueFunc;
	private final Type arrayCons;
	private final FunctionType lengthFunc;
	private final FunctionType loadFunc;
	private final Type nullCons;
	private IEvaluatorContext ctx;

	public Mirror(IValueFactory vf, TypeStore store, IEvaluatorContext ctx) {
		this.vf = vf;
		this.tr = new TypeReifier(vf);
		this.ctx = ctx;
		this.Mirror = store.lookupAbstractDataType("Mirror");
		
		this.classCons = store.lookupConstructor(Mirror, "class").iterator().next();
		this.invokeStaticFunc = (FunctionType) classCons.getFieldType(1);
		this.getStaticFunc = (FunctionType) classCons.getFieldType(2);
		this.newInstanceFunc = (FunctionType) classCons.getFieldType(3);
		
		this.objectCons = store.lookupConstructor(Mirror, "object").iterator().next();
		this.invokeFunc = (FunctionType) objectCons.getFieldType(1);
		this.getFieldFunc = (FunctionType) objectCons.getFieldType(2);
		this.toValueFunc = (FunctionType) objectCons.getFieldType(3);
		
		this.arrayCons = store.lookupConstructor(Mirror, "array").iterator().next();
		this.lengthFunc = (FunctionType) arrayCons.getFieldType(0);
		this.loadFunc = (FunctionType) arrayCons.getFieldType(1);
		
		this.nullCons = store.lookupConstructor(Mirror, "null").iterator().next();
	}

	public IConstructor mirrorClass(String className, Class<?> cls) {
		return vf.constructor(classCons, 
				vf.string(className),
				invokeStatic(className, cls),
				getStatic(className, cls),
				newInstance(className, cls)
				);
	}
	
	public IConstructor mirrorObject(Object object) {
		if (object == null) {
			return vf.constructor(nullCons);
		}
		
		Class<?> cls = object.getClass();
		IConstructor classMirror = mirrorClass(cls.getName(), cls);
		return mirrorObject(classMirror, object);
	}
	
	private IConstructor mirrorObject(IConstructor classMirror, Object object) {
		if (object.getClass().isArray()) {
			return vf.constructor(arrayCons,
					length(object),
					load(object));
		}
		else {
			return vf.constructor(objectCons,
					classMirror,
					invoke(object),
					field(object),
					toValue(object)
					);
		}
	}

	private IValue load(Object object) {
		return new MirrorCallBack<Object>(object, loadFunc, ctx) {
			@Override
			public Type getType() {
				return loadFunc;
			}

			@Override
			Result<IValue> call(Type[] formals, IValue[] actuals) {
				int index = ((IInteger) actuals[0]).intValue();
				return ResultFactory.makeResult(Mirror, mirrorObject(Array.get(getWrapped(), index)), ctx);
			}
		};
	}

	private IValue length(Object object) {
		return new MirrorCallBack<Object>(object, lengthFunc, ctx) {
			@Override
			public Type getType() {
				return lengthFunc;
			}

			@Override
			Result<IValue> call(Type[] formals, IValue[] actuals) {
				IInteger result = vf.integer(Array.getLength(getWrapped()));
				return ResultFactory.makeResult(result.getType(), result, ctx);
			}
		};
	}

	private IValue toValue(Object object) {
		return new MirrorCallBack<Object>(object, toValueFunc, ctx) {
			
			@Override
			public Type getType() {
				return toValueFunc;
			}
			
			@Override
			Result<IValue> call(Type[] formals, IValue[] actuals) {
				Type expected = tr.valueToType((IConstructor) actuals[0]);
				Object wrapped = getWrapped();
				IValue result = null;

				
				if (wrapped instanceof IValue) {
					result = (IValue) wrapped;
				}
				else {
					result = asValue(expected, wrapped);
				}

				if (result.getType().comparable(expected)) {
					return ResultFactory.makeResult(expected, result, ctx);
				}
				else {
					throw RuntimeExceptionFactory.illegalTypeArgument(expected.toString(), null, null);
				}
			}
		};
	}

	private IValue asValue(Type expected, Object wrapped) {
		return expected.accept(new ITypeVisitor<IValue, RuntimeException>() {

			@Override
			public IValue visitAbstractData(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			private RuntimeException illegalType(Type expected) {
				return RuntimeExceptionFactory.illegalTypeArgument(wrapped.getClass().toString() + " can not convert to " + expected.toString(), null, null);
			}

			@Override
			public IValue visitAlias(Type arg0) throws RuntimeException {
				return arg0.getAliased().accept(this);
			}

			@Override
			public IValue visitBool(Type arg0) throws RuntimeException {
				if (wrapped instanceof Boolean) {
					return vf.bool(((Boolean) wrapped).booleanValue());
				}
				else {
					throw illegalType(arg0);
				}
			}

			@Override
			public IValue visitConstructor(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitDateTime(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitExternal(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitInteger(Type arg0) throws RuntimeException {
				if (wrapped instanceof Integer) {
					return vf.integer(((Integer) wrapped).intValue());
				}
				else if (wrapped instanceof Byte) {
					return vf.integer(((Byte) wrapped).intValue());
				}
				else if (wrapped instanceof Short) {
					return vf.integer(((Short) wrapped).intValue());
				}
				else if (wrapped instanceof Character) {
					return vf.integer(((Character) wrapped).charValue());
				}
				else if (wrapped instanceof Long) {
					return vf.integer(((Long) wrapped).longValue());
				}
				else {
					throw illegalType(expected);
				}
			}

			@Override
			public IValue visitList(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitMap(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitNode(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitNumber(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitParameter(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitRational(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitReal(Type arg0) throws RuntimeException {
				if (wrapped instanceof Double) {
					return vf.real(((Double) wrapped).doubleValue());
				}
				else if (wrapped instanceof Float) {
					return vf.real(Float.toString((Float) wrapped));
				}
				else {
					throw illegalType(expected);
				}
			}

			@Override
			public IValue visitSet(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitSourceLocation(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitString(Type arg0) throws RuntimeException {
				if (wrapped instanceof String) {
					return vf.string(((String) wrapped));
				}
				else {
					return vf.string(wrapped.toString());
				}
			}

			@Override
			public IValue visitTuple(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}

			@Override
			public IValue visitValue(Type arg0) throws RuntimeException {
				return visitString(arg0);
			}

			@Override
			public IValue visitVoid(Type arg0) throws RuntimeException {
				throw illegalType(expected);
			}
		});
	}
	
	private IValue newInstance(String className, Class<?> cls) {
		return new MirrorCallBack<Class<?>>(cls, newInstanceFunc, ctx) {
			@Override
			public int getArity() {
				return 2;
			} 

			@Override
			Result<IValue> call(Type[] formals, IValue[] actuals) {
				try {
					IConstructor signature = (IConstructor) actuals[0];
					IList args = (IList) actuals[1];
					
					Constructor<?> meth = getDeclaredConstructor(getWrapped(), signature);
					Object object = meth.newInstance(unreflect(args));
					return ResultFactory.makeResult(Mirror, mirrorObject(object), ctx);
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | SecurityException | NoSuchMethodException | ClassNotFoundException | InstantiationException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
	
	private IValue getStatic(String className, Class<?> cls) {
		return new MirrorCallBack<Class<?>>(cls, getStaticFunc, ctx) {
			@Override
			public int getArity() {
				return 1;
			} 

			@Override
			Result<IValue> call(Type[] formals, IValue[] actuals) {
				try {
					String name = ((IString) actuals[0]).getValue();
					Field field = getWrapped().getField(name);
					Object result = field.get(null); 
					return ResultFactory.makeResult(Mirror, mirrorObject(result), ctx);
				} catch (IllegalAccessException | IllegalArgumentException
					 | SecurityException | NoSuchFieldException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	private IValue field(Object object) {
		return new MirrorCallBack<Object>(object, getFieldFunc, ctx) {
			@Override
			public int getArity() {
				return 1;
			} 

			@Override
			Result<IValue> call(Type[] formals, IValue[] actuals) {
				try {
					String name = ((IString) actuals[0]).getValue();
					Field field = getWrapped().getClass().getDeclaredField(name);
					field.setAccessible(true);
					Object result = field.get(getWrapped());
					return ResultFactory.makeResult(Mirror, mirrorObject(result), ctx);
				} catch (IllegalAccessException | IllegalArgumentException
					 | SecurityException | NoSuchFieldException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	private IValue invoke(Object object) {
		return new MirrorCallBack<Object>(object, invokeFunc, ctx) {
			@Override
			public int getArity() {
				return 2;
			} 

			@Override
			Result<IValue> call(Type[] formals, IValue[] actuals) {
				try {
					IConstructor signature = (IConstructor) actuals[0];
					IList args = (IList) actuals[1];
					Class<?> objectClass = getWrapped().getClass();
					Method meth = getMethod(objectClass, signature);
					meth.setAccessible(true);
					Object object = meth.invoke(getWrapped(), unreflect(args));
					return ResultFactory.makeResult(Mirror, mirrorObject(object), ctx);
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | SecurityException | NoSuchMethodException | ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
	
	private IValue invokeStatic(String className, Class<?> cls) {
		return new MirrorCallBack<Class<?>>(cls, invokeStaticFunc, ctx) {
			@Override
			public int getArity() {
				return 2;
			} 

			@Override
			Result<IValue> call(Type[] formals, IValue[] actuals) {
				try {
					IConstructor signature = (IConstructor) actuals[0];
					IList args = (IList) actuals[1];
					Method meth = getMethod(getWrapped(), signature);
					meth.setAccessible(true);
					Object object = meth.invoke(null, unreflect(args));
					return ResultFactory.makeResult(Mirror, mirrorObject(object), ctx);
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | SecurityException | NoSuchMethodException | ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
	
	protected Object[] unreflect(IList args) {
		Object[] result = new Object[args.length()];
		int i = 0;
		for (IValue elem : args) {
			result[i++] = unreflect((IConstructor) elem);
		}
		
		return result;
	}

	private Method getMethod(Class<?> cls, IConstructor sig) throws NoSuchMethodException, SecurityException, ClassNotFoundException {
		return cls.getMethod(AST.$getName(sig), Signature.binaryClasses(AST.$getFormals(sig), ctx.getStdOut()));
	}
	
	private <T> Constructor<T> getDeclaredConstructor(Class<T> cls, IConstructor sig) throws NoSuchMethodException, SecurityException, ClassNotFoundException {
		return cls.getDeclaredConstructor(Signature.binaryClasses(AST.$getFormals(sig), ctx.getStdOut()));
	}

	private abstract static class MirrorCallBack<T> extends AbstractFunction {
		private final T wrapped;

		public MirrorCallBack(T wrapped, FunctionType type, IEvaluatorContext ctx) {
			super(null, ctx.getEvaluator(), type, Collections.emptyList(), false, ctx.getCurrentEnvt());
			this.wrapped = wrapped;
		}

		public T getWrapped() {
			return wrapped;
		}
		
		@Override
		public ICallableValue cloneInto(Environment env) {
			return null;
		}
		
		@Override
		public boolean isStatic() {
			return false;
		}
		
		@Override
		public boolean isDefault() {
			return false;
		}
		
		@Override
		public IConstructor encodeAsConstructor() {
			IValueFactory vf = eval.getValueFactory();
			return vf.constructor(RascalValueFactory.Function_Function, vf.sourceLocation("file:///unknown"));
		}
		
		@Override
		public Result<IValue> call(Type[] argTypes, IValue[] argValues, Map<String, IValue> keyArgValues)
				throws MatchFailed {
			try {
				if (argTypes.length != argValues.length) {
					throw new RuntimeException("function should have " + argTypes.length + " arguments");
				}
				
				for (int i = 0; i < argTypes.length; i++) {
					Type actualType = argValues[i].getType();
					Type formalType = getFunctionType().getFieldType(i);
					if (!actualType.isSubtypeOf(formalType)) {
						throw new RuntimeException("argument " + i + " is a " + actualType + " but should be a " + formalType);
					}
				}
				
				return call(argTypes, argValues);
			}
			catch (Throwable e) {
				while (e.getCause() != e && e.getCause() != null) {
					e = e.getCause();
				}

				if (e instanceof Throw) {
					throw (Throw) e;
				}
				else if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				}
				else {
					throw new RuntimeException(e);
				}
			}
		}
		
		@Override
		public Result<IValue> call(IRascalMonitor monitor, Type[] argTypes, IValue[] argValues,
				Map<String, IValue> keyArgValues) {
			return call(argTypes, argValues, null);
		}

		abstract Result<IValue> call(Type[] argTypes, IValue[] argValues);
	}

	public IValue mirrorArray(IConstructor type, int length) throws ClassNotFoundException {
		return mirrorObject(newArray(type, length));
	}

	private Object newArray(IConstructor type, int length) throws ClassNotFoundException {
		return Array.newInstance(Signature.binaryClass(type), length);
	}
	
	public IValue mirrorArray(IConstructor type, IList elems) throws ClassNotFoundException {
		Object newInstance = newArray(type, elems.length());
		
		for (int i = 0; i < elems.length(); i++) {
			IConstructor mirror = (IConstructor) elems.get(i);
			Object object = unreflect(mirror);
			try {
				Switch.type(type, i,
						(z,ind) -> Array.setBoolean(newInstance, ind, (boolean) object), 
						(I,ind) -> Array.setInt(newInstance, ind, (int) object),
						(s,ind) -> Array.setShort(newInstance, ind, (short) object), 
						(b,ind) -> Array.setByte(newInstance, ind, (byte) object), 
						(c,ind) -> Array.setChar(newInstance, ind, (char) object), 
						(f,ind) -> Array.setFloat(newInstance, ind, (float) object), 
						(d,ind) -> Array.setDouble(newInstance, ind, (double) object), 
						(l,ind) -> Array.setLong(newInstance, ind, (long) object),
						(v,ind) -> Array.set(newInstance, ind, null), 
						(c,ind) -> Array.set(newInstance, ind, object), 
						(a,ind) -> Array.set(newInstance, ind, object), 
						(S,ind) -> Array.set(newInstance, ind, object)
						);
			}
			catch (IllegalArgumentException e) {
				if (object != null) {
					throw new IllegalArgumentException("element type mismatch: " + newInstance.getClass().getComponentType() + " vs " + object.getClass());
				}
			}
		}
		
		return mirrorObject(newInstance);
	}

	private Object unreflect(IConstructor mirror) {
		switch (mirror.getConstructorType().getName()) {
		case "null" : 
			return null; 
		case "class" : 
			return ((MirrorCallBack<?>) mirror.get("invokeStatic")).getWrapped();
		case "object": 
			return ((MirrorCallBack<?>) mirror.get("invoke")).getWrapped();
		case "array" :
			Object wrapped = ((MirrorCallBack<?>) mirror.get("load")).getWrapped();
			return wrapped;
		default:
			throw new IllegalArgumentException(mirror.toString());
		}
	}
	
	
}
