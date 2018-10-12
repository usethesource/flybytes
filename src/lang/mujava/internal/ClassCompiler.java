package lang.mujava.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.objectweb.asm.ClassVisitor;
import org.rascalmpl.objectweb.asm.ClassWriter;
import org.rascalmpl.objectweb.asm.Label;
import org.rascalmpl.objectweb.asm.Opcodes;
import org.rascalmpl.objectweb.asm.tree.ClassNode;
import org.rascalmpl.objectweb.asm.tree.FieldNode;
import org.rascalmpl.objectweb.asm.tree.MethodNode;
import org.rascalmpl.objectweb.asm.util.CheckClassAdapter;
import org.rascalmpl.objectweb.asm.util.TraceClassVisitor;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.ValueFactoryFactory;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IMap;
import io.usethesource.vallang.IMapWriter;
import io.usethesource.vallang.INumber;
import io.usethesource.vallang.IReal;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

/**
 * Translates muJava ASTs (see lang::mujava::Syntax.rsc) directly down to JVM bytecode,
 * using the ASM library.
 */
public class ClassCompiler {
	private PrintWriter out;
	private final IValueFactory vf;

	public ClassCompiler(IValueFactory vf) {
		this.vf = vf;
	}

	public void compileClass(IConstructor cls, ISourceLocation classFile, IBool enableAsserts, IConstructor version, IBool debugMode, IEvaluatorContext ctx) {
		this.out = ctx.getStdOut();

		try (OutputStream output = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
			ClassVisitor cv = cw;
			if (debugMode.getValue()) {
				cv = new CheckClassAdapter(new TraceClassVisitor(cw, out));
			}
			new Compile(cv, AST.$getVersionCode(version), out, debugMode.getValue()).compileClass(cls);

			output.write(cw.toByteArray());
		} catch (Throwable e) {
			// TODO better error handling
			e.printStackTrace(out);
		}
	}

	public IMap loadClasses(IList classes, IConstructor prefix, IList classpath, IBool enableAsserts, IConstructor version, IBool debugMode, IEvaluatorContext ctx) {
		ClassMapLoader l = new ClassMapLoader(getClass().getClassLoader());
		
		ISourceLocation classFolder = null;
		
		if (prefix.getConstructorType().getName().equals("just")) {
			classFolder = (ISourceLocation) prefix.get("val");
		}
		
		for (IValue elem : classes) {
			IConstructor cls = (IConstructor) elem;
			String name = AST.$getName(AST.$getType(cls));

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			ClassVisitor cv = cw;
			if (debugMode.getValue()) {
				cv = new CheckClassAdapter(new TraceClassVisitor(cw, out));
			}
			
			new Compile(cv, AST.$getVersionCode(version), out, debugMode.getValue()).compileClass(cls);
			byte[] bytes = cw.toByteArray();
			
			l.putBytes(name, cw.toByteArray());
			
		    if (classFolder != null) {
		    	ISourceLocation classFile = URIUtil.getChildLocation(classFolder, name.replace('.','/') + ".class");
				try (OutputStream out = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
					out.write(bytes);
				}
				catch (IOException e) {
					RuntimeExceptionFactory.io(vf.string(e.getMessage()), null, null);
				}
			}
		}
		
		try {
			Mirror m = new Mirror(vf, ctx.getCurrentEnvt().getStore(), ctx);
			IMapWriter w = vf.mapWriter();

			for (String name : l) {
				w.put(vf.string(name), m.mirrorClass(name, l.getClass(name)));
			}

			return w.done();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	public IValue loadClass(IConstructor cls, IConstructor output, IList classpath, IBool enableAsserts, IConstructor version, IBool debugMode, IEvaluatorContext ctx) {
		this.out = ctx.getStdOut();

		try {
			String className = AST.$getName(AST.$getType(cls));
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			ClassVisitor cv = cw;
			
			if (debugMode.getValue()) {
				cv = new TraceClassVisitor(new CheckClassAdapter(cw), out);
			}
			new Compile(cv, AST.$getVersionCode(version), out, debugMode.getValue()).compileClass(cls);

			Class<?> loaded = loadSingleClass(className, cw);

			Mirror m = new Mirror(vf, ctx.getCurrentEnvt().getStore(), ctx);

			if (output.getConstructorType().getName().equals("just")) {
				ISourceLocation classFile = (ISourceLocation) output.get("val");
				try (OutputStream out = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
					out.write(cw.toByteArray());
				}
				catch (IOException e) {
					RuntimeExceptionFactory.io(vf.string(e.getMessage()), null, null);
				}
			}

			return m.mirrorClass(className, loaded);
		} 
		catch (Throwable e) {
			e.printStackTrace(out);
			throw new RuntimeException(e);
		}
	}

	public IValue val(IValue v, IEvaluatorContext ctx) {
		Mirror m = new Mirror(vf, ctx.getCurrentEnvt().getStore(), ctx);
		return m.mirrorObject(v);
	}

	public IValue array(IConstructor type, IList elems, IEvaluatorContext ctx) throws ClassNotFoundException {
		Mirror m = new Mirror(vf, ctx.getCurrentEnvt().getStore(), ctx);
		return m.mirrorArray(type, elems);
	}

	public IValue array(IConstructor type, IInteger length, IEvaluatorContext ctx) throws ClassNotFoundException {
		Mirror m = new Mirror(vf, ctx.getCurrentEnvt().getStore(), ctx);
		return m.mirrorArray(type, length.intValue());
	}

	public IValue classMirror(IString n, IEvaluatorContext ctx) {
		try {
			Mirror m = new Mirror(vf, ctx.getCurrentEnvt().getStore(), ctx);
			String name = n.getValue();
			return m.mirrorClass(name, Class.forName(name));
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(n.getValue());
		}
	}

	private Class<?> loadSingleClass(String className, ClassWriter cw) throws ClassNotFoundException {
		ClassMapLoader l = new ClassMapLoader(getClass().getClassLoader());
		l.putBytes(className, cw.toByteArray());
		return l.getClass(className);
	}
	
	/**
	 * Load classes from a simple map (from class names to their bytearray bytecode representations)
	 */
	static private class ClassMapLoader extends ClassLoader implements Iterable<String>, Opcodes {
		private final Map<String, byte[]> bytecodes;
		private final Map<String, Class<?>> cache;

		public ClassMapLoader(ClassLoader parent) {
			super(parent);
			this.bytecodes = new HashMap<>();
			this.cache = new HashMap<>();
		}
		
		public void putBytes(String name, byte[] bytes) {
			bytecodes.put(name, bytes);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			return getClass(name);
		}
		
		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			return getClass(name);
		}
		
		public Class<?> getClass(String name) throws ClassNotFoundException {
			if (cache.containsKey(name)) {
				return cache.get(name);
			}
			
			byte[] bytes = bytecodes.get(name);
			if (bytes == null) {
				return getParent().loadClass(name);
			}
			
			Class<?> result = super.defineClass(name, bytes, 0, bytes.length);
			cache.put(name,  result);
			return result;
		}

		@Override
		public Iterator<String> iterator() {
			return bytecodes.keySet().iterator();
		}
	}
	
	/**
	 * The Compile class encapsulates a single run of the muJava -> JVM bytecode compiler
	 * for a single Class definition.
	 */
	private static class Compile {
		private static final Builder<?> DONE = () -> { return null; };
		private final ClassVisitor cw;
		private final int version;
		@SuppressWarnings("unused")
		private final PrintWriter out;
		private ArrayList<IConstructor> variableTypes;
		private ArrayList<String> variableNames;
		private ArrayList<IConstructor> variableDefaults;
		private boolean hasDefaultConstructor = false;
		private boolean hasStaticInitializer;
		private boolean isInterface;
		private Map<String, IConstructor> fieldInitializers = new HashMap<>();
		private Map<String, IConstructor> staticFieldInitializers = new HashMap<>();
		private LeveledLabel methodStartLabel;
		private LeveledLabel methodEndLabel;
		private MethodNode method;
		private ArrayList<Builder<?>> tryFinallyNestingLevel = new ArrayList<>();
		private IConstructor classType;
		private ClassNode classNode;
		private final Builder<?> pushTrue = () -> trueExp();
		private final Builder<?> pushFalse = () -> falseExp();
		private Map<String, LeveledLabel> labels;
		private boolean emittingFinally = false;
		private final boolean debug;

		public Compile(ClassVisitor cw, int version, PrintWriter out, boolean debug) {
			this.cw = cw;
			this.version = version;
			this.out = out;
			this.debug = debug;
		}

		public void compileClass(IConstructor o) {
			classNode = new ClassNode();
			IWithKeywordParameters<? extends IConstructor> kws = o.asWithKeywordParameters();

			isInterface = AST.$is("interface", o);
			classType = AST.$getType(o);
			classNode.version = version;
			classNode.signature = null; /* anything else leads to the class extending itself! */
			classNode.name = AST.$getName(classType);

			if (kws.hasParameter("modifiers")) {
				classNode.access = access(AST.$getModifiers(o));
			}
			else {
				classNode.access = Opcodes.ACC_PUBLIC;
			}

			if (isInterface) {
				classNode.access += Opcodes.ACC_ABSTRACT + Opcodes.ACC_INTERFACE;
			}

			if (kws.hasParameter("super")) {
				classNode.superName = AST.$getSuper(kws);
			}
			else {
				classNode.superName = "java/lang/Object";
			}

			if (kws.hasParameter("interfaces")) {
				ArrayList<String> interfaces = new ArrayList<String>();
				for (IValue v : AST.$getInterfaces(kws)) {
					interfaces.add(AST.$getName((IConstructor) v).replace('.','/'));
				}
				classNode.interfaces = interfaces;
			}

			if (kws.hasParameter("source")) {
				classNode.sourceFile = AST.$getSourceParameter(kws);
			}
			else {
				classNode.sourceFile = null;
			}

			if (kws.hasParameter("fields")) {
				fields(classNode, AST.$getFieldsParameter(kws), isInterface);
			}

			if (kws.hasParameter("methods")) {
				methods(classNode, AST.$getMethodsParameter(kws));
			}

			if (!hasDefaultConstructor && !isInterface) {
				generateDefaultConstructor(classNode);
			}

			if (!hasStaticInitializer && !staticFieldInitializers.isEmpty()) {
				staticInitializer(classNode, null);
			}

			classNode.accept(cw);
		}

		private void generateDefaultConstructor(ClassNode cn) {
			method = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
			method.visitCode();
			Label l0 = new LeveledLabel(0);
			Label l1 = new LeveledLabel(0);
			method.visitLocalVariable("this", "L" + cn.name + ";", null, l0, l1, 0);

			// this = new MyClass(...)
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cn.superName, "<init>", "()V", false);

			// this.a = blaBla;
			// ...
			fieldInitializers(classNode, method);

			// return
			method.visitInsn(Opcodes.RETURN);
			method.visitLabel(l1);

			if (debug) {
				method.visitMaxs(Short.MAX_VALUE, Short.MAX_VALUE);
			}
			else {
				method.visitMaxs(0, 0);
			}
			method.visitEnd();
			classNode.methods.add(method);
		}

		private void fields(ClassNode classNode, IList fields, boolean interf) {
			for (IValue field : fields) {
				IConstructor cons = (IConstructor) field;
				field(classNode, cons, interf);
			}
		}

		private void methods(ClassNode classNode, IList methods) {
			for (IValue field : methods) {
				IConstructor cons = (IConstructor) field;
				if (cons.getConstructorType().getName().equals("static")) {
					staticInitializer(classNode, cons);
				}
				else {
					method(classNode, cons);
				}
			}
		}

		private void staticInitializer(ClassNode classNode, IConstructor cons) {
			if (hasStaticInitializer) {
				throw new IllegalArgumentException("can only have one static initializer per class");
			}
			else {
				hasStaticInitializer = true;
			}

			method = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);

			variableTypes = new ArrayList<>();
			variableNames = new ArrayList<>();
			variableDefaults = new ArrayList<>();

			methodStartLabel = new LeveledLabel(0);
			methodEndLabel = new LeveledLabel(0);

			method.visitCode(); 
			method.visitLabel(methodStartLabel);

			staticFieldInitializers(classNode, method);

			IList block = AST.$getBlock(cons);
			if (block != null) {
				statements(block, null, null, methodEndLabel);
			}

			method.visitLabel(methodEndLabel);
			method.visitInsn(Opcodes.RETURN);
			if (debug) {
				method.visitMaxs(Short.MAX_VALUE, Short.MAX_VALUE);
			}
			else {
				method.visitMaxs(0, 0);
			}
			method.visitEnd();

			classNode.methods.add(method);
		}


