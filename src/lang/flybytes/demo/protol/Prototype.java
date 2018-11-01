package lang.flybytes.demo.protol;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

/**
 * Inspired mainly by the JSR-292 cookbook by @headius, this class provides the 
 * base class for all prototype objects "Prototype". It also contains the bootstrap methods for
 * invokeDynamic instructions. This bootstrap method binds all method invocations to an indirect 
 * dynamic lookup method called "finder" which traverses the prototype hierarchy to locate the 
 * method which was invoked, and finally resorts to calling "method_missing" if all else fails.
 * 
 * There is caching to make sure repeated calls to the same prototype will not result in repeated
 * method lookups, but otherwise (for example in a loop over an object array), each invocation will
 * lead to a dynamically computed binding. 
 */
public class Prototype {
	  public static final Prototype PROTO = new Prototype();
	  private static final Lookup lookup = MethodHandles.lookup();
	  private static class ProtoCallSite extends MutableCallSite {
		  final String methodName;
		  Prototype object;
		  
		  ProtoCallSite(String methodName, MethodType type) {
			  super(type);
			  this.methodName = methodName;
			  this.object = null;
		  }
		  
		  public void setObject(Prototype object) {
			this.object = object;
		  }
	  }
	  
	  /** basic wrapper for builtin ints; everything in Protol _is_ an object prototype */
	  public static class Int extends Prototype {
		  public int integer;
		  
		  public Int(int i) {
			  this.prototype = PROTO;
			  this.integer = i;
		  }
		  
		  @Override
		  public String toString() {
			  return Integer.toString(integer);
		  }
		  
		  public int $get_integer() {
			  return integer;
		  }
	  }
	  
	  /** basic wrapper for builtin strings; everything in Protol _is_ an object prototype */
	  public static class Str extends Prototype {
		  public String string;
		  
		  public Str(String s) {
			  this.prototype = PROTO;
			  this.string = s;
		  }
		  
		  public Prototype concat(Prototype p) {
			  if (p instanceof Str) {
				  return new Str(string + ((Str) p).string);
			  }
			  else {
				  return new Str(string + p.toString());
			  }
		  }
		  
		  @Override
		  public String toString() {
			  return "\"" + string + "\"";
		  }
	  }
	  
	  /** basic wrapper for builtin arrays; everything in Protol _is_ an object prototype */
	  public static class Arr extends Prototype {
		  public Prototype[] array;
		  
		  public Arr(Prototype[] array) {
			  this.prototype = PROTO;
			  this.array = array;
		  }
		  
		  @Override
		  public String toString() {
			  StringBuilder b = new StringBuilder();
			  b.append("[\n");
			  for (Prototype p : array) {
				  b.append("  ");
				  b.append(p.toString());
				  b.append(",\n");
			  }
			  b.delete(b.length() - 2, b.length()); // remove last comma and newline
			  b.append("\n]\n");
			  return b.toString();
		  }
	  }
	  
	  private static final MethodHandle FINDER;
	  private static final MethodHandle TESTER;
	  static {
		  try {
			  FINDER = lookup.findStatic(Prototype.class, "finder", MethodType.methodType(Object.class, ProtoCallSite.class, Object[].class));
			  TESTER = lookup.findStatic(Prototype.class, "cacheTester", MethodType.methodType(boolean.class, ProtoCallSite.class, Object[].class));
		  } catch (ReflectiveOperationException e) {
			  throw (AssertionError) new AssertionError().initCause(e);
		  }
	  }
	  
	  /** the object this object inherited from at allocation time */
	  public Prototype prototype;
	  
	  public Prototype() {
		  this.prototype = null;
	  }
	  
	  @Override
	  protected Object clone() throws CloneNotSupportedException {
		  return new Prototype();
	  }
	  
	  public Prototype(Prototype prototype) throws CloneNotSupportedException {
		  // inheritance creates a shallow clone of the template prototype, so 
		  // the objects can continue to live and be updated independently
		  this.prototype = (Prototype) prototype.clone();
	  }
	  
	  public Prototype missing(Prototype name, Prototype args) {
		  System.err.println("missed " + name + "!");
		  return name;
	  }
	  
	  @Override
	  public String toString() {
		  return "{}";
	  }
	  
	  /**
	   * Bootstrap sets up _every_ method call via a dynamic finder method.
	   */
	  public static CallSite bootstrap(Lookup lookup, String name, MethodType type) {
		  ProtoCallSite callSite = new ProtoCallSite(name, type);

		  MethodHandle findMethod = FINDER.bindTo(callSite);
		  findMethod = findMethod.asCollector(Object[].class, type.parameterCount());
		  findMethod = findMethod.asType(type);
		  
		  callSite.setTarget(findMethod);
		  return callSite;
	  }
	  
	  /**
	   * Finder traverses the object prototyping hierarchy to locate the called method,
	   * at run-time, or otherwise calls method_missing if it can't find anything.
	   * The result is cached. 
	   */
	  public static Object finder(ProtoCallSite callSite, Object[] args) throws Throwable {
		Prototype receiver = (Prototype) args[0];
	    MethodType type = callSite.type();
	    MethodHandle target;
	    
	    while (receiver != null) {
	    	Class<?> receiverClass = receiver.getClass();
	    	
	    	try {
	    		// happy path: we just call a method in the receiver class
	    		target = lookup.findVirtual(receiverClass, callSite.methodName, type.dropParameterTypes(0, 1));
	    		target = target.asType(type);
	    		
	    		// TODO: let the cache fail!
	    		// if the cache fails, try to find the method again:
//	    		target = MethodHandles.guardWithTest(
//	    				TESTER, 
//	    				target, 
//	    				FINDER
//	    				);
	    		
	    		// cache the target
	    		callSite.setTarget(target);
	    		callSite.setObject(receiver);
	    		
	    		return target.invokeWithArguments(args);
	    	}
	    	catch (NoSuchMethodException e) {
	    		// common enough, we don't actually find the method in the receiver!
	    		// so we look in its prototype, and try again:
	    		receiver = receiver.prototype;
	    	}
	    }
	    
	    // try again with method_missing:
	    receiver = (Prototype) args[0];

	    while (receiver != null) {
	    	Class<?> receiverClass = receiver.getClass();
	    	
	    	try {
	    		MethodType missingType = MethodType.methodType(Prototype.class, Prototype.class, Prototype.class);
				target = lookup.findVirtual(receiverClass, "missing", missingType);
				target = target.bindTo(receiver);
				target = MethodHandles.insertArguments(target, 0, new Str(callSite.methodName));
	    		
	    		Prototype[] newArgs = new Prototype[args.length - 1];
	    		System.arraycopy(args, 1, newArgs, 0, args.length - 1);
	    		return target.invoke(new Arr(newArgs));
	    	}
	    	catch (NoSuchMethodException e) {
	    		receiver = receiver.prototype;
	    	}
	    }
	    
	    // this never happens because all Prototypes have a method_missing method
	    throw new NoSuchMethodException(callSite.methodName);
	  }
	  
	  static boolean cacheTester(ProtoCallSite site, Object[] args) {
		  // the first argument is the receiver of a method call, we test if its still the same
		  // object as when we resolved the dynamic call, otherwise we should
		  // re-bind the method namely to another object:
		  return site.object == (Prototype) args[0];
	  }
}
