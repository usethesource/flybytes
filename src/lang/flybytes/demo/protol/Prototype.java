package lang.flybytes.demo.protol;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.Arrays;

/**
 * Inspired mainly by the JSR-292 cookbook by @headius
 */
public class Prototype {
	  private static final Lookup lookup = MethodHandles.lookup();
	  private static class ProtoCallSite extends MutableCallSite {
	    final String name;

	    ProtoCallSite(String name, MethodType type) {
	      super(type);
	      this.name = name;
	    }
	  }
	  
	  public final Prototype[] prototypes;
	  
	  public Prototype() {
		  this.prototypes = new Prototype[0];
	  }
	  
	  public Prototype(Prototype[] prototypes) {
		  this.prototypes = Arrays.copyOf(prototypes, prototypes.length);
	  }
	  
	  public Prototype method_missing(ProtoCallSite name, Object[] args) {
		  System.err.println("missed " + name.name + "!");
		  return new Prototype();
	  }
	  
	  
	  public static CallSite bootstrap(Lookup lookup, String name, MethodType type) {
		  ProtoCallSite callSite = new ProtoCallSite(name, type);

		  MethodHandle fallback = TRY.bindTo(callSite);
		  fallback = fallback.asCollector(Object[].class, type.parameterCount());
		  fallback = fallback.asType(type);

		  callSite.setTarget(fallback);
		  return callSite;
	  }
	  
	  public static Object tryMethod(ProtoCallSite callSite, Object[] args) throws Throwable {
		Prototype receiver = (Prototype) args[0];
		Class<?> receiverClass = receiver.getClass();
	    MethodType type = callSite.type();
	    MethodHandle target;
	    
	    try {
	    	// happy path: we just call a method in the receiver class
			target = lookup.findVirtual(receiverClass, callSite.name, type.dropParameterTypes(0, 1));
	    	target = target.asType(type);
	    	callSite.setTarget(target);
	    	return target.invokeWithArguments(args);
	    }
	    catch (NoSuchMethodException e) {
	    	// common enough, we don't actually find the method in the receiver!

	    	// but we can always find the method_missing method, since every object extends Prototype
	    	target = lookup.findVirtual(receiverClass, "method_missing", type);
	    	callSite.setTarget(target);
	    	return target.invoke(callSite, args);
	    }
	  }
	  
	  private static final MethodHandle TRY;
	  static {
	    try {
	      TRY = lookup.findStatic(Prototype.class, "tryMethod",
	          MethodType.methodType(Object.class, ProtoCallSite.class, Object[].class));
	    } catch (ReflectiveOperationException e) {
	      throw (AssertionError) new AssertionError().initCause(e);
	    }
	  }
}