		private void method(ClassNode classNode, IConstructor cons) {
			IWithKeywordParameters<? extends IConstructor> kws = cons.asWithKeywordParameters();

			boolean isAbstract = cons.getConstructorType().getArity() == 1; // only a signature
			
			int modifiers = Opcodes.ACC_PUBLIC;
			modifiers += isAbstract ? Opcodes.ACC_ABSTRACT : 0;
			
			if (kws.hasParameter("modifiers")) {
				modifiers = modifiers(AST.$getModifiersParameter(kws));
			}

			IConstructor sig = AST.$getDesc(cons);
			boolean isConstructor = sig.getConstructorType().getName().equals("constructorDesc");
			String name = isConstructor ? "<init>" : AST.$getName(sig);

			method = new MethodNode(modifiers, name, isConstructor ? Signature.constructor(sig) : Signature.method(sig), null, null);
			
			// every method body has a fresh set of jump labels
			labels = new HashMap<>(); 
			
			if (!isAbstract) {
				if (isInterface && classNode.version < Opcodes.V1_8) {
					throw new IllegalArgumentException("default methods requires at least JVM version v1_8()");
				}
				
				if ((modifiers & Opcodes.ACC_ABSTRACT) != 0) {
					throw new IllegalArgumentException("method with body should not be abstract");
				}
				
				IList sigFormals = AST.$getFormals(sig);
				hasDefaultConstructor |= (isConstructor && sigFormals.isEmpty());
				IList varFormals = AST.$getFormals(cons);

				if (sigFormals.length() != varFormals.length()) {
					throw new IllegalArgumentException("type signature of " + name + " has different number of types (" + sigFormals.length() + ") from formal parameters (" + varFormals.length() + "), see: " + sigFormals + " versus " + varFormals);
				}

				boolean isStatic = (modifiers & Opcodes.ACC_STATIC) != 0;

				variableTypes = new ArrayList<>();
				variableNames =  new ArrayList<>();
				variableDefaults = new ArrayList<>();

				if (!isStatic) {
					declareVariable(classType, "this", null, false);
				}

				methodStartLabel = new LeveledLabel(0);
				methodEndLabel = new LeveledLabel(0);

				method.visitCode(); 
				method.visitLabel(methodStartLabel);
				
				formalVariables(varFormals, false /* no initialization */);

				if (isConstructor && !fieldInitializers.isEmpty()) {
					fieldInitializers(classNode, method);
				}

				statements(AST.$getBlock(cons), methodStartLabel, methodEndLabel, methodEndLabel);

				method.visitLabel(methodEndLabel);
				
				for (int i = 0; i < variableNames.size(); i++) {
					String varName = variableNames.get(i);
					
					if (varName == null) {
						continue; // empty slot
					}
					
					String varType = Signature.type(variableTypes.get(i));
					
					method.visitLocalVariable(varName, varType, null, methodStartLabel, methodEndLabel, i);
				}
				
				if (debug) {
					method.visitMaxs(Short.MAX_VALUE, Short.MAX_VALUE);
				}
				else {
					method.visitMaxs(0, 0);
				}
			}
			
			method.visitEnd(); // also needed for abstract methods
			classNode.methods.add(method);
		}
		
		private void declareVariable(IConstructor type, String name, IConstructor def, boolean alwaysInitialize) {
			int pos = variableNames.size();
			
			variableTypes.add(type);
			variableNames.add(name);
			variableDefaults.add(def);
			
			String typeName = type.getConstructorType().getName();
			if (typeName.equals("double") || typeName.equals("long")) {
				// doubles and longs take up 2 stack positions
				variableTypes.add(null);
				variableNames.add(null);
				variableDefaults.add(null); 
			}
			
			if (alwaysInitialize) {
				if (def == null) {
					computeDefaultValueForVariable(type, pos);
				}
				else {
					storeStat(name, def);
				}
			}
			else {
				if (def != null && (typeName.equals("reference") || typeName.equals("array"))) {
					// if somebody passed 'null' as actual parameter and we have something to initialize with here,
					// then we store that into the variable now. mujava has default parameters!
					loadExp(name);
					invertedConditionalFlow(0, Opcodes.IFNONNULL, () -> storeStat(name, def), null, null);
				}
			}
			
			return;
		}

		private void fieldInitializers(ClassNode classNode, MethodNode method) {
			for (String field : fieldInitializers.keySet()) {
				IConstructor def = fieldInitializers.get(field);
				IConstructor exp = (IConstructor) def.asWithKeywordParameters().getParameter("init");
				loadExp("this");
				expr(exp);
				method.visitFieldInsn(Opcodes.PUTFIELD, classNode.name, field, Signature.type(AST.$getType(def)));
			}
		}

		private void staticFieldInitializers(ClassNode classNode, MethodNode method) {
			for (String field : staticFieldInitializers.keySet()) {
				IConstructor def = staticFieldInitializers.get(field);
				IConstructor exp = (IConstructor) def.asWithKeywordParameters().getParameter("init");
				expr(exp);
				method.visitFieldInsn(Opcodes.PUTSTATIC, classNode.name, field, Signature.type(AST.$getType(def)));
			}
		}

		private void formalVariables(IList formals, boolean initialize) {
			for (IValue elem : formals) {
				IConstructor var = (IConstructor) elem;
				IConstructor varType = AST.$getType(var);
				declareVariable(varType, AST.$getName(var), AST.$getDefault(var), initialize);
			}
		}

		private void computeDefaultValueForVariable(IConstructor type, int pos) {
			Switch.type(type, pos,
					(BiConsumer<IConstructor,Integer>) (z,j) -> { 
						method.visitInsn(Opcodes.ICONST_0);
						method.visitVarInsn(Opcodes.ISTORE, j); 
					},
					(ii,j) -> { 
						method.visitInsn(Opcodes.ICONST_0);
						method.visitVarInsn(Opcodes.ISTORE, j); 
					},
					(s,j) -> { 
						method.visitInsn(Opcodes.ICONST_0);
						method.visitVarInsn(Opcodes.ISTORE, j); 
					},
					(b,j) -> { 
						method.visitInsn(Opcodes.ICONST_0);
						method.visitVarInsn(Opcodes.ISTORE, j); 
					},
					(c,j) -> { 
						method.visitInsn(Opcodes.ICONST_0);
						method.visitVarInsn(Opcodes.ISTORE, j); 
					},
					(f,j) -> { 
						method.visitInsn(Opcodes.FCONST_0);
						method.visitVarInsn(Opcodes.FSTORE, j); 
					},
					(d,j) -> { 
						method.visitInsn(Opcodes.DCONST_0);
						method.visitVarInsn(Opcodes.DSTORE, j);
					}, 
					(l,j) -> { 
						method.visitInsn(Opcodes.LCONST_0);
						method.visitVarInsn(Opcodes.LSTORE, j);
					}, 
					(v,j) -> { 
						throw new IllegalArgumentException("void variable"); 
					},
					(c,j) -> { 
						method.visitInsn(Opcodes.ACONST_NULL);
						method.visitVarInsn(Opcodes.ASTORE, j); 
					},
					(a,j) -> { 
						method.visitInsn(Opcodes.ACONST_NULL);
						method.visitVarInsn(Opcodes.ASTORE, j); 
					},
					(S,j) -> {
						stringConstant("");
						method.visitVarInsn(Opcodes.ASTORE, j);
					}

					);
		}

		private Void statements(IList statements, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			int i = 0, len = statements.length();
			for (IValue elem : statements) {
				// generate the label for where the next statement ends, unless this is the last statement, because
				// then we rejoin the context and we have a label for that given in the parameter 'joinLabel'
				LeveledLabel nextLabel = (++i < len) ? newLabel(tryFinallyNestingLevel) : joinLabel;
				statement((IConstructor) elem, continueLabel, breakLabel, nextLabel);
				if (i < len) {
					method.visitLabel(nextLabel);
				}
			}
			return null;
		}

		private void statement(IConstructor stat, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			switch (stat.getConstructorType().getName()) {
			case "incr":
				incStat(AST.$getName(stat), AST.$getInc(stat));
				break;
			case "decl":
				declStat(stat, joinLabel);
				break;
			case "block":
				String blockLabel = stat.asWithKeywordParameters().hasParameter("label") ? ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue() : null;
				blockStat(blockLabel, AST.$getBlock(stat), joinLabel);
				break;
			case "do" : 
				doStat((IConstructor) stat.get("exp"));
				break;
			case "store" : 
				storeStat(AST.$getName(stat), AST.$getValue(stat)); 
				break;
			case "astore" :
				aastoreStat(AST.$getArray(stat), AST.$getIndex(stat), AST.$getArg(stat));
				break;
			case "putField":
				putFieldStat(AST.$getClassFromType(AST.$getClass(stat), classNode.name), AST.$getReceiver(stat), AST.$getType(stat), AST.$getName(stat), AST.$getArg(stat));
				break;
			case "putStatic":
				putStaticStat(AST.$getClassFromType(AST.$getClass(stat), classNode.name), AST.$getType(stat), AST.$getName(stat), AST.$getArg(stat));
				break;
			case "return" : 
				returnStat(stat);
				// dropping the joinLabel, there is nothing to do after return!
				break;
			case "break":
				breakStat(stat, breakLabel);
				// dropping the joinLabel, there is nothing to do after break!
				break;
			case "continue":
				continueStat(stat, continueLabel);
				// dropping the joinLabel, there is nothing to do after break!
				break;
			
			case "if":
				if (stat.getConstructorType().getArity() == 3) {
					ifThenElseStat(AST.$getCondition(stat), AST.$getThenBlock(stat), AST.$getElseBlock(stat), continueLabel, breakLabel, joinLabel);
				}
				else {
					assert stat.getConstructorType().getArity() == 2;
					ifStat(AST.$getCondition(stat), AST.$getThenBlock(stat), continueLabel, breakLabel, joinLabel);
				}
				break;
			case "for":
				String forLabel = stat.asWithKeywordParameters().hasParameter("label") ? ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue() : null;
				forStat(forLabel, AST.$getInit(stat), AST.$getCondition(stat), AST.$getNext(stat), AST.$getStatements(stat), continueLabel, breakLabel, joinLabel);
				break;
			case "while":
				String whileLabel = stat.asWithKeywordParameters().hasParameter("label") ? ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue() : null;
				whileStat(whileLabel, AST.$getCondition(stat), AST.$getBlock(stat), continueLabel, breakLabel, joinLabel);
				break;
			case "doWhile":
				String doWhileLabel = stat.asWithKeywordParameters().hasParameter("label") ? ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue() : null;
				doWhileStat(doWhileLabel, AST.$getCondition(stat), AST.$getBlock(stat), continueLabel, breakLabel, joinLabel);
				break;
			case "throw":
				throwStat(AST.$getArg(stat));
				break;
			case "monitor":
				monitorStat(AST.$getArg(stat), AST.$getBlock(stat), continueLabel, breakLabel, joinLabel);
				break;
			case "try":
				tryStat(AST.$getBlock(stat), AST.$getCatch(stat), continueLabel, breakLabel, joinLabel);
				break;
			case "switch":
				String option = stat.asWithKeywordParameters().hasParameter("option") ? ((IConstructor) stat.asWithKeywordParameters().getParameter("option")).getConstructorType().getName() : "auto";
				switchStat(option, AST.$getArg(stat), AST.$getCases(stat), continueLabel, breakLabel, joinLabel);
				break;
			}
		}

		private void switchStat(String option, IConstructor arg, IList cases, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			switch (option) {
			case "table":
				tableSwitch(arg, cases, continueLabel, joinLabel);
				return;
			case "lookup":
				lookupSwitch(arg, cases, continueLabel, joinLabel);
				return;
			}
			
			autoSwitch(arg, cases, continueLabel, joinLabel);
		}

		private void autoSwitch(IConstructor arg, IList cases, LeveledLabel continueLabel, LeveledLabel joinLabel) {
			int max = Integer.MAX_VALUE;
			int min = Integer.MIN_VALUE;
			long labelCount = 0;
			
			for (int i = 0; i < cases.length(); i++) {
				IConstructor c = (IConstructor) cases.get(i);
				boolean isDefault = AST.$is("default", c);
				
				if (!isDefault) {
					int key = AST.$getKey(c);
					min = Math.min(key, min);
					max = Math.max(key, max);
					labelCount++;
				}
			}
			
			// we're exactly mimicking Java compiler, see 
			// http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/30db5e0aaf83/src/share/classes/com/sun/tools/javac/jvm/Gen.java#l1153
			
			long wordCost = 4 + ((long) max - min + 1); 
			long comparisonsCost  = 3; // comparisons
			long lookupWordCost = 3 + 2 * (long) labelCount;
			long lookupComparisonCost = labelCount;
			long tableSwitchCost = wordCost + 3 * comparisonsCost;
			long lookupSwitchCost = lookupWordCost + 3 * lookupComparisonCost;
			
			if (labelCount > 0 && tableSwitchCost <= lookupSwitchCost) {
				tableSwitch(arg, cases, continueLabel, joinLabel);
			}
			else {
				lookupSwitch(arg, cases, continueLabel, joinLabel);
			}
		}

