package lang.flybytes.demo.protol;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Inspired mainly by the JSR-292 cookbook by @headius, this class provides the 
 * base class for all prototype objects "Prototype". It also contains the bootstrap methods for
 * invokeDynamic instructions. This bootstrap method binds all method invocations to an indirect 
 * dynamic lookup method called "finder" which traverses the prototype hierarchy to locate the 
 * method which was invoked, and finally resorts to calling the "missing" method if all else fails.
 * 
 * There is caching to make sure repeated calls to the same prototype will not result in repeated
 * method lookups, but otherwise (for example in a loop over an object array), each invocation will
 * lead to a dynamically computed binding. 
 */
public class Prototype {
	  public static final Prototype PROTO = new Prototype();
	  private static final Lookup lookup = MethodHandles.lookup();
	  
	  /**
	   * A call site in Protol is dynamic, so it knows what it's receiver is to make
	   * sure we do not reuse an older receiver.
	   */
	  private static class ProtoCallSite extends MutableCallSite {
		  final String methodName;
		  Prototype receiver;
		  
		  ProtoCallSite(String methodName, MethodType type) {
			  super(type);
			  this.methodName = methodName;
			  this.receiver = null;
		  }
		  
		  public void setObject(Prototype object) {
			this.receiver = object;
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
		  
		  @Override
		  public boolean equals(Object obj) {
			  if  (obj instanceof Int) {
				  return ((Int) obj).integer == integer;
			  }
			  
			  return false;
		  }
	  }
	  
	  /** basic wrapper for builtin strings; everything in Protol _is_ an object prototype */
	  public static class Str extends Prototype {
		  public String string;
		  
		  public Str(String s) {
			  this.prototype = PROTO;
			  this.string = s;
		  }
		  
		 
		  
		  @Override
		  public String toString() {
			  return string;
		  }

		  @Override
		  public boolean equals(Object obj) {
			  if (obj instanceof Str) {
				  return ((Str) obj).string.equals(string);
			  }
			  return false;
		  }
	  }
	  
	  /** basic wrapper for builtin arrays; everything in Protol _is_ an object prototype */
	  public static class Arr extends Prototype {
		  public Prototype[] array;
		  
		  public Arr(Prototype[] array) {
			  this.prototype = PROTO;
			  this.array = array;
		  }
		  
		  public Prototype[] $get_array() {
			  return array;
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
		  
		  @Override
		  public boolean equals(Object obj) {
			  if (!(obj instanceof Arr)) {
				  return false;
			  }
			  
			  Arr other = (Arr) obj;
			  
			  if (array.length != other.array.length) {
				  return false;
			  }
			  
			  for (int i = 0; i < array.length; i++) {
				  if (!array[i].equals(other.array[i])) {
					  return false;
				  }
			  }
			  
			  return true;
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
		  this.prototype = prototype; // (Prototype) prototype.clone();
	  }
	  
	  public Prototype concat(Prototype p) {
		  return new Str(toString().concat(p.toString()));
	  }
	  
	  public Prototype missing(Prototype name, Prototype args) {
		  System.err.println("missed " + name + "!");
		  return name;
	  }
	  
	  @Override
	  public String toString() {
		  try {
			  StringBuilder sb = new StringBuilder();
			  sb.append("{");

			  Map<String,Prototype> fields = new HashMap<>();
			  collectFields(fields);

			  if (!fields.isEmpty()) {
				  sb.append("\n");
			  }
			  
			  for (String key : fields.keySet()) {
				  sb.append("  ");
				  sb.append(key);
				  sb.append(" = ");
				  sb.append(fields.get(key).toString());
				  sb.append("\n");
			  }
			 
			  sb.append("}");
			  if (!fields.isEmpty()) {
				  sb.append("\n");
			  }
			  
			  return sb.toString();
		  } catch (IllegalArgumentException | IllegalAccessException e) {
			  return "{}";
		  } 
	  }
	  
	  private int countFields() {
		  return getClass().getFields().length + (prototype != null ? prototype.countFields() : 0);
	  }
	  
	  private void collectFields(Map<String, Prototype> fields) throws IllegalArgumentException, IllegalAccessException {
		  for (Field f : getClass().getFields()) {
			  if (!f.getName().equals("prototype") && !f.getName().equals("PROTO")) {
				  if (!fields.containsKey(f.getName())) {
					  Object object = f.get(this);
					  if (object instanceof Prototype) {
						  fields.put(f.getName(), (Prototype) object);
					  }
					  else if (object instanceof Integer) {
						  fields.put(f.getName(), new Int((int) object));
					  }
					  else if (object instanceof String) {
						  fields.put(f.getName(), new Str((String) object));
					  }
					  else if (object instanceof Prototype[]) {
						  fields.put(f.getName(), new Arr((Prototype[]) object));
					  }
				  }
			  }
		  }

		  if (prototype != null) {
			  prototype.collectFields(fields);
		  }
	  }
	  
	  @Override
	  public boolean equals(Object obj) {
		  if (!(obj instanceof Prototype)) {
			  return false;
		  }
		  
		  if (countFields() != ((Prototype) obj).countFields()) {
			  return false;
		  }
		  
		  try {
			  Map<String, Prototype> fields = new HashMap<>();
			  collectFields(fields);
			  Map<String, Prototype>  otherFields = new HashMap<>();
			  ((Prototype) obj).collectFields(otherFields);
		
			  for (String key : fields.keySet()) {
				  if (!otherFields.containsKey(key)) {
					  return false;
				  }
				  else if (!fields.get(key).equals(otherFields.get(key))) {
					  return false;
				  }
			  }
			  
		      return true;
		  } catch (IllegalArgumentException | IllegalAccessException e) {
			  return false;
		  }
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
		Prototype original = receiver;
	    MethodType type = callSite.type();
	    MethodHandle target;
	    int counter = 0;
	    
	    while (receiver != null) {
	    	Class<?> receiverClass = receiver.getClass();
	    	args[0]  = receiver;
	    	
	    	try {
	    		// happy path: we just call a method in the receiver class
	    		target = lookup.findVirtual(receiverClass, callSite.methodName, type.dropParameterTypes(0, 1));
	    		
	    		// match the target method with the new method type
	    		target = target.asType(type);
	    		
	    		MethodHandle findMethod = FINDER.bindTo(callSite);
	    		findMethod = findMethod.asCollector(Prototype[].class, type.parameterCount());
	    		findMethod = findMethod.asType(type);
	    		
	    		MethodHandle testMethod = TESTER.bindTo(callSite);
	    		testMethod = testMethod.asCollector(Prototype[].class, type.parameterCount());
	   		  
	    		// if the cache fails, try to find the method again, recursively:
	    		MethodHandle cond = MethodHandles.guardWithTest(
	    				testMethod, 
	    				target, 
	    				findMethod
	    				);
	  		  
	    		// cache the target
	    		callSite.setTarget(cond);
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
	    receiver = original;

	    while (receiver != null) {
	    	Class<?> receiverClass = receiver.getClass();
	    	args[0] = receiver;
	    	
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
		  return site.receiver == (Prototype) args[0];
	  }
}
