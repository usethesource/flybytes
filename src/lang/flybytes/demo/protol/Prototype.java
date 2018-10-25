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
	  private static final Lookup lookup = MethodHandles.lookup();
	  private static class ProtoCallSite extends MutableCallSite {
		  final String methodName;

		  ProtoCallSite(String methodName, MethodType type) {
			  super(type);
			  this.methodName = methodName;
		  }
	  }
	  
	  private static final MethodHandle FINDER;
	  static {
		  try {
			  FINDER = lookup.findStatic(Prototype.class, "finder", MethodType.methodType(Object.class, ProtoCallSite.class, Object[].class));
		  } catch (ReflectiveOperationException e) {
			  throw (AssertionError) new AssertionError().initCause(e);
		  }
	  }
	  
	  /** the object this object inherited from at allocation time */
	  public final Prototype prototype;
	  
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
	  
	  public Prototype method_missing(ProtoCallSite name, Object[] args) {
		  System.err.println("missed " + name.methodName + "!");
		  return new Prototype();
	  }
	  
	  public static CallSite bootstrap(Lookup lookup, String name, MethodType type) {
		  ProtoCallSite callSite = new ProtoCallSite(name, type);

		  MethodHandle findMethod = FINDER.bindTo(callSite);
		  findMethod = findMethod.asCollector(Object[].class, type.parameterCount());
		  findMethod = findMethod.asType(type);

		  callSite.setTarget(findMethod);
		  return callSite;
	  }
	  
	  public static Object finder(ProtoCallSite callSite, Object[] args) throws Throwable {
		Prototype receiver = (Prototype) args[0];
		Class<?> receiverClass = receiver.getClass();
	    MethodType type = callSite.type();
	    MethodHandle target;
	    
	    while (receiver != null) {
	    	try {
	    		// happy path: we just call a method in the receiver class
	    		target = lookup.findVirtual(receiverClass, callSite.methodName, type.dropParameterTypes(0, 1));
	    		target = target.asType(type);
	    		callSite.setTarget(target);
	    		return target.invokeWithArguments(args);
	    	}
	    	catch (NoSuchMethodException e) {
	    		// common enough, we don't actually find the method in the receiver!
	    		// so we look in its prototype, and try again:
	    		receiver = receiver.prototype;
	    		receiverClass = receiver.getClass();
	    	}
	    }
	    
	    // try again with method_missing:
	    receiver = (Prototype) args[0];
	    receiverClass = receiver.getClass();
	    
	    while (receiver != null) {
	    	try {
	    		target = lookup.findVirtual(receiverClass, "method_missing", type);
	    		callSite.setTarget(target);
	    		return target.invoke(callSite, args);
	    	}
	    	catch (NoSuchMethodException e) {
	    		receiver = receiver.prototype;
	    		receiverClass = receiver.getClass();
	    	}
	    }
	    
	    // this never happens because all Prototypes have a method_missing method
	    throw new NoSuchMethodException(callSite.methodName);
	  }
	  
	 
}