		/** 
		 * Generates a LOOKUPSWITCH instruction, which jumps to each case in O(log(n)) where n is the
		 * number of cases, that is if you ignore the cost of far away memory lookup and cache misses. 
		 * The actual cost might be more in reality, but it's very close to log(n) comparisons to find the 
		 * right jump label and a goto instruction. 
		 * 
		 * The table is stored in a sparse manner, so only the actual labels and their handlers
		 * are stored. This gives good cache performance. 
		 *  
		 * The case handlers are registered in order and have
		 * "fall-through" semantics by default.
		 * 
		 * lookpSwitch is best to call if the case labels are not consecutive integers, the total set of integers
		 * is a sparse and/or more or less uniformally distributed set (like hashcode's of Strings for example).
		 */
		private void lookupSwitch(IConstructor arg, IList cases, LeveledLabel continueLabel, LeveledLabel joinLabel) {
			ArrayList<Integer> keys = new ArrayList<>();
			ArrayList<Label> labels = new ArrayList<>();
			Label defaultLabel = new Label();
			boolean hasDef = false;
			
			for (int i = 0; i < cases.length(); i++) {
				IConstructor c = (IConstructor) cases.get(i);
				
				if (AST.$is("default", c)) {
					defaultLabel = new Label();
					hasDef = true;
					
					if (i != cases.length() - 1) {
						throw new IllegalArgumentException("default handler should be the last of the cases");
					}
				}
				else {
					int key = AST.$getKey(c);
					Label caseLabel = new Label();
					
					// NB! the lookupswitch wants the cases in reverse order!
					keys.add(0, key);
					labels.add(0, caseLabel);
				}
			}
				
			
			// first put the key value on the stack
			expr(arg);
						
			// here come the handlers
			int[] keyArray = keys.stream().mapToInt(i->i).toArray();
			Label[] labelArray = labels.toArray(new Label[0]);
			
			// NOTE: this only works correctly if the jump labels have already been visited			
			method.visitLookupSwitchInsn(defaultLabel, keyArray, labelArray);
						
			// the case code must be printed in the original order for fall-through semantics
			for (int i = 0; i < cases.length(); i++) {
				IConstructor c = (IConstructor) cases.get(i);
				boolean isDef = AST.$is("default", c);

				if (isDef) {
					method.visitLabel(defaultLabel);
				}
				else {
					int reverseInd = cases.length() - i - (hasDef?2:1);
					method.visitLabel(labelArray[reverseInd]);
				}

				LeveledLabel endCase = newLabel(tryFinallyNestingLevel);
				statements(AST.$getBlock(c), continueLabel, joinLabel /* break will jump beyond the switch */, endCase);
				method.visitLabel(endCase);
			}
			
			if (!hasDef) {
				method.visitLabel(defaultLabel);
			}
			
		}


		/** 
		 * Generates a TABLESWITCH instruction, which jumps to each case in O(1), that is if you ignore
		 * the cost of far away memory lookup and cache misses. The actual cost may depend on actual memory 
		 * addresses, but it's pretty close to a regular single conditional jump instruction. 
		 * 
		 * The table is filled from the minimum label to
		 * the maximum label with jumps to the default case, and only in the slots for the actual cases jumps
		 * to the respective case handlers are made. The case handlers are registered in order and have
		 * "fall-through" semantics by default.
		 * 
		 * tableSwitch is best to call if the case labels are consecutive integers and if there are not so many 
		 * as to trigger a cache misses all the time.
		 */
		private void tableSwitch(IConstructor arg, IList cases, LeveledLabel continueLabel, LeveledLabel joinLabel) {
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			boolean hasDefault = false;

			// first we collect information about the cases in the switch, and check 
			// if the default is in the right place
			for (int i = 0; i < cases.length(); i++) {
				IConstructor c = (IConstructor) cases.get(i);
				boolean isLast = i == cases.length() - 1;
				boolean isDefault = AST.$is("default", c);
				
				if (!isDefault) {
					int key = AST.$getKey(c);
					min = Math.min(key, min);
					max = Math.max(key, max);
				}
				else {
					hasDefault = true;
					
					if (!isLast) {
						throw new IllegalArgumentException("default handler should be the last of the cases");
					}
				}
			}
				
			LeveledLabel defaultLabel = hasDefault ? newLabel(tryFinallyNestingLevel) : joinLabel;
			Label[] labels = new LeveledLabel[max - min + 1];
			
			for (int i = 0; i < labels.length; i++) {
				labels[i] = defaultLabel; // they all jump to default
			}
			
			// unless there is case to jump to:
			for (int j = 0; j < cases.length(); j++) {
				IConstructor c = (IConstructor) cases.get(j);
				if (AST.$is("default", c)) {
					continue;
				}
				else {
					// overwrite the default label with the case label
					labels[AST.$getKey(c) - min] = newLabel(tryFinallyNestingLevel);
				}
			}
			
			// first put the key value on the stack
			expr(arg);
						
			// then we generate the switch tabel
			method.visitTableSwitchInsn(min, max, defaultLabel, labels);
			
			// here come the handlers
			for (int i = 0; i < cases.length(); i++) {
				IConstructor c = (IConstructor) cases.get(i);
				boolean isDef = AST.$is("default", c);

				if (isDef) {
					method.visitLabel(defaultLabel);
				}
				else {
					method.visitLabel(labels[AST.$getKey(c) - min]);
				}

				LeveledLabel endCase = newLabel(tryFinallyNestingLevel);
				statements(AST.$getBlock(c), continueLabel, joinLabel /* break will jump beyond the switch */, endCase);
				method.visitLabel(endCase);
			}
		}

		private void incStat(String name, int inc) {
			method.visitIincInsn(positionOf(name), inc);
		}

		/**
		 * Try/catch/finally is the most complex statement-type to compile, in particular 
		 * because it interacts heavily with return, and (labeled) break and continue statements.
		 * 
		 * We lean heavily on MethodNode to collect all the information about the catch blocks where
		 * we find it in the AST, such that later when streaming this MethoeNode to bytecode the 
		 * handlers are printed in the right order.
		 */
		private void tryStat(IList block, IList catches, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			if (block.length() == 0) {
				// JVM can not deal with empty catch ranges anyway
				return;
			}
			
			Label tryStart = newLabel(tryFinallyNestingLevel);
			Label tryEnd = newLabel(tryFinallyNestingLevel);
			Label[] handlers = new LeveledLabel[catches.length()];
			
			String finallyVarName = null;
			Builder<?> finallyCode = null;
			
			// produce handler registration for every catch block
			for (int i = 0; i < catches.length(); i++) {
				IConstructor catcher = (IConstructor) catches.get(i);
				boolean isFinally = AST.$is("finally", catcher);
				boolean isLast = i == catches.length() - 1;
				handlers[i] = newLabel(tryFinallyNestingLevel);
				
				if (isLast && isFinally) {
					finallyVarName = "finally:" + UUID.randomUUID();
					declareVariable(Types.throwableType(), finallyVarName, null, false);
					finallyCode = () -> statements(AST.$getBlock(catcher), breakLabel, continueLabel, joinLabel);
					pushFinally(finallyCode);
				}
				else if (isFinally) {
					throw new IllegalArgumentException("finally block should be the last handler");
				}
				else {
					IConstructor exceptionType = AST.$getType(catcher);
					String varName = AST.$getName(catcher);
					declareVariable(exceptionType, varName, null, false);
				}
			}
			
			// the try block itself
			method.visitLabel(tryStart);
			statements(block, continueLabel, breakLabel, joinLabel);
			method.visitLabel(tryEnd);
			
			// jump over the catch blocks
			method.visitJumpInsn(Opcodes.GOTO, joinLabel);
			
			// generate blocks for each handler
			for (int i = 0; i < catches.length(); i++) {
				IConstructor catcher = (IConstructor) catches.get(i);
				boolean isFinally = AST.$is("finally", catcher);
				boolean isLast = i == catches.length() - 1;
				
				if (isLast && isFinally) {
					method.visitLabel(handlers[i]);
					finallyCode.build();
					popFinally();
				}
				else { // normal catch handler
					method.visitLabel(handlers[i]);
					String varName = AST.$getName(catcher);
					IConstructor exceptionType = AST.$getType(catcher);
					String clsName = AST.$getClassFromType(exceptionType, classNode.name);
					method.visitVarInsn(Opcodes.ASTORE, positionOf(varName));
					statements(AST.$getBlock(catcher), continueLabel, breakLabel, joinLabel);
					method.visitTryCatchBlock(tryStart, tryEnd, handlers[i], clsName);
					
					if (!isLast) { // jump over the other handlers
						method.visitJumpInsn(Opcodes.GOTO, joinLabel);
					}
				}
			}
		}

		private Builder<?> popFinally() {
			return tryFinallyNestingLevel.remove(tryFinallyNestingLevel.size() - 1);
		}

		private boolean pushFinally(Builder<?> finallyCode) {
			return tryFinallyNestingLevel.add(finallyCode);
		}

		private void monitorStat(IConstructor lock, IList block, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			Label startExceptionBlock = newLabel(tryFinallyNestingLevel);
			Label endExceptionBlock = newLabel(tryFinallyNestingLevel);
			Label handlerStart = newLabel(tryFinallyNestingLevel);
			Label handlerEnd = newLabel(tryFinallyNestingLevel);
			
			method.visitTryCatchBlock(startExceptionBlock, endExceptionBlock, handlerStart, null);
			method.visitTryCatchBlock(handlerStart, handlerEnd, handlerStart, null);

			IConstructor type = expr(lock);
			String lockVarName = "$lock:" + UUID.randomUUID().toString();
			declareVariable(type, lockVarName, null, false);
			dup(); // for MONITORENTER
			method.visitVarInsn(Opcodes.ASTORE, positionOf(lockVarName));
			method.visitInsn(Opcodes.MONITORENTER);
			
			method.visitLabel(startExceptionBlock);
			method.visitLineNumber(22, startExceptionBlock);
			statements(block, continueLabel, breakLabel, null /* no support for break, continue, goto */);
			method.visitVarInsn(Opcodes.ALOAD, positionOf(lockVarName));
			method.visitInsn(Opcodes.MONITOREXIT);
			method.visitLabel(endExceptionBlock);

			method.visitJumpInsn(Opcodes.GOTO, joinLabel); // nothing happened
			method.visitLabel(handlerStart);
			method.visitVarInsn(Opcodes.ALOAD, positionOf(lockVarName));
			method.visitInsn(Opcodes.MONITOREXIT); // an exception happened, exit the monitor
			method.visitLabel(handlerEnd);
			method.visitInsn(Opcodes.ATHROW); // rethrow
		}

		private void throwStat(IConstructor arg) {
			expr(arg);
			method.visitInsn(Opcodes.ATHROW);
		}

		private void whileStat(String label, IConstructor cond, IList body, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			LeveledLabel testConditional = newLabel(tryFinallyNestingLevel);
			
			if (label != null) {
				labels.put("break:" + label, joinLabel);
				labels.put("continue:" + label, testConditional);
			}

			method.visitLabel(testConditional);
			
			// deal efficiently with negated conditionals
			int cmpCode = Opcodes.IFEQ;
			if (cond.getConstructorType().getName().equals("neg")) {
				cond = expr(AST.$getArg(cond));
				cmpCode = Opcodes.IFNE;
			}

			expr(cond);
			invertedConditionalFlow(0, cmpCode, 
					() -> statements(body, testConditional, joinLabel, testConditional), 
					() -> jumpTo(joinLabel) /* end of loop */, 
					testConditional);
			
			jumpTo(testConditional); // this might be superfluous
		}
		
		private void doWhileStat(String label, IConstructor cond, IList body, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			LeveledLabel nextIteration = newLabel(tryFinallyNestingLevel);
			
			if (label != null) {
				labels.put("break:" + label, joinLabel);
				labels.put("continue:" + label, nextIteration);
			}

			method.visitLabel(nextIteration);
			
			statements(body, nextIteration, joinLabel, nextIteration);
			
			// deal efficiently with negated conditionals
			int cmpCode = Opcodes.IFEQ;
			if (cond.getConstructorType().getName().equals("neg")) {
				cond = expr(AST.$getArg(cond));
				cmpCode = Opcodes.IFNE;
			}

			// while(cond)
			expr(cond);
			invertedConditionalFlow(0, cmpCode, 
					() -> jumpTo(nextIteration), 
					null /* end of loop */, 
					joinLabel);
		}

		private void breakStat(IConstructor stat, LeveledLabel join) {
			if (join == null) {
				throw new IllegalArgumentException("no loop to break from (or inside an expression or monitor block");
			}
			
			LeveledLabel target = join;
			
			if (stat.asWithKeywordParameters().hasParameter("label")) {
				String loopLabel = ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue();
				target = getLabel(tryFinallyNestingLevel.size(), "break:" + loopLabel);
			}
			
			emitFinally(target.getFinallyNestingLevel());
			method.visitJumpInsn(Opcodes.GOTO, target);
		}
		
		private void continueStat(IConstructor stat, LeveledLabel join) {
			if (join == null) {
				throw new IllegalArgumentException("no loop to continue with (or inside an expression or monitor block");
			}
			
			LeveledLabel target = join;
			
			if (stat.asWithKeywordParameters().hasParameter("label")) {
				String loopLabel = ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue();
				target = getLabel(tryFinallyNestingLevel.size(), "continue:" + loopLabel);
			}
			
			emitFinally(target.getFinallyNestingLevel());
			method.visitJumpInsn(Opcodes.GOTO, target);
		}

		private LeveledLabel getLabel(int level, String label) {
			LeveledLabel l = labels.get(label);
			
			if (l == null) {
				throw new IllegalArgumentException("unknown label: " + label);
			}
			
			return l;
		}

		private void blockStat(String label, IList body, LeveledLabel joinLabel) {
			LeveledLabel again = newLabel(tryFinallyNestingLevel);
			
			labels.put("break:" + label, joinLabel);
			labels.put("continue:" + label, again);

			method.visitLabel(again);
			statements(body, again, joinLabel, joinLabel);
		}

		private void declStat(IConstructor stat, LeveledLabel joinLabel) {
			IConstructor def = null;
			if (stat.asWithKeywordParameters().hasParameter("init")) {
				def = (IConstructor) stat.asWithKeywordParameters().getParameter("init");
			}
			
			declareVariable(AST.$getType(stat), AST.$getName(stat), def, true);
		}

		private void forStat(String label, IList init, IConstructor cond, IList next, IList body, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			LeveledLabel testConditional = newLabel(tryFinallyNestingLevel);
			LeveledLabel nextIterationLabel = newLabel(tryFinallyNestingLevel);
			
			if (label != null) {
				labels.put("break:" + label, joinLabel);
				labels.put("continue:" + label, nextIterationLabel);
			}

			statements(init, continueLabel /*outerloop*/, breakLabel /*outerloop*/, testConditional /*start of inner loop*/);
			
			method.visitLabel(testConditional);
			
			// deal efficiently with negated conditionals
			int cmpCode = Opcodes.IFEQ;
			if (cond.getConstructorType().getName().equals("neg")) {
				cond = expr(AST.$getArg(cond));
				cmpCode = Opcodes.IFNE;
			}

			expr(cond);
			invertedConditionalFlow(0, cmpCode, 
					() -> statements(body, nextIterationLabel, joinLabel, nextIterationLabel), 
					() -> jumpTo(joinLabel) /* end of loop */, 
					nextIterationLabel);
			
			method.visitLabel(nextIterationLabel);
			LeveledLabel endNext = newLabel(tryFinallyNestingLevel);
			statements(next, continueLabel /*outerloop */, breakLabel /*outerloop*/, endNext);
			method.visitLabel(endNext);
			jumpTo(testConditional); // this might be superfluous
		}

		private Void jumpTo(Label join) {
			method.visitJumpInsn(Opcodes.GOTO, join);
			return null;
		}

		private void ifStat(IConstructor cond, IList thenBlock, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			ifThenElseStat(cond, thenBlock, null, continueLabel, breakLabel, joinLabel);
		}

		private void ifThenElseStat(IConstructor cond, IList thenBlock, IList elseBlock, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel) {
			Builder<?> thenBuilder = () -> statements(thenBlock, continueLabel, breakLabel, joinLabel);
			Builder<?> elseBuilder = elseBlock != null ? () -> statements(elseBlock, continueLabel, breakLabel, joinLabel) : DONE;

			// here we special case for !=, ==, <=, >=, < and >, because
			// there are special jump instructions for these operators on the JVM and we don't want to push
			// a boolean on the stack and then conditionally have to jump on that boolean again:
			switch (cond.getConstructorType().getName()) {
			case "true":
				statements(thenBlock, continueLabel, breakLabel, joinLabel);
				break;
			case "false":
				if (elseBlock != null) {
					statements(elseBlock, continueLabel, breakLabel, joinLabel);
				}
				break;
			case "eq":
				eqExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel);
				return;
			case "ne":
				neExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel);
				return;
			case "le":
				leExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel);
				return;
			case "gt":
				gtExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel);
				return;
			case "ge":
				geExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel);
				return;
			case "lt":
				ltExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel);
				return;
			case "neg":
				// if(!expr) is compiled to IFNE directly without intermediate (inefficient) negation code
				expr(AST.$getArg(cond));
				invertedConditionalFlow(0, Opcodes.IFNE, thenBuilder, elseBuilder, joinLabel);
				return;
			default:
				expr(cond);
				invertedConditionalFlow(0, Opcodes.IFEQ, thenBuilder, elseBuilder, joinLabel);
				return;
			}
		}

		private void putStaticStat(String cls, IConstructor type, String name, IConstructor arg) {
			expr(arg);
			method.visitFieldInsn(Opcodes.PUTSTATIC, cls, name, Signature.type(type));
		}

		private void putFieldStat(String cls, IConstructor receiver, IConstructor type, String name, IConstructor arg) {
			expr(receiver);
			expr(arg);
			method.visitFieldInsn(Opcodes.PUTFIELD, cls, name, Signature.type(type));
		}

		private Void storeStat(String name, IConstructor expression) {
			int pos = positionOf(name);
			expr(expression);

			Switch.type0(variableTypes.get(pos),
					(z) -> { method.visitVarInsn(Opcodes.ISTORE, pos); },
					(i) -> { method.visitVarInsn(Opcodes.ISTORE, pos); },
					(s) -> { method.visitVarInsn(Opcodes.ISTORE, pos); },
					(b) -> { method.visitVarInsn(Opcodes.ISTORE, pos); },
					(c) -> { method.visitVarInsn(Opcodes.ISTORE, pos); },
					(f) -> { method.visitVarInsn(Opcodes.FSTORE, pos); },
					(d) -> { method.visitVarInsn(Opcodes.DSTORE, pos); },
					(l) -> { method.visitVarInsn(Opcodes.LSTORE, pos); },
					(v) -> { /* void */ },
					(c) -> { /* class */ method.visitVarInsn(Opcodes.ASTORE, pos); },
					(a) -> { /* array */ method.visitVarInsn(Opcodes.ASTORE, pos); },
					(S) -> { /* string */ method.visitVarInsn(Opcodes.ASTORE, pos); }
					);
			
			return null;
		}

		private void returnStat(IConstructor stat) {
			if (stat.getConstructorType().getArity() == 0) {
				method.visitInsn(Opcodes.RETURN);
			}
			else {
				IConstructor type = expr(AST.$getArg(stat));
				
				// return, or break or continue from the finally block,
				// must not execute current finally again (infinite loop),
				// so pop that and push it back when done.
				emitFinally(0);
				
				Switch.type0(type,
						(z) -> { method.visitInsn(Opcodes.IRETURN); },
						(i) -> { method.visitInsn(Opcodes.IRETURN); },
						(s) -> { method.visitInsn(Opcodes.IRETURN); },
						(b) -> { method.visitInsn(Opcodes.IRETURN); },
						(c) -> { method.visitInsn(Opcodes.IRETURN); }, 
						(f) -> { method.visitInsn(Opcodes.FRETURN); },
						(d) -> { method.visitInsn(Opcodes.DRETURN); },
						(l) -> { method.visitInsn(Opcodes.LRETURN); },
						(v) -> { /* void  */ method.visitInsn(Opcodes.RETURN); },
						(c) -> { /* class */ method.visitInsn(Opcodes.ARETURN); },
						(a) -> { /* array */ method.visitInsn(Opcodes.ARETURN); },
						(S) -> { /* string */ method.visitInsn(Opcodes.ARETURN); }
						);
			}
		}

		private void emitFinally(int toLevel) {
			// emit code for finally blocks in reverse order
			// during this build, the finally stack must NOT be active itself
			if (!emittingFinally) {
				emittingFinally  = true;

				for (int i = tryFinallyNestingLevel.size() - 1; i >= 0 && i >= toLevel; i--) {
					tryFinallyNestingLevel.get(i).build();
				}
				
				emittingFinally = false;
			}
		}

		private Void doStat(IConstructor exp) {
			IConstructor type = expr(exp); 
			Switch.type0(type, 
					(z) -> pop(), 
					(i) -> pop(), 
					(s) -> pop(), 
					(b) -> pop(), 
					(c) -> pop(), 
					(f) -> pop(), 
					(d) -> pop2(), // wide pop
					(j) -> pop2(), // wide pop
					(v) -> { /* no pop */ }, 
					(c) -> pop(), 
					(a) -> pop(),
					(S) -> pop()
					);
			return null;
		}

		private void pop() {
			method.visitInsn(Opcodes.POP);
		}

		private void pop2() {
			method.visitInsn(Opcodes.POP2);
		}

		private IConstructor expr(IConstructor exp) {
			switch (exp.getConstructorType().getName()) {
			case "const" : 
				return constExp(AST.$getType(exp), AST.$getConstant(exp)); 
			case "this" : 
				return loadExp("this");
			case "newInstance":
				return newInstanceExp(exp);
			case "newArray":
				if (exp.get(1) instanceof IList) {
					return newArrayExp(AST.$getType(exp), AST.$getArgs(exp));
				}
				else {
					return newArrayExp(AST.$getType(exp), AST.$getSize(exp));
				}
			case "alength":
				return alengthExp(AST.$getArg(exp));
			case "load" : 
				return loadExp(AST.$getName(exp)); 
			case "aload" :
				return aaloadExp(AST.$getArray(exp), AST.$getIndex(exp));
			case "getStatic":
				return getstaticExp(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getType(exp), AST.$getName(exp));
			case "invokeVirtual" : 
				return invokeVirtualExp(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
			case "invokeInterface" : 
				return invokeInterfaceExp(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
			case "invokeSpecial" : 
				return invokeSpecialExp(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
			case "invokeSuper" : 
				return invokeSuperStat(classNode.superName, AST.$getDesc(exp), AST.$getArgs(exp));
			case "invokeStatic" : 
				return invokeStaticExp(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getArgs(exp));
			case "getField":
				return getfieldExp(AST.$getReceiver(exp), AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getType(exp), AST.$getName(exp));
			case "instanceof":
				return instanceofExp(AST.$getArg(exp), AST.$getClassFromType(exp, classNode.name));
			case "sblock":
				return sblockExp(AST.$getStatements(exp), AST.$getArg(exp));
			case "null":
				if (exp.getConstructorType().getArity() == 0) {
					return nullExp();  // null constant
				}
				else { 
					return isNullTest(AST.$getArg(exp), pushTrue, pushFalse, null); // null check 
				}
			case "nonnull":
				return isNonNullTest(AST.$getArg(exp), pushTrue, pushFalse, null); // null check 
			case "true":
				return trueExp();
			case "false":
				return falseExp();
			case "coerce":
				return coerceExp(AST.$getFrom(exp), AST.$getTo(exp), AST.$getArg(exp));
			case "eq":
				return eqExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null);
			case "ne":
				return neExp(AST.$getLhs(exp), AST.$getRhs(exp), (Builder<?>) pushTrue, (Builder<?>) pushFalse, null);
			case "le":
				return leExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null);
			case "gt":
				return gtExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null);
			case "ge":
				return geExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null);
			case "lt":
				return ltExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null);
			case "add":
				return addExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "div":
				return divExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "rem":
				return remExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "sub":
				return subExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "mul":
				return mulExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "and":
				return andExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "or":
				return orExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "xor":
				return xorExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "neg":
				return negExp(AST.$getArg(exp));
			case "inc":
				return incExp(AST.$getName(exp), AST.$getInc(exp));
			case "shr":
				return shrExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "shl":
				return shlExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "ushr":
				return ushrExp(AST.$getLhs(exp), AST.$getRhs(exp));
			case "checkcast":
				return checkCastExp(AST.$getArg(exp), AST.$getType(exp));
			default: 
				throw new IllegalArgumentException("unknown expression: " + exp);                                     
			}
		}

		private IConstructor shlExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareShiftArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.ISHL),
					(i) -> method.visitInsn(Opcodes.ISHL), 
					(s) -> method.visitInsn(Opcodes.ISHL), 
					(b) -> method.visitInsn(Opcodes.ISHL), 
					(c) -> method.visitInsn(Opcodes.ISHL), 
					(f) -> { throw new IllegalArgumentException("shl on void"); },
					(d) -> { throw new IllegalArgumentException("shl on void"); },
					(l) -> method.visitInsn(Opcodes.LSHL),
					(v) -> { throw new IllegalArgumentException("shl on void"); }, 
					(c) -> { throw new IllegalArgumentException("shl on object"); },
					(a) -> { throw new IllegalArgumentException("shl on array"); },
					(S) -> { throw new IllegalArgumentException("shl on string"); }
					);
			return type;
		}

		private IConstructor prepareShiftArguments(IConstructor lhs, IConstructor rhs) {
			IConstructor type = expr(lhs);
			if (expr(rhs).getConstructorType() != Types.integerType().getConstructorType()) {
				throw new IllegalArgumentException("shift should get an integer as second parameter");
			}
			return type;
		}

		private IConstructor ushrExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareShiftArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.IUSHR),
					(i) -> method.visitInsn(Opcodes.IUSHR), 
					(s) -> method.visitInsn(Opcodes.IUSHR), 
					(b) -> method.visitInsn(Opcodes.IUSHR), 
					(c) -> method.visitInsn(Opcodes.IUSHR), 
					(f) -> { throw new IllegalArgumentException("ushr on void"); },
					(d) -> { throw new IllegalArgumentException("ushr on void"); },
					(l) -> method.visitInsn(Opcodes.LUSHR),
					(v) -> { throw new IllegalArgumentException("ushr on void"); }, 
					(c) -> { throw new IllegalArgumentException("ushr on object"); },
					(a) -> { throw new IllegalArgumentException("ushr on array"); },
					(S) -> { throw new IllegalArgumentException("ushr on string"); }
					);
			return type;
		}

		private IConstructor shrExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareShiftArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.ISHR),
					(i) -> method.visitInsn(Opcodes.ISHR), 
					(s) -> method.visitInsn(Opcodes.ISHR), 
					(b) -> method.visitInsn(Opcodes.ISHR), 
					(c) -> method.visitInsn(Opcodes.ISHR), 
					(f) -> { throw new IllegalArgumentException("shr on void"); },
					(d) -> { throw new IllegalArgumentException("shr on void"); },
					(l) -> method.visitInsn(Opcodes.LSHR),
					(v) -> { throw new IllegalArgumentException("shr on void"); }, 
					(c) -> { throw new IllegalArgumentException("shr on object"); },
					(a) -> { throw new IllegalArgumentException("shr on array"); },
					(S) -> { throw new IllegalArgumentException("shr on string"); }
					);
			return type;
		}

		private IConstructor incExp(String name, int inc) {
			method.visitIincInsn(positionOf(name), inc);
			loadExp(name);
			return Types.integerType();
		}

		private IConstructor addExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.IOR),
					(i) -> method.visitInsn(Opcodes.IADD), 
					(s) -> method.visitInsn(Opcodes.IADD), 
					(b) -> method.visitInsn(Opcodes.IADD), 
					(c) -> method.visitInsn(Opcodes.IADD), 
					(f) -> method.visitInsn(Opcodes.FADD),
					(d) -> method.visitInsn(Opcodes.DADD),
					(l) -> method.visitInsn(Opcodes.LADD),
					(v) -> { throw new IllegalArgumentException("add on void"); }, 
					(c) -> { throw new IllegalArgumentException("add on object"); },
					(a) -> { throw new IllegalArgumentException("add on array"); },
					(S) -> { throw new IllegalArgumentException("add on string"); }
					);
			return type;
		}

		private IConstructor subExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> { throw new IllegalArgumentException("sub on bool"); },
					(i) -> method.visitInsn(Opcodes.ISUB), 
					(s) -> method.visitInsn(Opcodes.ISUB), 
					(b) -> method.visitInsn(Opcodes.ISUB), 
					(c) -> method.visitInsn(Opcodes.ISUB), 
					(f) -> method.visitInsn(Opcodes.FSUB),
					(d) -> method.visitInsn(Opcodes.DSUB),
					(l) -> method.visitInsn(Opcodes.LSUB),
					(v) -> { throw new IllegalArgumentException("add on void"); }, 
					(c) -> { throw new IllegalArgumentException("add on object"); },
					(a) -> { throw new IllegalArgumentException("add on array"); },
					(S) -> { throw new IllegalArgumentException("add on string"); }
					);
			return type;
		}

		private IConstructor remExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> { throw new IllegalArgumentException("rem on bool"); },
					(i) -> method.visitInsn(Opcodes.IREM), 
					(s) -> method.visitInsn(Opcodes.IREM), 
					(b) -> method.visitInsn(Opcodes.IREM), 
					(c) -> method.visitInsn(Opcodes.IREM), 
					(f) -> method.visitInsn(Opcodes.FREM),
					(d) -> method.visitInsn(Opcodes.DREM),
					(l) -> method.visitInsn(Opcodes.LREM),
					(v) -> { throw new IllegalArgumentException("add on void"); }, 
					(c) -> { throw new IllegalArgumentException("add on object"); },
					(a) -> { throw new IllegalArgumentException("add on array"); },
					(S) -> { throw new IllegalArgumentException("add on string"); }
					);
			return type;
		}

		private IConstructor divExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> { throw new IllegalArgumentException("div on bool"); },
					(i) -> method.visitInsn(Opcodes.IDIV), 
					(s) -> method.visitInsn(Opcodes.IDIV), 
					(b) -> method.visitInsn(Opcodes.IDIV), 
					(c) -> method.visitInsn(Opcodes.IDIV), 
					(f) -> method.visitInsn(Opcodes.FDIV),
					(d) -> method.visitInsn(Opcodes.DDIV),
					(l) -> method.visitInsn(Opcodes.LDIV),
					(v) -> { throw new IllegalArgumentException("div on void"); }, 
					(c) -> { throw new IllegalArgumentException("div on object"); },
					(a) -> { throw new IllegalArgumentException("div on array"); },
					(S) -> { throw new IllegalArgumentException("div on string"); }
					);
			return type;
		}

		private IConstructor mulExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.IAND),
					(i) -> method.visitInsn(Opcodes.IMUL), 
					(s) -> method.visitInsn(Opcodes.IMUL), 
					(b) -> method.visitInsn(Opcodes.IMUL), 
					(c) -> method.visitInsn(Opcodes.IMUL), 
					(f) -> method.visitInsn(Opcodes.FMUL),
					(d) -> method.visitInsn(Opcodes.DMUL),
					(l) -> method.visitInsn(Opcodes.LMUL),
					(v) -> { throw new IllegalArgumentException("add on void"); }, 
					(c) -> { throw new IllegalArgumentException("add on object"); },
					(a) -> { throw new IllegalArgumentException("add on array"); },
					(S) -> { throw new IllegalArgumentException("add on string"); }
					);
			return type;
		}

		private IConstructor andExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.IAND),
					(i) -> method.visitInsn(Opcodes.IAND), 
					(s) -> method.visitInsn(Opcodes.IAND), 
					(b) -> method.visitInsn(Opcodes.IAND), 
					(c) -> method.visitInsn(Opcodes.IAND), 
					(f) -> { throw new IllegalArgumentException("and on float"); },
					(d) -> { throw new IllegalArgumentException("and on double"); },
					(l) -> { throw new IllegalArgumentException("and on long"); },
					(v) -> { throw new IllegalArgumentException("and on void"); }, 
					(c) -> { throw new IllegalArgumentException("and on object"); },
					(a) -> { throw new IllegalArgumentException("and on array"); },
					(S) -> { throw new IllegalArgumentException("and on string"); }
					);
			return type;
		}

		private IConstructor orExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.IOR),
					(i) -> method.visitInsn(Opcodes.IOR), 
					(s) -> method.visitInsn(Opcodes.IOR), 
					(b) -> method.visitInsn(Opcodes.IOR), 
					(c) -> method.visitInsn(Opcodes.IOR), 
					(f) -> { throw new IllegalArgumentException("or on float"); },
					(d) -> { throw new IllegalArgumentException("or on double"); },
					(l) -> { throw new IllegalArgumentException("or on long"); },
					(v) -> { throw new IllegalArgumentException("or on void"); }, 
					(c) -> { throw new IllegalArgumentException("or on object"); },
					(a) -> { throw new IllegalArgumentException("or on array"); },
					(S) -> { throw new IllegalArgumentException("or on string"); }
					);
			return type;
		}

		private IConstructor xorExp(IConstructor lhs, IConstructor rhs) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.IXOR),
					(i) -> method.visitInsn(Opcodes.IXOR), 
					(s) -> method.visitInsn(Opcodes.IXOR), 
					(b) -> method.visitInsn(Opcodes.IXOR), 
					(c) -> method.visitInsn(Opcodes.IXOR), 
					(f) -> { throw new IllegalArgumentException("xor on void"); },
					(d) -> { throw new IllegalArgumentException("xor on void"); },
					(l) -> { throw new IllegalArgumentException("xor on void"); },
					(v) -> { throw new IllegalArgumentException("xor on void"); }, 
					(c) -> { throw new IllegalArgumentException("xor on object"); },
					(a) -> { throw new IllegalArgumentException("xor on array"); },
					(S) -> { throw new IllegalArgumentException("xor on string"); }
					);
			return type;
		}

		private IConstructor negExp(IConstructor arg) {
			IConstructor type = expr(arg);
			Switch.type0(type, 
					(z) -> { 
						// TODO: is there really not a better way to negate a boolean on the JVM?
						Label zeroLabel = newLabel(tryFinallyNestingLevel);
						Label contLabel = newLabel(tryFinallyNestingLevel);
						method.visitJumpInsn(Opcodes.IFEQ, zeroLabel);
						falseExp();
						jumpTo(contLabel);
						method.visitLabel(zeroLabel);
						trueExp();
						method.visitLabel(contLabel);
					},
					(i) -> method.visitInsn(Opcodes.INEG), 
					(s) -> method.visitInsn(Opcodes.INEG), 
					(b) -> method.visitInsn(Opcodes.INEG), 
					(c) -> method.visitInsn(Opcodes.INEG), 
					(f) -> method.visitInsn(Opcodes.FNEG),
					(d) -> method.visitInsn(Opcodes.DNEG),
					(l) -> method.visitInsn(Opcodes.LNEG),
					(v) -> { throw new IllegalArgumentException("neg on void"); }, 
					(c) -> { throw new IllegalArgumentException("neg on object"); },
					(a) -> { throw new IllegalArgumentException("neg on array"); },
					(S) -> { throw new IllegalArgumentException("neg on string"); }
					);
			return type;
		}


		private IConstructor invokeSuperStat(String superclass, IConstructor sig, IList args) {
			loadExp("this");
			expressions(args);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, superclass, "<init>", Signature.constructor(sig), false);
			return Types.voidType();
		}

		private void aastoreStat(IConstructor array, IConstructor index,
				IConstructor arg) {
			IConstructor type = expr(array);
			expr(index);
			expr(arg);
			arrayStoreExpWithArrayIndexValueOnStack(AST.$getArg(type));
		}

		private void arrayStoreExpWithArrayIndexValueOnStack(IConstructor type) {
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.BASTORE),
					(i) -> method.visitInsn(Opcodes.IASTORE),
					(s) -> method.visitInsn(Opcodes.SASTORE),
					(b) -> method.visitInsn(Opcodes.BASTORE),
					(c) -> method.visitInsn(Opcodes.CASTORE),
					(f) -> method.visitInsn(Opcodes.FASTORE),
					(d) -> method.visitInsn(Opcodes.DASTORE),
					(l) -> method.visitInsn(Opcodes.LASTORE),
					(v) -> { throw new IllegalArgumentException("store void in array"); },
					(c) -> method.visitInsn(Opcodes.AASTORE),
					(a) -> method.visitInsn(Opcodes.AASTORE),
					(S) -> method.visitInsn(Opcodes.AASTORE)
					);
		}

		private IConstructor checkCastExp(IConstructor arg, IConstructor type) {
			String cons = type.getConstructorType().getName();

			// weird inconsistency in CHECKCAST instruction?
			if (cons == "reference") {
				method.visitTypeInsn(Opcodes.CHECKCAST, AST.$getName(type));
			}
			else if (cons == "array") {
				method.visitTypeInsn(Opcodes.CHECKCAST, Signature.type(type));
			}
			else {
				throw new IllegalArgumentException("can not check cast to " + type);
			}

			return type;
		}

		private IConstructor alengthExp(IConstructor arg) {
			expr(arg);
			method.visitInsn(Opcodes.ARRAYLENGTH);
			return Types.integerType();
		}

		private IConstructor newArrayExp(IConstructor type, IConstructor size) {
			expr(size);

			if (!type.getConstructorType().getName().equals("array")) {
				throw new IllegalArgumentException("arg should be an array type");
			}
			newArrayWithSizeOnStack(AST.$getArg(type));
			return type;
		}

		private IConstructor newArrayExp(IConstructor type, IList elems) {
			intConstant(elems.length());
			if (!type.getConstructorType().getName().equals("array")) {
				throw new IllegalArgumentException("arg should be an array type");
			}

			newArrayWithSizeOnStack(AST.$getArg(type));

			int i = 0;
			for (IValue elem : elems) {
				dup();
				intConstant(i++);
				expr((IConstructor) elem);
				arrayStoreExpWithArrayIndexValueOnStack(type);
			}

			return type;
		}

		private void newArrayWithSizeOnStack(IConstructor type) {
			Switch.type0(type,
					(z) -> method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN) ,
					(i) -> method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT) , 
					(s) -> method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_SHORT) , 
					(b) -> method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE) , 
					(c) -> method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_CHAR) ,
					(f) -> method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT) ,
					(d) -> method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE) ,
					(j) -> method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG) , 
					(v) -> { throw new IllegalArgumentException("void array"); }, 
					(c) -> method.visitTypeInsn(Opcodes.ANEWARRAY, AST.$string(AST.$getArg(type))), 
					(a) -> method.visitTypeInsn(Opcodes.ANEWARRAY, AST.$string(AST.$getArg(type))),
					(S) -> method.visitTypeInsn(Opcodes.ANEWARRAY, Signature.stringType)
					);
		}

		private IConstructor ltExp(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			IConstructor type = prepareArguments(lhs, rhs);

			Switch.type0(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFGE, thenPart, elsePart, joinLabel),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFGE, thenPart, elsePart, joinLabel),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFGE, thenPart, elsePart, joinLabel),
					(v) -> { throw new IllegalArgumentException("< on void"); }, 
					(c) -> { throw new IllegalArgumentException("< on class"); }, 
					(a) -> { throw new IllegalArgumentException("< on array"); },
					(S) -> { throw new IllegalArgumentException("< on string"); }
					);
			return Types.booleanType();
		}

		private IConstructor leExp(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFGT, thenPart, elsePart, joinLabel),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFGT, thenPart, elsePart, joinLabel),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFGT, thenPart, elsePart, joinLabel),
					(v) -> { throw new IllegalArgumentException("<= on void"); }, 
					(c) -> { throw new IllegalArgumentException("<= on class"); }, 
					(a) -> { throw new IllegalArgumentException("<= on array"); },
					(a) -> { throw new IllegalArgumentException("<= on string"); }
					);
			return Types.booleanType();
		}

		private IConstructor gtExp(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			IConstructor type = prepareArguments(lhs, rhs);

			Switch.type0(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFLE, thenPart, elsePart, joinLabel),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFLE, thenPart, elsePart, joinLabel),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFLE, thenPart, elsePart, joinLabel),
					(v) -> { throw new IllegalArgumentException("> on void"); }, 
					(c) -> { throw new IllegalArgumentException("> on class"); }, 
					(a) -> { throw new IllegalArgumentException("> on array"); },
					(S) -> { throw new IllegalArgumentException("> on array"); }
					);
			return Types.booleanType();
		}

		private IConstructor geExp(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			IConstructor type = prepareArguments(lhs, rhs);

			Switch.type0(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFLT, thenPart, elsePart, joinLabel),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFLT, thenPart, elsePart, joinLabel),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFLT, thenPart, elsePart, joinLabel),
					(v) -> { throw new IllegalArgumentException(">= on void"); }, 
					(c) -> { throw new IllegalArgumentException(">= on class"); }, 
					(a) -> { throw new IllegalArgumentException(">= on array"); },
					(S) -> { throw new IllegalArgumentException(">= on array"); }
					);
			return Types.booleanType();
		}

		private IConstructor eqExp(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			if (lhs.getConstructorType().getName().equals("null")) {
				return isNullTest(rhs, thenPart, elsePart, joinLabel);
			}
			else if (rhs.getConstructorType().getName().equals("null")) {
				return isNullTest(lhs, thenPart, elsePart, joinLabel);
			}

			IConstructor type = prepareArguments(lhs, rhs);

			Switch.type0(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFNE, thenPart, elsePart, joinLabel),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFNE, thenPart, elsePart, joinLabel),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFNE, thenPart, elsePart, joinLabel),
					(v) -> { throw new IllegalArgumentException(">= on void"); }, 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ACMPNE, thenPart, elsePart, joinLabel), 
					(a) -> invertedConditionalFlow(0, Opcodes.IF_ACMPNE, thenPart, elsePart, joinLabel),
					(S) -> invertedConditionalFlow(0, Opcodes.IF_ACMPNE, thenPart, elsePart, joinLabel)
					);
			return Types.booleanType();
		}

		private IConstructor prepareArguments(IConstructor lhs, IConstructor rhs) {
			IConstructor type = expr(lhs);
			if (type.getConstructorType() != expr(rhs).getConstructorType()) {
				throw new IllegalArgumentException("incomparable types for operator");
			}
			return type;
		}

		@FunctionalInterface
		private static interface Builder<T> { 
			T build();
		}

		/**
		 * The branching work horse compileCondition generates the pattern for conditional
		 * code execution.
		 * 
		 * @param args         first compile the code for the arguments of the condition
		 * @param compare      then decide how to compare the result 
		 * @param opcode       the conditional jump instruction; NB! inverted condition (if you are generating code for EQ then choose NE here!).
		 * @param thenPart     emit code for the thenPart
		 * @param elsePart     emit code for the elsePart
		 * @param joinLabel emit code for what runs after this conditional
		 */
		private void invertedConditionalFlow(int compare, int opcode, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			Label jump = newLabel(tryFinallyNestingLevel);
			Label next = joinLabel == null ? newLabel(tryFinallyNestingLevel) : joinLabel;
			
			if (compare != 0) {
				method.visitInsn(compare);
			}
			
			method.visitJumpInsn(opcode, elsePart != null ? jump : next);
			thenPart.build();
			
			if (elsePart != null) {
				jumpTo(next);
				method.visitLabel(jump);
				elsePart.build();
			}
			
			if (joinLabel == null) {
				method.visitLabel(next);
			}
		}

		private IConstructor isNullTest(IConstructor arg, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			expr(arg);
			invertedConditionalFlow(0, Opcodes.IFNONNULL, thenPart, elsePart, joinLabel);
			return Types.booleanType();
		}

		private IConstructor isNonNullTest(IConstructor arg, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			expr(arg);
			invertedConditionalFlow(0, Opcodes.IFNULL, thenPart, elsePart, joinLabel);
			return Types.booleanType();
		}

		private IConstructor neExp(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, LeveledLabel joinLabel) {
			if (lhs.getConstructorType().getName().equals("null")) {
				return isNonNullTest(rhs, thenPart, elsePart, joinLabel);
			}
			else if (rhs.getConstructorType().getName().equals("null")) {
				return isNonNullTest(lhs, thenPart, elsePart, joinLabel);
			}

			IConstructor type = prepareArguments(lhs, rhs);

			Switch.type0(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFEQ, thenPart, elsePart, joinLabel),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFEQ, thenPart, elsePart, joinLabel),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFEQ, thenPart, elsePart, joinLabel),
					(v) -> { throw new IllegalArgumentException("!= on void"); }, 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, joinLabel), 
					(a) -> invertedConditionalFlow(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, joinLabel),
					(S) -> invertedConditionalFlow(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, joinLabel)
					);

			return Types.booleanType();
		}

		private IConstructor coerceExp(IConstructor from, IConstructor to, IConstructor arg) {
			Switch.type0(from,
					(z) -> coerceFromBool(to, arg),
					(i) -> coerceFromInt(to, arg),
					(s) -> coerceFromShort(to, arg),
					(b) -> coerceFromByte(to, arg),
					(c) -> coerceFromChar(to, arg),
					(f) -> coerceFromFloat(to, arg),
					(d) -> coerceFromDouble(to, arg),
					(l) -> coerceFromLong(to, arg),
					(v) -> failedCoercion("void", to),
					(c) -> coerceFromClass(from, to, arg),
					(a) -> coerceFromArray(from, to, arg), 
					(S) -> coerceFromString(from, to, arg) 
					);
			return to;
		}

		private void coerceFromString(IConstructor from, IConstructor to, IConstructor arg) {
			Switch.type0(to, 
					(z) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "parseBoolean", Signature.stringType, false);
					}, 
					(i) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "I", false);
					}, 
					(s) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "parseShort", "S", false);
					},
					(b) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "parseByte", "B", false);
					}, 
					(c) -> failedCoercion("string", to), 
					(f) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "parseFloat", "F", false);
					}, 
					(d) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "parseDouble", "D", false);
					}, 
					(j) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "parseLong", "J", false);
					}, 
					(v) -> failedCoercion("string", to), 
					(a) -> failedCoercion("string", to),
					(c) -> failedCoercion("reference", to),
					(S) -> { /* identity */ }
					);
		}

		private void coerceFromBool(IConstructor to, IConstructor arg) {
			throw new IllegalArgumentException("Can not coerce " + "bool" + " to " + to.getConstructorType().getName());
		}

		private void failedCoercion(String from, IConstructor to) {
			throw new IllegalArgumentException("Can not coerce " + from + " to " + to.getConstructorType().getName());
		}

		private void coerceFromArray(IConstructor from, IConstructor to, IConstructor arg) {
			Switch.type0(to,
					(z) -> failedCoercion("int", to),
					(i) -> failedCoercion("int", to),
					(s) -> failedCoercion("short", to),
					(b) -> failedCoercion("boolean", to),
					(c) -> failedCoercion("char", to),
					(f) -> failedCoercion("float", to),
					(d) -> failedCoercion("double", to),
					(l) -> failedCoercion("long", to),
					(v) -> failedCoercion("void", to),
					(c) -> failedCoercion("reference", to),
					(a) -> coerceArrayToArray(from, to, arg),
					(S) -> failedCoercion("string", to) // TODO byteArray?
					);
		}

		private void coerceArrayToArray(IConstructor from, IConstructor to, IConstructor arg) {

		}

		private void coerceFromLong(IConstructor to, IConstructor arg) {
			Switch.type0(to,
					(z) -> failedCoercion("boolean", to),
					(i) -> { method.visitInsn(Opcodes.L2I); },
					(s) -> { method.visitInsn(Opcodes.L2I); },
					(b) -> { method.visitInsn(Opcodes.L2I); },
					(c) -> { method.visitInsn(Opcodes.L2I); },
					(f) -> { method.visitInsn(Opcodes.L2F); },
					(d) -> { method.visitInsn(Opcodes.L2D); },
					(l) -> { /* do nothing */ },
					(v) -> { pop(); nullExp(); },
					(c) -> failedCoercion("reference", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromClass(IConstructor from, IConstructor to, IConstructor arg) {
			String cls = AST.$getName(from);

			Switch.type0(to,
					(z) -> {
						if (cls.equals("java/lang/Boolean")) {
							expr(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "booleanValue", "()Z", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(i) -> {
						if (cls.equals("java/lang/Integer")) {
							expr(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "intValue", "()I", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(s) -> {
						if (cls.equals("java/lang/Integer")) {
							expr(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "shortValue", "()S", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(b) -> {
						if (cls.equals("java/lang/Integer")) {
							expr(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "byteValue", "()B", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(c) -> failedCoercion(cls, arg),
					(f) -> {
						if (cls.equals("java/lang/Float")) {
							expr(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Float", "floatValue", "()F", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(d) -> {
						if (cls.equals("java/lang/Double")) {
							expr(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Double", "doubleValue", "()D", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(l) -> {
						if (cls.equals("java/lang/Long")) {
							expr(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Long", "longValue", "()L", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(v) -> { pop(); nullExp(); },
					(c) -> {
						if (cls.equals(AST.$getName(to))) {
							/* do nothing */
						}
						else {
							failedCoercion("reference", to);
						}
					},
					(a) -> failedCoercion("array", to),
					(S) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromDouble(IConstructor to, IConstructor arg) {
			Switch.type0(to,
					(z) -> failedCoercion("boolean", to),
					(i) -> { method.visitInsn(Opcodes.D2I); },
					(s) -> { method.visitInsn(Opcodes.D2I); },
					(b) -> { method.visitInsn(Opcodes.D2I); },
					(c) -> { method.visitInsn(Opcodes.D2I); },
					(f) -> { method.visitInsn(Opcodes.D2F); },
					(d) -> { /* do nothing */ },
					(l) -> { method.visitInsn(Opcodes.D2L); } ,
					(v) -> { pop(); nullExp(); },
					(c) -> failedCoercion("reference", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromFloat(IConstructor to, IConstructor arg) {
			Switch.type0(to,
					(z) -> failedCoercion("boolean", to),
					(i) -> { method.visitInsn(Opcodes.F2I); },
					(s) -> { method.visitInsn(Opcodes.F2I); },
					(b) -> { method.visitInsn(Opcodes.F2I); },
					(c) -> { method.visitInsn(Opcodes.F2I); },
					(f) -> { /* do nothing */ },
					(d) -> { method.visitInsn(Opcodes.F2D); },
					(l) -> { method.visitInsn(Opcodes.F2L); },
					(v) -> { pop(); nullExp(); },
					(c) -> failedCoercion("reference", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromChar(IConstructor to, IConstructor arg) {
			Switch.type0(to,
					(z) -> failedCoercion("boolean", to),
					(i) -> { /* do nothing */ },
					(s) -> { /* do nothing */ },
					(b) -> { /* do nothing */ },
					(c) -> { /* do nothing */ },
					(f) -> { method.visitInsn(Opcodes.I2F); },
					(d) -> { method.visitInsn(Opcodes.I2D); },
					(l) -> { method.visitInsn(Opcodes.I2L); },
					(v) -> { pop(); nullExp(); },
					(c) -> failedCoercion("reference", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromByte(IConstructor to, IConstructor arg) {
			Switch.type0(to,
					(z) -> failedCoercion("boolean", to),
					(i) -> { /* do nothing */ },
					(s) -> { /* do nothing */ },
					(b) -> { /* do nothing */ },
					(c) -> { /* do nothing */ },
					(f) -> { method.visitInsn(Opcodes.I2F); },
					(d) -> { method.visitInsn(Opcodes.I2D); },
					(l) -> { method.visitInsn(Opcodes.I2L); },
					(v) -> { pop(); nullExp(); },
					(c) -> failedCoercion("reference", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromShort(IConstructor to, IConstructor arg) {
			Switch.type0(to,
					(z) -> failedCoercion("boolean", to),
					(i) -> { /* do nothing */ },
					(s) -> { /* do nothing */ },
					(b) -> { /* do nothing */ },
					(c) -> { /* do nothing */ },
					(f) -> { method.visitInsn(Opcodes.I2F); },
					(d) -> { method.visitInsn(Opcodes.I2D); },
					(l) -> { method.visitInsn(Opcodes.I2L); },
					(v) -> { pop(); nullExp(); },
					(c) -> failedCoercion("reference", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromInt(IConstructor to, IConstructor arg) {
			Switch.type0(to,
					(z) -> failedCoercion("boolean", to),
					(i) -> { /* do nothing */ },
					(s) -> { /* do nothing */ },
					(b) -> { /* do nothing */ },
					(c) -> { /* do nothing */ },
					(f) -> { method.visitInsn(Opcodes.I2F); },
					(d) -> { method.visitInsn(Opcodes.I2D); },
					(l) -> { method.visitInsn(Opcodes.I2L); },
					(v) -> { pop(); nullExp(); },
					(c) -> failedCoercion("reference", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						expr(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private IConstructor falseExp() {
			method.visitInsn(Opcodes.ICONST_0);
			return Types.booleanType();
		}

		private IConstructor trueExp() {
			method.visitInsn(Opcodes.ICONST_1);
			return Types.booleanType();
		}

		private IConstructor nullExp() {
			method.visitInsn(Opcodes.ACONST_NULL);
			return Types.voidType();
		}

		private IConstructor sblockExp(IList block, IConstructor arg) {
			LeveledLabel blockEnd = newLabel(tryFinallyNestingLevel);
			statements(block, null, null, blockEnd);
			method.visitLabel(blockEnd);
			IConstructor type = expr(arg);
			return type;
		}

		private LeveledLabel newLabel(ArrayList<Builder<?>> level) {
			return new LeveledLabel(level.size());
		}

		private IConstructor instanceofExp(IConstructor arg, String cls) {
			expr(arg);
			method.visitTypeInsn(Opcodes.INSTANCEOF, cls);
			return Types.booleanType();
		}

		private IConstructor getfieldExp(IConstructor receiver, String cls, IConstructor type, String name) {
			expr(receiver);
			method.visitFieldInsn(Opcodes.GETFIELD, cls, name, Signature.type(type));
			return type;
		}

		private IConstructor newInstanceExp(IConstructor exp) {
			expressions(AST.$getArgs(exp));
			IConstructor type = AST.$getClass(exp);
			String cls = AST.$getClassFromType(type, classNode.name);
			String desc = Signature.constructor(AST.$getDesc(exp));
			method.visitTypeInsn(Opcodes.NEW, cls);
			dup();
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, "<init>", desc, false);
			return type;
		}

		private IConstructor aaloadExp(IConstructor array, IConstructor index) {
			IConstructor type = expr(array);
			expr(index);
			Switch.type0(AST.$getArg(type), 
					(b) -> method.visitInsn(Opcodes.BALOAD), 
					(i) -> method.visitInsn(Opcodes.IALOAD), 
					(s) -> method.visitInsn(Opcodes.SALOAD), 
					(b) -> method.visitInsn(Opcodes.BALOAD), 
					(c) -> method.visitInsn(Opcodes.CALOAD), 
					(f) -> method.visitInsn(Opcodes.FALOAD), 
					(d) -> method.visitInsn(Opcodes.DALOAD), 
					(j) -> method.visitInsn(Opcodes.LALOAD), 
					(v) -> { throw new IllegalArgumentException("loading into a void array"); }, 
					(L) -> method.visitInsn(Opcodes.AALOAD), 
					(a) -> method.visitInsn(Opcodes.AALOAD), 
					(s) -> method.visitInsn(Opcodes.AALOAD))
			;
			return AST.$getArg(type);
		}

		private IConstructor getstaticExp(String cls, IConstructor type, String name) {
			method.visitFieldInsn(Opcodes.GETSTATIC, cls, name, Signature.type(type));
			return type;
		}

		private IConstructor invokeSpecialExp(String cls, IConstructor sig, IConstructor receiver, IList args) {
			expr(receiver);
			expressions(args);

			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor invokeVirtualExp(String cls, IConstructor sig, IConstructor receiver, IList args) {
			expr(receiver);
			expressions(args);

			method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor invokeInterfaceExp(String interf, IConstructor sig, IConstructor receiver, IList args) {
			expr(receiver);
			expressions(args);

			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, interf, AST.$getName(sig), Signature.method(sig), true);
			return AST.$getReturn(sig);
		}

		private Void expressions(IList args) {
			if (args.length() == 0) {
				return null;
			}
			else {
				expr((IConstructor) args.get(0));
				expressions(args.delete(0));
			}
			return null;
		}

		private IConstructor invokeStaticExp(String cls, IConstructor sig, IList args) {
			expressions(args);

			method.visitMethodInsn(Opcodes.INVOKESTATIC, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor loadExp(String name) {
			int pos = positionOf(name);
			IConstructor type = variableTypes.get(pos);

			Switch.type(type, pos,
					(z,p) -> method.visitVarInsn(Opcodes.ILOAD, p),
					(i,p) -> method.visitVarInsn(Opcodes.ILOAD, p),
					(s,p) -> method.visitVarInsn(Opcodes.ILOAD, p),
					(b,p) -> method.visitVarInsn(Opcodes.ILOAD, p),
					(c,p) -> method.visitVarInsn(Opcodes.ILOAD, p),
					(f,p) -> method.visitVarInsn(Opcodes.FLOAD, p),
					(d,p) -> method.visitVarInsn(Opcodes.DLOAD, p),
					(l,p) -> method.visitVarInsn(Opcodes.LLOAD, p),
					(v,p) -> { /* void */ },
					(c,p) -> method.visitVarInsn(Opcodes.ALOAD, p),
					(a,p) -> method.visitVarInsn(Opcodes.ALOAD, p),
					(S,p) -> method.visitVarInsn(Opcodes.ALOAD, p)
					);

			return type;
		}

		private void intConstant(int constant) {
			switch (constant) {
			case 0: method.visitInsn(Opcodes.ICONST_0); return;
			case 1: method.visitInsn(Opcodes.ICONST_1); return;
			case 2: method.visitInsn(Opcodes.ICONST_2); return;
			case 3: method.visitInsn(Opcodes.ICONST_3); return;
			case 4: method.visitInsn(Opcodes.ICONST_4); return;
			case 5: method.visitInsn(Opcodes.ICONST_5); return;
			}

			if (constant < Byte.MAX_VALUE) {
				method.visitIntInsn(Opcodes.BIPUSH, constant);
			}
			else if (constant < Short.MAX_VALUE) {
				method.visitIntInsn(Opcodes.SIPUSH, constant);
			}
			else {
				method.visitLdcInsn(new Integer(constant));
			}
		}

		private void longConstant(long constant) {
			if (constant == 0) {
				method.visitInsn(Opcodes.LCONST_0); 
			}
			else if (constant == 1) {
				method.visitInsn(Opcodes.LCONST_1);
			}
			else {
				method.visitLdcInsn(new Long(constant));
			}
		}

		private void stringConstant(String constant) {
			method.visitLdcInsn(constant);
		}

		private void floatConstant(float constant) {
			if (constant == 0) {
				method.visitInsn(Opcodes.FCONST_0); 
			}
			else if (constant == 1) {
				method.visitInsn(Opcodes.FCONST_1);
			}
			else if (constant == 2) { // float has a 2 constant, double does not.
				method.visitInsn(Opcodes.FCONST_2);
			}
			else {
				method.visitLdcInsn(new Float(Float.toString(constant)));
			}
		}

		private void doubleConstant(double constant) {
			if (constant == 0) {
				method.visitInsn(Opcodes.DCONST_0);
			}
			else if (constant == 1) {
				method.visitInsn(Opcodes.DCONST_1);
			}
			else {
				method.visitLdcInsn(new Double(Double.toString(constant)));
			}
		}

		private int positionOf(String name) {
			for (int pos = 0; pos < variableNames.size(); pos++) {
				if (name.equals(variableNames.get(pos))) {
					return pos;
				}
			}

			throw new IllegalArgumentException("name not found: " + name);
		}

		private IConstructor constExp(IConstructor type, IValue constant) {
			Switch.type0(type, 
					(z) -> booleanConstant(AST.$getBooleanConstant(constant)), 
					(i) -> intConstant(AST.$getIntegerConstant(constant)), 
					(s) -> intConstant(AST.$getIntegerConstant(constant)), 
					(b) -> intConstant(AST.$getIntegerConstant(constant)), 
					(c) -> intConstant(AST.$getIntegerConstant(constant)), 
					(f) -> floatConstant(AST.$getFloatConstant(constant)), 
					(d) -> doubleConstant(AST.$getDoubleConstant(constant)), 
					(j) -> longConstant(AST.$getLongConstant(constant)), 
					(v) -> { throw new IllegalArgumentException("void constant"); }, 
					(c) -> { throw new IllegalArgumentException("object constant"); }, 
					(a) -> {
						if (constant instanceof IList) {
							constantArray(AST.$getArg(type), (IList) constant);
						}
						else {
							{ throw new IllegalArgumentException("array constant without list input"); }	
						}
					}, 
					(S) -> stringConstant(AST.$getStringConstant(constant))
					);

			return type;
		}

		private void constantArray(IConstructor type, IList constant) {
			intConstant(constant.length());
			newArrayWithSizeOnStack(type);
			int index = 0;

			for (IValue elem : constant) {
				dup();
				intConstant(index);
				constExp((IConstructor) elem, elem);
				arrayStoreExpWithArrayIndexValueOnStack(type);
			}
		}

		private void booleanConstant(boolean val) {
			if (val) {
				trueExp();
			}
			else {
				falseExp();
			}
		}

		private void dup() {
			method.visitInsn(Opcodes.DUP);
		}

		private void field(ClassNode classNode, IConstructor cons, boolean interf) {
			IWithKeywordParameters<? extends IConstructor> kws = cons.asWithKeywordParameters();
			int access = 0;

			if (interf) {
				access = Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC;
			}
			else if (kws.hasParameter("modifiers")) {
				access = modifiers(AST.$getModifiersParameter(kws));
			}
			else {
				access = Opcodes.ACC_PRIVATE;
			}

			String name = AST.$getName(cons);

			IConstructor type = AST.$getType(cons);
			String signature = Signature.type(type);

			Object value = null;
			if (kws.hasParameter("init")) {
				IConstructor defaultExpr = (IConstructor) kws.getParameter("init");

				if (!AST.$is("const", defaultExpr)) {
					if ((access & Opcodes.ACC_STATIC) != 0) {
						// later code will be generated into the static init block
						staticFieldInitializers.put(name, cons);
					}
					else {
						// later code will be generated into each constructor
						fieldInitializers.put(name, cons);
					}
				}
				else {
					IValue val = AST.$getConstant(defaultExpr);
					value = Switch.type(type, 
							(z) -> ((IBool) val).getValue(), 
							(i) -> ((IInteger) val).intValue(), 
							(s) -> ((IInteger) val).intValue(), 
							(b) -> ((IInteger) val).intValue(), 
							(c) -> ((IInteger) val).intValue(), 
							(f) -> ((IReal) val).floatValue(), 
							(d) -> ((IReal) val).doubleValue(), 
							(l) -> ((IInteger) val).longValue(), 
							(v) -> { throw new IllegalArgumentException("constant void initializer"); }, 
							(c) -> { throw new IllegalArgumentException("constant object initializer"); }, 
							(a) -> { throw new IllegalArgumentException("constant array initializer"); }, 
							(s) -> ((IString) val).getValue());
					// GOTO end of method using `value` initialized to the right constant
				}
			}
			else {
				value = Switch.type(type, 
						(z) -> false, 
						(i) -> 0, 
						(s) -> 0, 
						(b) -> 0, 
						(c) -> 0, 
						(f) -> 0.0f, 
						(d) -> 0.0d, 
						(l) -> 0L, 
						(v) -> null, 
						(c) -> null, 
						(a) -> null, 
						(s) -> null);
			}

			classNode.fields.add(new FieldNode(access, name, signature, null, value));
		}

		private int access(ISet modifiers) {
			for (IValue cons : modifiers) {
				switch (((IConstructor) cons).getName()) {
				case "public": return Opcodes.ACC_PUBLIC;
				case "private": return Opcodes.ACC_PRIVATE;
				case "protected": return Opcodes.ACC_PROTECTED;
				}
			}

			return 0;
		}

		private int modifiers(ISet modifiers) {
			int res = 0;
			for (IValue cons : modifiers) {
				switch (((IConstructor) cons).getName()) {
				case "public": 
					res += Opcodes.ACC_PUBLIC; 
					break;
				case "private": 
					res +=  Opcodes.ACC_PRIVATE; 
					break;
				case "protected": 
					res += Opcodes.ACC_PROTECTED; 
					break;
				case "static": 
					res += Opcodes.ACC_STATIC; 
					break;
				case "final": 
					res += Opcodes.ACC_FINAL; 
					break;
				case "abstract": 
					res += Opcodes.ACC_ABSTRACT; 
					break;
				case "interface": 
					res += Opcodes.ACC_INTERFACE; 
					break;
				case "synchronized":
					res += Opcodes.ACC_SYNCHRONIZED;
					break;
				}
			}

			return res;
		}
	}

	/**
	 * Building mangled signature names from symbolic types
	 */
	public static class Signature {
		public static final String objectName = "java/lang/Object";
		public static final String stringName = "java/lang/String";
		public static final String objectType = "L" + objectName + ";";
		public static final String stringType = "L" + stringName + ";";

		private static String constructor(IConstructor sig) {
			StringBuilder val = new StringBuilder();
			val.append("(");
			for (IValue formal : AST.$getFormals(sig)) {
				val.append(type((IConstructor) formal));
			}
			val.append(")V");
			return val.toString();
		}

		private static String method(IConstructor sig) {
			StringBuilder val = new StringBuilder();
			val.append("(");
			for (IValue formal : AST.$getFormals(sig)) {
				val.append(type((IConstructor) formal));
			}
			val.append(")");
			val.append(type(AST.$getReturn(sig)));
			return val.toString();
		}

		public static String type(IConstructor t) {
			IConstructor type = (IConstructor) t;

			return Switch.type(type,
					(z) -> "Z",
					(i) -> "I",
					(s) -> "S",
					(b) -> "B",
					(c) -> "C",
					(f) -> "F",
					(d) -> "D",
					(j) -> "J",
					(v) -> "V",
					(c) -> "L" + AST.$getName(c).replace('.', '/') + ";",
					(a) -> "[" + type(AST.$getArg(a)),
					(S) -> "Ljava/lang/String;"
					);
		}

		public static Class<?>[] binaryClasses(IList formals, PrintWriter out) throws ClassNotFoundException {
			Class<?>[] result = new Class<?>[formals.length()];
			int i = 0;
			for (IValue elem : formals) {
				result[i++] = binaryClass((IConstructor) elem);
			}
			return result;
		}

		public static Class<?> binaryClass(IConstructor type) throws ClassNotFoundException {
			return Switch.type(type,
					(z) -> boolean.class,
					(i) -> int.class,
					(s) -> short.class,
					(b) -> byte.class,
					(c) -> char.class,
					(f) -> float.class,
					(d) -> double.class,
					(j) -> long.class,
					(v) -> void.class,
					(c) -> forName(AST.$getName(type)),
					(a) -> arrayClass(AST.$getArg(type)),
					(S) -> String.class
					);
		}

		private static Class<?> arrayClass(IConstructor component) {
			try {
				Class<?> elem = binaryClass(component);
				return Array.newInstance(elem, 0).getClass();
			} catch (ClassNotFoundException e) {
				return Object[].class;
			}
		}

		private static Class<?> forName(String name) {
			try {
				return Class.forName(name);
			} catch (ClassNotFoundException e) {
				return Object.class;
			}
		}
	}

	/**
	 * Wrappers to get stuff out of the Class ASTs
	 */
	public static class AST {

		public static int $getKey(IConstructor exp) {
			return ((IInteger) exp.get("key")).intValue();
		}
		
		public static IValue $getConstant(IConstructor exp) {
			return exp.get("constant");
		}

		public static IList $getCases(IConstructor stat) {
			return (IList) stat.get("cases");
		}

		public static IList $getCatch(IConstructor stat) {
			return (IList) stat.get("catch");
		}

		public static IList $getFinally(IConstructor stat) {
			return (IList) stat.get("finally");
		}
		
		public static String $getLabel(IConstructor stat) {
			return ((IString) stat.get("label")).getValue();
		}

		public static boolean $is(String string, IConstructor parameter) {
			return parameter.getConstructorType().getName().equals(string);
		}

		public static IConstructor $getDefault(IConstructor var) {
			IWithKeywordParameters<? extends IConstructor> kws = var.asWithKeywordParameters();

			if (!kws.hasParameter("init")) {
				return null;
			}

			return (IConstructor) kws.getParameter("init");
		}

		public static IList $getNext(IConstructor stat) {
			return (IList) stat.get("next");
		}

		public static IList $getInit(IConstructor stat) {
			return (IList) stat.get("init");
		}

		public static IConstructor $getCondition(IConstructor stat) {
			return (IConstructor) stat.get("condition");
		}

		public static IList $getThenBlock(IConstructor stat) {
			return (IList) stat.get("thenBlock");
		}

		public static IList $getElseBlock(IConstructor stat) {
			return (IList) stat.get("elseBlock");
		}

		public static IConstructor $getSize(IConstructor exp) {
			return (IConstructor) exp.get("size");
		}

		public static IConstructor $getLhs(IConstructor exp) {
			return (IConstructor) exp.get("lhs");
		}

		public static IConstructor $getRhs(IConstructor exp) {
			return (IConstructor) exp.get("rhs");
		}

		public static IConstructor $getFrom(IConstructor exp) {
			return (IConstructor) exp.get("from");
		}

		public static IConstructor $getTo(IConstructor exp) {
			return (IConstructor) exp.get("to");
		}

		public static IConstructor $getValue(IConstructor stat) {
			return (IConstructor) stat.get("value");
		}

		public static IConstructor $getIndex(IConstructor exp) {
			return (IConstructor) exp.get("index");
		}

		public static IConstructor $getArray(IConstructor exp) {
			return (IConstructor) exp.get("array");
		}

		public static IConstructor $getReceiver(IConstructor exp) {
			return (IConstructor) exp.get("receiver");
		}

		public static IConstructor $getReturn(IConstructor sig) {
			return (IConstructor) sig.get("return");
		}

		public static IList $getArgs(IConstructor sig) {
			return (IList) sig.get("args");
		}

		public static IConstructor $getClass(IConstructor parameter) {
			return (IConstructor) parameter.get("class");
		}

		public static String $getClassFromType(IConstructor type, String currentClass) {
			return Switch.type(type, 
					(z) -> "java.lang.Boolean", 
					(i) -> "java.lang.Integer", 
					(s) -> "java.lang.Short", 
					(b) -> "java.lang.Byte",
					(c) -> "java.lang.Character", 
					(f) -> "java.lang.Float", 
					(d) -> "java.lang.Double", 
					(j) -> "java.lang.Long", 
					(v) -> { throw new IllegalArgumentException("can not instantiate void type"); },
					(c) -> {
						String name = AST.$getName(type).replace('.','/');
						if (name.equals("<current>")) {
							name = currentClass;
						}
						return name;
					},
					(a) -> { throw new IllegalArgumentException("can not instantiate array types, use newArray instead of newInstance"); }, 
					(s) -> "java.lang.String");
		}

		public static String $getConstructorName(IValue parameter) {
			return ((IConstructor) parameter).getConstructorType().getName();
		}

		public static String $getName(IConstructor exp) {
			return ((IString) exp.get("name")).getValue();
		}

		public static IConstructor $getType(IConstructor exp) {
			return (IConstructor) exp.get("type");
		}

		public static boolean $getBooleanConstant(IValue parameter) {
			return ((IBool) parameter).getValue();
		}

		public static int $getIntegerConstant(IValue parameter) {
			return ((IInteger) parameter).intValue();
		}

		public static long $getLongConstant(IValue parameter) {
			return ((IInteger) parameter).longValue();
		}

		public static float $getFloatConstant(IValue parameter) {
			return ((IReal) parameter).floatValue();
		}

		public static double $getDoubleConstant(IValue parameter) {
			return ((IReal) parameter).doubleValue();
		}

		public static String $getStringConstant(IValue parameter) {
			return ((IString) parameter).getValue();
		}

		public static boolean $getBoolean(IValue parameter) {
			return ((IBool) parameter).getValue();
		}

		public static double $getDouble(IValue parameter) {
			return ((INumber) parameter).toReal(10).doubleValue();
		}

		public static IList $getStatements(IConstructor block) {
			return (IList) block.get("statements");
		}

		public static IList $getVariables(IConstructor block) {
			return (IList) block.get("variables");
		}

		public static ISet $getModifiersParameter(IWithKeywordParameters<? extends IConstructor> kws) {
			return (ISet) kws.getParameter("modifiers");
		}

		public static IConstructor $getDesc(IConstructor cons) {
			return (IConstructor) cons.get("desc");
		}

		public static IList $getFormals(IConstructor sig) {
			return (IList) sig.get("formals");
		}

		public static IList $getBlock(IConstructor cons) {
			return (IList) cons.get("block");
		}

		public static IList $getMethodsParameter(IWithKeywordParameters<? extends IConstructor> kws) {
			return (IList) kws.getParameter("methods");
		}

		public static IList $getFieldsParameter(IWithKeywordParameters<? extends IConstructor> kws) {
			return (IList) kws.getParameter("fields");
		}

		public static String $getSourceParameter(IWithKeywordParameters<? extends IConstructor> kws) {
			return kws.getParameter("source").toString();
		}

		public static ISet $getModifiers(IConstructor o) {
			return (ISet) o.get("modifiers");
		}

		public static String $getSuper(IWithKeywordParameters<? extends IConstructor> kws) {
			return AST.$getName(((IConstructor) kws.getParameter("super"))).replace('.', '/');
		}

		public static String $string(IValue v) {
			return ((IString) v).getValue();
		}

		public static IValue $getDefaultParameter(IWithKeywordParameters<? extends IConstructor> kws) {
			return kws.getParameter("init");
		}

		public static IConstructor $getArg(IConstructor type) {
			return (IConstructor) type.get("arg");
		}

		public static int $getInc(IConstructor type) {
			return ((IInteger) type.get("inc")).intValue();
		}

		public static int $getVersionCode(IConstructor version) {
			switch (version.getConstructorType().getName()) {
			case "v1_6": return Opcodes.V1_6;
			case "v1_7": return Opcodes.V1_7;
			case "v1_8": return Opcodes.V1_8;
			default:
				throw new IllegalArgumentException(version.toString());
			}
		}

		public static IList $getInterfaces(IWithKeywordParameters<? extends IConstructor> kws) {
			return (IList) kws.getParameter("interfaces");
		}
	}

	public static class Switch {
		/**
		 * Dispatch on a consumer on a type. The idea is to never accidentally forget a type using this higher-order function.
		 * @param type
		 */
		public static void type0(IConstructor type, Consumer<IConstructor> bools, Consumer<IConstructor> ints, Consumer<IConstructor> shorts, Consumer<IConstructor> bytes, Consumer<IConstructor> chars, Consumer<IConstructor> floats, Consumer<IConstructor> doubles, Consumer<IConstructor> longs, Consumer<IConstructor> voids, Consumer<IConstructor> classes, Consumer<IConstructor> arrays, Consumer<IConstructor> strings) {
			switch (AST.$getConstructorName(type)) {
			case "boolean": 
				bools.accept(type);
				break;
			case "integer": 
				ints.accept(type);
				break;
			case "short":
				shorts.accept(type);
				break;
			case "byte":
				bytes.accept(type);
				break;
			case "character":
				chars.accept(type);
				break;
			case "float":
				floats.accept(type);
				break;
			case "double":
				doubles.accept(type);
				break;
			case "long":
				longs.accept(type);
				break;
			case "void" :
				voids.accept(type);
				break;
			case "reference" :
				classes.accept(type);
				break;
			case "array" :
				arrays.accept(type);
				break;
			case "string":
				strings.accept(type);
				break;
			default:
				throw new IllegalArgumentException("type not supported: " + type);
			}
		}

		/**
		 * Dispatch on a function on a type. The idea is to never accidentally forget a type using this higher-order function.
		 * @param type
		 */
		public static <T> T type(IConstructor type, Function<IConstructor, T> bools, Function<IConstructor, T> ints, Function<IConstructor, T> shorts, Function<IConstructor, T> bytes, Function<IConstructor, T> chars, Function<IConstructor, T> floats, Function<IConstructor, T> doubles, Function<IConstructor, T> longs, Function<IConstructor, T> voids, Function<IConstructor, T> classes, Function<IConstructor, T> arrays, Function<IConstructor, T> strings) {
			switch (AST.$getConstructorName(type)) {
			case "boolean" :
				return bools.apply(type);
			case "integer": 
				return ints.apply(type);
			case "short":
				return shorts.apply(type);
			case "byte":
				return bytes.apply(type);
			case "character":
				return chars.apply(type);
			case "float":
				return floats.apply(type);
			case "double":
				return doubles.apply(type);
			case "long":
				return longs.apply(type);
			case "void" :
				return voids.apply(type);
			case "reference" :
				return classes.apply(type);
			case "array" :
				return arrays.apply(type);
			case "string":
				return strings.apply(type);
			default:
				throw new IllegalArgumentException("type not supported: " + type);
			}
		}

		/**
		 * Dispatch a consumer on a type and pass a parameter
		 * @param type
		 */
		public static <T> void type(IConstructor type, T arg,  BiConsumer<IConstructor,T> bools, BiConsumer<IConstructor,T> ints, BiConsumer<IConstructor,T> shorts, BiConsumer<IConstructor,T> bytes, BiConsumer<IConstructor,T> chars, BiConsumer<IConstructor,T> floats, BiConsumer<IConstructor,T> doubles, BiConsumer<IConstructor,T> longs, BiConsumer<IConstructor,T> voids, BiConsumer<IConstructor,T> classes, BiConsumer<IConstructor,T> arrays, BiConsumer<IConstructor, T> strings) {
			switch (AST.$getConstructorName(type)) {
			case "boolean":
				bools.accept(type, arg);
				break;
			case "integer": 
				ints.accept(type, arg);
				break;
			case "short":
				shorts.accept(type, arg);
				break;
			case "byte":
				bytes.accept(type, arg);
				break;
			case "character":
				chars.accept(type, arg);
				break;
			case "float":
				floats.accept(type, arg);
				break;
			case "double":
				doubles.accept(type, arg);
				break;
			case "long":
				longs.accept(type, arg);
				break;
			case "void" :
				voids.accept(type, arg);
				break;
			case "reference" :
				classes.accept(type, arg);
				break;
			case "array" :
				arrays.accept(type, arg);
				break;
			case "string":
				strings.accept(type, arg);
				break;
			default:
				throw new IllegalArgumentException("type not supported: " + type);
			}
		}
	}

	private static class Types {
		private static final TypeFactory tf = TypeFactory.getInstance();
		private static final IValueFactory vf = ValueFactoryFactory.getValueFactory();
		private static final TypeStore store = new TypeStore();
		private static final Type TYPE = tf.abstractDataType(store, "Type");
		private static final Type BOOLEAN = tf.constructor(store, TYPE, "boolean");
		private static final IConstructor BOOL_CONS = vf.constructor(BOOLEAN);
		private static final Type INTEGER = tf.constructor(store, TYPE, "integer");
		private static final IConstructor INTEGER_CONS = vf.constructor(INTEGER);
		private static final Type VOID = tf.constructor(store, TYPE, "void");
		private static final IConstructor VOID_CONS = vf.constructor(VOID);
		private static final Type REF = tf.constructor(store, TYPE, "reference", tf.stringType(), "name");

		static IConstructor booleanType() {
			return BOOL_CONS;
		}

		static IConstructor integerType() {
			return INTEGER_CONS;
		}

		static IConstructor voidType() {
			return VOID_CONS;
		}
		
		static String throwableName() {
			return "java/lang/Throwable";
		}
		
		static IConstructor throwableType() {
			return vf.constructor(REF, vf.string(throwableName()));
		}
	}
	
	private static class LeveledLabel extends Label {
		private final int finallyNestingLevel;
		
		public LeveledLabel(int level) {
			this.finallyNestingLevel = level;
		}
		
		public int getFinallyNestingLevel() {
			return finallyNestingLevel;
		}
	}
}

