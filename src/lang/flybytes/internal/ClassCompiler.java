/*
 * Copyright (c) 2022, NWO-I CWI 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package lang.flybytes.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.uri.classloaders.SourceLocationClassLoader;
import org.rascalmpl.values.IRascalValueFactory;
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
 * Translates flybytes ASTs (see lang::flybytes::Syntax.rsc) directly down to JVM bytecode,
 * using the ASM library.
 */
public class ClassCompiler {
	private final IRascalValueFactory vf;
	private final TypeStore ts;
	private final PrintWriter out;
	private final Mirror mirror;
	private final ClassLoader loader;

	public ClassCompiler(IRascalValueFactory vf, TypeStore store, PrintWriter out, ClassLoader loader) {
		this.vf = vf;
		this.ts = store;
		this.out = out;
		this.mirror = new Mirror(vf, ts, out);
		this.loader = loader;
	}

	public void compileClass(IConstructor cls, ISourceLocation classFile, IBool enableAsserts, IConstructor version, IBool debugMode) {
		try (OutputStream output = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
			ClassVisitor cv = cw;

			new Compile(cv, AST.$getVersionCode(version), debugMode.getValue()).compileClass(cls);

			output.write(cw.toByteArray());
		} 
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(e.getMessage());
		}
	}

	public IMap loadClasses(IList classes, IConstructor prefix, IList classpath, IBool enableAsserts, IConstructor version, IBool debugMode) {
		ClassLoader locLoader = new SourceLocationClassLoader(classpath.append(URIUtil.rootLocation("system")), loader);
		ClassMapLoader l = new ClassMapLoader(locLoader);

		ISourceLocation classFolder = null;

		if (prefix.getConstructorType().getName().equals("just")) {
			classFolder = (ISourceLocation) prefix.get("val");
		}

		for (IValue elem : classes) {
			IConstructor cls = (IConstructor) elem;
			String name = AST.$getName(AST.$getType(cls));

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			ClassVisitor cv = cw;

			new Compile(cv, AST.$getVersionCode(version), debugMode.getValue()).compileClass(cls);
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
			Mirror m = new Mirror(vf, ts, out);
			IMapWriter w = vf.mapWriter();

			for (String name : l) {
				w.put(vf.string(name), m.mirrorClass(name, l.getClass(name)));
			}

			return w.done();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public IValue loadClass(IConstructor cls, IConstructor output, IList classpath, IBool enableAsserts, IConstructor version, IBool debugMode) {
		try {
			ClassLoader locLoader = new SourceLocationClassLoader(classpath.append(URIUtil.rootLocation("system")), getClass().getClassLoader());
			String className = AST.$getName(AST.$getType(cls));
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			ClassVisitor cv = cw;

			new Compile(cv, AST.$getVersionCode(version), debugMode.getValue()).compileClass(cls);

			Class<?> loaded = loadSingleClass(className, cw, locLoader);

			if (output.getConstructorType().getName().equals("just")) {
				ISourceLocation classFile = (ISourceLocation) output.get("val");
				try (OutputStream out = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
					out.write(cw.toByteArray());
				}
				catch (IOException e) {
					RuntimeExceptionFactory.io(vf.string(e.getMessage()), null, null);
				}
			}

			return mirror.mirrorClass(className, loaded);
		} 
		catch (Throwable e) {
			e.printStackTrace(out);
			throw new RuntimeException(e);
		}
	}

	public IValue val(IValue v) {
		return mirror.mirrorObject(v);
	}

	public IValue array(IConstructor type, IList elems) throws ClassNotFoundException {
		return mirror.mirrorArray(type, elems);
	}

	public IValue array(IConstructor type, IInteger length) throws ClassNotFoundException {
		return mirror.mirrorArray(type, length.intValue());
	}

	public IValue classMirror(IString n, IList classpath) {
		try {
			ClassLoader loader = new SourceLocationClassLoader(classpath, this.loader);
			String name = n.getValue();
			return mirror.mirrorClass(name, loader.loadClass(name));
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(n.getValue());
		}
	}

	private Class<?> loadSingleClass(String className, ClassWriter cw, ClassLoader loader) throws ClassNotFoundException {
		ClassMapLoader l = new ClassMapLoader(loader);
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
		private static final Builder<IConstructor> DONE = () -> { return null; };
		private final ClassVisitor cw;
		private final int version;
		private ArrayList<IConstructor> variableTypes;
		private ArrayList<String> variableNames;
		private ArrayList<IConstructor> variableDefaults;
		private ArrayList<IList> variableAnnotations;
		private boolean hasDefaultConstructor = false;
		private boolean hasStaticInitializer;
		private boolean isInterface;
		private Map<String, IConstructor> fieldInitializers = new HashMap<>();
		private Map<String, IConstructor> staticFieldInitializers = new HashMap<>();
		private LeveledLabel methodStartLabel;
		private LeveledLabel methodEndLabel;
		private MethodNode method;
		private ArrayList<Builder<IConstructor>> tryFinallyNestingLevel = new ArrayList<>();
		private IConstructor classType;
		private ClassNode classNode;
		private final Builder<IConstructor> pushTrue = () -> trueExp();
		private final Builder<IConstructor> pushFalse = () -> falseExp();
		private Map<String, LeveledLabel> labels;
		private Map<String, Label> asmLabels;
		private int currentLine = 0;
		private boolean emittingFinally = false;
		private final boolean debug;

		public Compile(ClassVisitor cw, int version, boolean debug) {
			this.cw = cw;
			this.version = version;
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

			classNode.visitSource(sourceFile(o), null);

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

			if (kws.hasParameter("fields")) {
				fields(classNode, AST.$getFieldsParameter(kws), isInterface, getLineNumber(o, -1));
			}

			if (kws.hasParameter("methods")) {
				methods(classNode, AST.$getMethodsParameter(kws), getLineNumber(o, -1));
			}

			if (!hasDefaultConstructor && !isInterface) {
				generateDefaultConstructor(classNode);
			}

			if (!hasStaticInitializer && !staticFieldInitializers.isEmpty()) {
				staticInitializer(classNode, null, getLineNumber(o, -1));
			}

			if (kws.hasParameter("annotations")) {
				annotations(classNode, AST.$getAnnotations(kws));
			}

			classNode.accept(cw);
		}

		private String sourceFile(IConstructor o) {
			if (o.mayHaveKeywordParameters()) {
				ISourceLocation loc = (ISourceLocation) o.asWithKeywordParameters().getParameter("src");

				if (loc != null) {
					if (!loc.hasLineColumn()) {
						throw new IllegalArgumentException("debug mode requires line/column information on the src fields to weave in line number information.");
					}
					return loc.getPath();
				}
			}

			if (debug) {
				throw new IllegalArgumentException("debug mode is requires src annotation on the class to determine the source file name.");
			}

			return null;
		}

		private LeveledLabel newLabel() {
			return newLabel(tryFinallyNestingLevel);
		}

		private int getLineNumber(IConstructor o, int parentLine) {
			if (o.mayHaveKeywordParameters()) {
				ISourceLocation loc = (ISourceLocation) o.asWithKeywordParameters().getParameter("src");
				if (loc != null && loc.hasLineColumn()) {
					return loc.getBeginLine();
				}
			}

			return parentLine;
		}

		private void annotations(ClassNode cn, IList annotations) {
			annotations((d, v) -> cn.visitAnnotation(d, v), annotations);
		}

		private void annotations(MethodNode mn, IList annotations) {
			annotations((d, v) -> mn.visitAnnotation(d, v), annotations);
		}

		private void annotations(BiFunction<String,Boolean,AnnotationVisitor> cn, IList annotations) {
			for (IValue elem : annotations) {
				annotation(cn, elem);
			}
		}

		private void annotation(BiFunction<String, Boolean, AnnotationVisitor> cn, IValue elem) {
			String retention = AST.$getRetention((IConstructor) elem);
			boolean visible = true;

			switch (retention) {
			case "source" : return;
			case "class" : visible = false;
			case "runtime": visible = true;
			}

			IConstructor anno = (IConstructor) elem;
			String sig = "L" + AST.$getAnnoClass(anno) + ";";
			AnnotationVisitor an = cn.apply(sig, visible);

			if (anno.arity() > 1) {
				annotation(an, (IConstructor) elem);
			}

			an.visitEnd();
		}

		private void annotation(AnnotationVisitor node, IConstructor anno) {
			String name = AST.$getAnnotationName(anno);
			IConstructor type = AST.$getType(anno);
			Object val = object(type, AST.$getVal(anno));
			String descriptor = Signature.type(type);

			Switch.type0(type, 
					(z) -> node.visit(name, val),
					(i) -> node.visit(name, val),
					(s) -> node.visit(name, val),
					(b) -> node.visit(name, val),
					(c) -> node.visit(name, val),
					(f) -> node.visit(name, val),
					(d) -> node.visit(name, val),
					(l) -> node.visit(name, val),
					(v) -> node.visit(name, val),
					(C) -> node.visitEnum(name, descriptor, (String) val),
					(a) -> {
						AnnotationVisitor aav = node.visitArray(name);
						for (int i = 0; i < Array.getLength(val); i++) {
							aav.visit(name, Array.get(val, i));
						}
						aav.visitEnd();
					},
					(S) -> node.visit(name, val)
					);
		}

		Object object(IConstructor type, IValue val) {
			return Switch.type(type, 
					(z) -> Boolean.valueOf(((IBool) val).getValue()), 
					(i) -> Integer.valueOf((int) ((IInteger) val).intValue()), 
					(s) -> Short.valueOf((short) ((IInteger) val).intValue()), 
					(b) -> Byte.valueOf((byte) ((IInteger) val).intValue()),
					(c) -> Character.valueOf((char) ((IInteger) val).intValue()),
					(f) -> Float.valueOf((float) ((IReal) val).floatValue()),
					(d) -> Double.valueOf((double) ((IReal) val).doubleValue()),
					(j) -> Long.valueOf((long) ((IInteger) val).longValue()),
					(v) -> null,
					(C) -> ((IString) val).getValue(), // to support enums 
					(a) -> array(type, val),
					(s)  -> ((IString) val).getValue()
					);
		}

		private Object array(IConstructor type, IValue val) {
			try {
				if (!(val instanceof IList)) {
					throw new IllegalArgumentException("array annotations must be provided as lists");
				}

				IList list = (IList) val;
				Object arr = Array.newInstance(Signature.binaryClass(AST.$getArg(type)), list.length());

				int i = 0;
				for (IValue elem : (IList) val) {
					Array.set(arr, i++, object(AST.$getArg(type), elem));
				}

				return arr;
			}
			catch (ClassNotFoundException e) {
				throw new IllegalArgumentException("could not construct annotation array of " + type + " due to " + e.getMessage(), e);
			}
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
			method.visitMaxs(0, 0);
			method.visitEnd();
			classNode.methods.add(method);
		}

		private void fields(ClassNode classNode, IList fields, boolean interf, int parentLine) {
			for (IValue field : fields) {
				IConstructor cons = (IConstructor) field;
				field(classNode, cons, interf, parentLine);
			}
		}

		private void methods(ClassNode classNode, IList methods, int parentLine) {
			for (IValue field : methods) {
				IConstructor cons = (IConstructor) field;
				if (cons.getConstructorType().getName().equals("static")) {
					staticInitializer(classNode, cons, parentLine);
				}
				else {
					method(classNode, cons, parentLine);
				}
			}
		}

		private void staticInitializer(ClassNode classNode, IConstructor cons, int parentLine) {
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
			variableAnnotations = new ArrayList<>();

			methodStartLabel = new LeveledLabel(0);
			methodEndLabel = new LeveledLabel(0);

			method.visitCode(); 
			method.visitLabel(methodStartLabel);

			staticFieldInitializers(classNode, method, getLineNumber(cons, parentLine));

			IList block = AST.$getBlock(cons);
			if (block != null) {
				statements(block, null, null, methodEndLabel, getLineNumber(cons, parentLine));
			}

			method.visitLabel(methodEndLabel);
			method.visitInsn(Opcodes.RETURN);
			method.visitMaxs(0, 0);
			method.visitEnd();

			classNode.methods.add(method);
		}


		private void method(ClassNode classNode, IConstructor cons, int parentLine) {
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

			// for raw asm labels, we have a different map
			asmLabels = new HashMap<>();

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
				variableAnnotations = new ArrayList<>();

				if (!isStatic) {
					declareVariable(classType, "this", null, false, null, getLineNumber(cons, parentLine));
				}

				methodStartLabel = new LeveledLabel(0);
				methodEndLabel = new LeveledLabel(0);

				method.visitCode(); 
				method.visitLabel(methodStartLabel);


				formalVariables(varFormals, false /* no initialization */);

				if (isConstructor && !fieldInitializers.isEmpty()) {
					fieldInitializers(classNode, method);
				}

				if (AST.$is("procedure", cons)) {
				    instructions(AST.$getInstructions(cons), methodEndLabel, methodEndLabel, methodEndLabel, getLineNumber(cons, parentLine));
				}
				else {
				    statements(AST.$getBlock(cons), methodStartLabel, methodEndLabel, methodEndLabel, getLineNumber(cons, parentLine));
				}

				method.visitLabel(methodEndLabel);

				for (int i = 0; i < variableNames.size(); i++) {
					String varName = variableNames.get(i);

					if (varName == null) {
						continue; // empty slot
					}

					String varType = Signature.type(variableTypes.get(i));

					method.visitLocalVariable(varName, varType, null, methodStartLabel, methodEndLabel, i);

					IList annotations = variableAnnotations.get(i);
					if (annotations != null) {
						final int index = i;
						annotations((s,v) -> 
						method.visitLocalVariableAnnotation(TypeReference.LOCAL_VARIABLE, null, new Label[] {methodStartLabel}, new Label[] {methodEndLabel}, new int[] {index}, s, v), annotations);
					}
				}

				method.visitMaxs(0, 0);
			}

			if (kws.hasParameter("annotations")) {
				annotations(method, (IList) kws.getParameter("annotations"));
			}

			method.visitEnd(); // also needed for abstract methods
			classNode.methods.add(method);
		}

        private void declareVariable(IConstructor type, String name, IConstructor def, boolean alwaysInitialize, IList annotations, int line) {
			int pos = variableNames.size();

			variableTypes.add(type);
			variableNames.add(name);
			variableDefaults.add(def);
			variableAnnotations.add(null);

			String typeName = type.getConstructorType().getName();
			if (typeName.equals("double") || typeName.equals("long")) {
				// doubles and longs take up 2 stack positions
				variableTypes.add(null);
				variableNames.add(null);
				variableDefaults.add(null); 
				variableAnnotations.add(null);
			}

			if (alwaysInitialize) {
				if (def == null) {
					computeDefaultValueForVariable(type, pos);
				}
				else {
					storeStat(name, def, line);
				}
			}
			else {
				if (def != null && (typeName.equals("object") || typeName.equals("array"))) {
					// if somebody passed 'null' as actual parameter and we have something to initialize with here,
					// then we store that into the variable now. flybytes has default parameters!
					loadExp(name, line);
					invertedConditionalFlow(0, Opcodes.IFNONNULL, () -> storeStat(name, def, line), null, null, line);
				}
			}

			return;
		}

		private void fieldInitializers(ClassNode classNode, MethodNode method) {
			for (String field : fieldInitializers.keySet()) {
				IConstructor def = fieldInitializers.get(field);
				IConstructor exp = (IConstructor) def.asWithKeywordParameters().getParameter("init");
				loadExp("this", getLineNumber(exp, -1));
				expr(exp, -1);
				method.visitFieldInsn(Opcodes.PUTFIELD, classNode.name, field, Signature.type(AST.$getType(def)));
			}
		}

		private void staticFieldInitializers(ClassNode classNode, MethodNode method, int parentLine) {
			for (String field : staticFieldInitializers.keySet()) {
				IConstructor def = staticFieldInitializers.get(field);
				IConstructor exp = (IConstructor) def.asWithKeywordParameters().getParameter("init");
				expr(exp, parentLine);
				method.visitFieldInsn(Opcodes.PUTSTATIC, classNode.name, field, Signature.type(AST.$getType(def)));
			}
		}

		private void formalVariables(IList formals, boolean initialize) {
			int i = 0;

			for (IValue elem : formals) {
				final int index = i;
				IConstructor var = (IConstructor) elem;
				IConstructor varType = AST.$getType(var);
				declareVariable(varType, AST.$getName(var), AST.$getDefault(var), initialize, null, getLineNumber(var, -1));

				if (var.asWithKeywordParameters().hasParameter("annotations")) {
					annotation((s,v) -> method.visitParameterAnnotation(index, s, v), (IList) var.asWithKeywordParameters().getParameter("annotations"));
				}
				i++;
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

		private IConstructor statements(IList statements, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int parentLine) {
			int i = 0, len = statements.length();
			for (IValue elem : statements) {
				// generate the label for where the next statement ends, unless this is the last statement, because
				// then we rejoin the context and we have a label for that given in the parameter 'joinLabel'
				LeveledLabel nextLabel = (++i < len) ? newLabel() : joinLabel;
				statement((IConstructor) elem, continueLabel, breakLabel, nextLabel, parentLine);
				if (i < len) {
					method.visitLabel(nextLabel);
				}
			}
			return null;
		}

		private void statement(IConstructor stat, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int parentLine) {
			int line = getLineNumber(stat, parentLine);

			switch (stat.getConstructorType().getName()) {
			case "incr":
				incStat(AST.$getName(stat), AST.$getInc(stat));
				break;
			case "invokeSuper" : 
                invokeSuper(classNode.superName, AST.$getDesc(stat), AST.$getArgs(stat), line);
                break;
			case "decl":
				declStat(stat, joinLabel, line);
				break;
			case "block":
				String blockLabel = stat.asWithKeywordParameters().hasParameter("label") ? ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue() : null;
				blockStat(blockLabel, AST.$getBlock(stat), joinLabel, line);
				break;
			case "do" : 
				doStat((IConstructor) stat.get("exp"), line);
				break;
			case "store" : 
				storeStat(AST.$getName(stat), AST.$getValue(stat), line); 
				break;
			case "astore" :
				aastoreStat(AST.$getArray(stat), AST.$getIndex(stat), AST.$getArg(stat), line);
				break;
			case "putField":
				putFieldStat(AST.$getRefClassFromType(AST.$getClass(stat), classNode.name), AST.$getReceiver(stat), AST.$getType(stat), AST.$getName(stat), AST.$getArg(stat), line);
				break;
			case "putStatic":
				putStaticStat(AST.$getRefClassFromType(AST.$getClass(stat), classNode.name), AST.$getType(stat), AST.$getName(stat), AST.$getArg(stat), line);
				break;
			case "return" : 
				returnStat(stat, line);
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
					ifThenElseStat(AST.$getCondition(stat), AST.$getThenBlock(stat), AST.$getElseBlock(stat), continueLabel, breakLabel, joinLabel, line);
				}
				else {
					assert stat.getConstructorType().getArity() == 2;
					ifStat(AST.$getCondition(stat), AST.$getThenBlock(stat), continueLabel, breakLabel, joinLabel, line);
				}
				break;
			case "for":
				String forLabel = stat.asWithKeywordParameters().hasParameter("label") ? ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue() : null;
				forStat(forLabel, AST.$getInit(stat), AST.$getCondition(stat), AST.$getNext(stat), AST.$getStatements(stat), continueLabel, breakLabel, joinLabel, line);
				break;
			case "while":
				String whileLabel = stat.asWithKeywordParameters().hasParameter("label") ? ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue() : null;
				whileStat(whileLabel, AST.$getCondition(stat), AST.$getBlock(stat), continueLabel, breakLabel, joinLabel, line);
				break;
			case "doWhile":
				String doWhileLabel = stat.asWithKeywordParameters().hasParameter("label") ? ((IString) stat.asWithKeywordParameters().getParameter("label")).getValue() : null;
				doWhileStat(doWhileLabel, AST.$getCondition(stat), AST.$getBlock(stat), continueLabel, breakLabel, joinLabel, line);
				break;
			case "throw":
				throwStat(AST.$getArg(stat), line);
				break;
			case "monitor":
				monitorStat(AST.$getArg(stat), AST.$getBlock(stat), continueLabel, breakLabel, joinLabel, line);
				break;
			case "acquire":
				acquireStat(AST.$getArg(stat), line);
				break;
			case "release":
				releaseStat(AST.$getArg(stat), line);
				break;
			case "try":
				tryStat(AST.$getBlock(stat), AST.$getCatch(stat), continueLabel, breakLabel, joinLabel, line);
				break;
			case "switch":
				String option = stat.asWithKeywordParameters().hasParameter("option") ? ((IConstructor) stat.asWithKeywordParameters().getParameter("option")).getConstructorType().getName() : "lookup";
				switchStat(option, AST.$getArg(stat), AST.$getCases(stat), continueLabel, breakLabel, joinLabel, line);
				break;
			case "asm":
				instructions(AST.$getInstructions(stat), continueLabel, breakLabel, joinLabel, line);
				break;
			}
		}

		private void instructions(IList instructions, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int parentLine) {
			for (IValue elem : instructions) {
				instruction((IConstructor) elem, continueLabel, breakLabel, joinLabel, parentLine);
			}
		}

		private void instruction(IConstructor instr, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int parentLine) {
			switch (instr.getName()) {
			case "exp":
				expr((IConstructor) instr.get("expression"), parentLine);
				break;
			case "stat":
				statement((IConstructor) instr.get("statement"), continueLabel, breakLabel, joinLabel, parentLine);
				break;
			case "NOP": 
				simpleInstruction(Opcodes.NOP);
				break;
			case "ACONST_NULL":
				simpleInstruction(Opcodes.ACONST_NULL);
				break;
			case "ICONST_M1":
				simpleInstruction(Opcodes.ICONST_M1);
				break;
			case "ICONST_0":
				simpleInstruction(Opcodes.ICONST_0);
				break;
			case "ICONST_1":
				simpleInstruction(Opcodes.ICONST_1);
				break;
			case "ICONST_2":
				simpleInstruction(Opcodes.ICONST_2);
				break;
			case "ICONST_3":
				simpleInstruction(Opcodes.ICONST_3);
				break;
			case "ICONST_4":
				simpleInstruction(Opcodes.ICONST_4);
				break;
			case "ICONST_5":
				simpleInstruction(Opcodes.ICONST_5);
				break;
			case "LCONST_0":
				simpleInstruction(Opcodes.LCONST_0);
				break;
			case "LCONST_1":
				simpleInstruction(Opcodes.LCONST_1);
				break;
			case "FCONST_0":
				simpleInstruction(Opcodes.FCONST_0);
				break;
			case "FCONST_1":
				simpleInstruction(Opcodes.FCONST_1);
				break;
			case "FCONST_2":
				simpleInstruction(Opcodes.FCONST_2);
				break;
			case "DCONST_0":
				simpleInstruction(Opcodes.DCONST_0);
				break;
			case "DCONST_1":
				simpleInstruction(Opcodes.DCONST_1);
				break;
			case "IALOAD":
				simpleInstruction(Opcodes.IALOAD);
				break;
			case "LALOAD":
				simpleInstruction(Opcodes.LALOAD);
				break;
			case "FALOAD":
				simpleInstruction(Opcodes.FALOAD);
				break;
			case "DALOAD":
				simpleInstruction(Opcodes.DALOAD);
				break;
			case "AALOAD":
				simpleInstruction(Opcodes.AALOAD);
				break;
			case "BALOAD":
				simpleInstruction(Opcodes.BALOAD);
				break;
			case "CALOAD":
				simpleInstruction(Opcodes.CALOAD);
				break;
			case "SALOAD":
				simpleInstruction(Opcodes.SALOAD);
				break;
			case "IASTORE":
				simpleInstruction(Opcodes.IASTORE);
				break;
			case "LASTORE":
				simpleInstruction(Opcodes.LASTORE);
				break;
			case "FASTORE":
				simpleInstruction(Opcodes.FASTORE);
				break;
			case "DASTORE":
				simpleInstruction(Opcodes.DASTORE);
				break;
			case "AASTORE":
				simpleInstruction(Opcodes.AASTORE);
				break;
			case "BASTORE":
				simpleInstruction(Opcodes.BASTORE);
				break;
			case "CASTORE":
				simpleInstruction(Opcodes.CASTORE);
				break;
			case "SASTORE":
				simpleInstruction(Opcodes.SASTORE);
				break;
			case "POP":
				simpleInstruction(Opcodes.POP);
				break;
			case "POP2":
				simpleInstruction(Opcodes.POP2);
				break;
			case "DUP":
				simpleInstruction(Opcodes.DUP);
				break;
			case "DUP_X1":
				simpleInstruction(Opcodes.DUP_X1);
				break;
			case "DUP_X2":
				simpleInstruction(Opcodes.DUP_X2);
				break;
			case "DUP2":
				simpleInstruction(Opcodes.DUP2);
				break;
			case "DUP2_X1":
				simpleInstruction(Opcodes.DUP2_X1);
				break;
			case "DUP2_X2":
				simpleInstruction(Opcodes.DUP2_X2);
				break;
			case "SWAP":
				simpleInstruction(Opcodes.SWAP);
				break;
			case "IADD":
				simpleInstruction(Opcodes.IADD);
				break;
			case "LADD":
				simpleInstruction(Opcodes.LADD);
				break;
			case "FADD":
				simpleInstruction(Opcodes.FADD);
				break;
			case "DADD":
				simpleInstruction(Opcodes.DADD);
				break;
			case "ISUB":
				simpleInstruction(Opcodes.ISUB);
				break;
			case "LSUB":
				simpleInstruction(Opcodes.LSUB);
				break;
			case "FSUB":
				simpleInstruction(Opcodes.FSUB);
				break;
			case "DSUB":
				simpleInstruction(Opcodes.DSUB);
				break;
			case "IMUL":
				simpleInstruction(Opcodes.IMUL);
				break;
			case "LMUL":
				simpleInstruction(Opcodes.LMUL);
				break;
			case "FMUL":
				simpleInstruction(Opcodes.FMUL);
				break;
			case "DMUL":
				simpleInstruction(Opcodes.DMUL);
				break;
			case "IDIV":
				simpleInstruction(Opcodes.IDIV);
				break;
			case "LDIV":
				simpleInstruction(Opcodes.LDIV);
				break;
			case "FDIV":
				simpleInstruction(Opcodes.FDIV);
				break;
			case "DDIV":
				simpleInstruction(Opcodes.DDIV);
				break;
			case "IREM":
				simpleInstruction(Opcodes.IREM);
				break;
			case "LREM":
				simpleInstruction(Opcodes.LREM);
				break;
			case "FREM":
				simpleInstruction(Opcodes.FREM);
				break;
			case "DREM":
				simpleInstruction(Opcodes.DREM);
				break;
			case "INEG":
				simpleInstruction(Opcodes.INEG);
				break;
			case "LNEG":
				simpleInstruction(Opcodes.LNEG);
				break;
			case "FNEG":
				simpleInstruction(Opcodes.FNEG);
				break;
			case "DNEG":
				simpleInstruction(Opcodes.DNEG);
				break;
			case "ISHL":
				simpleInstruction(Opcodes.ISHL);
				break;
			case "LSHL":
				simpleInstruction(Opcodes.LSHL);
				break;
			case "ISHR":
				simpleInstruction(Opcodes.ISHR);
				break;
			case "LSHR":
				simpleInstruction(Opcodes.LSHR);
				break;
			case "IUSHR":
				simpleInstruction(Opcodes.IUSHR);
				break;
			case "LUSHR":
				simpleInstruction(Opcodes.LUSHR);
				break;
			case "IAND":
				simpleInstruction(Opcodes.IAND);
				break;
			case "LAND":
				simpleInstruction(Opcodes.LAND);
				break;
			case "IOR":
				simpleInstruction(Opcodes.IOR);
				break;
			case "LOR":
				simpleInstruction(Opcodes.LOR);
				break;
			case "IXOR":
				simpleInstruction(Opcodes.IXOR);
				break;
			case "LXOR":
				simpleInstruction(Opcodes.LXOR);
				break;
			case "I2L":
				simpleInstruction(Opcodes.I2L);
				break;
			case "I2F":
				simpleInstruction(Opcodes.I2F);
				break;
			case "I2D":
				simpleInstruction(Opcodes.I2D);
				break;
			case "L2I":
				simpleInstruction(Opcodes.L2I);
				break;
			case "L2F":
				simpleInstruction(Opcodes.L2F);
				break;
			case "L2D":
				simpleInstruction(Opcodes.L2D);
				break;
			case "F2I":
				simpleInstruction(Opcodes.F2I);
				break;
			case "F2L":
				simpleInstruction(Opcodes.F2L);
				break;
			case "F2D":
				simpleInstruction(Opcodes.F2D);
				break;
			case "D2I":
				simpleInstruction(Opcodes.D2I);
				break;
			case "D2L":
				simpleInstruction(Opcodes.D2L);
				break;
			case "D2F":
				simpleInstruction(Opcodes.D2F);
				break;
			case "I2B":
				simpleInstruction(Opcodes.I2B);
				break;
			case "I2C":
				simpleInstruction(Opcodes.I2C);
				break;
			case "I2S":
				simpleInstruction(Opcodes.I2S);
				break;
			case "LCMP":
				simpleInstruction(Opcodes.LCMP);
				break;
			case "FCMPL":
				simpleInstruction(Opcodes.FCMPL);
				break;
			case "FCMPG":
				simpleInstruction(Opcodes.FCMPG);
				break;
			case "DCMPL":
				simpleInstruction(Opcodes.DCMPL);
				break;
			case "DCMPG":
				simpleInstruction(Opcodes.DCMPG);
				break;
			case "IRETURN":
				simpleInstruction(Opcodes.IRETURN);
				break;
			case "LRETURN":
				simpleInstruction(Opcodes.LRETURN);
				break;
			case "FRETURN":
				simpleInstruction(Opcodes.FRETURN);
				break;
			case "DRETURN":
				simpleInstruction(Opcodes.DRETURN);
				break;
			case "ARETURN":
				simpleInstruction(Opcodes.ARETURN);
				break;
			case "RETURN":
				simpleInstruction(Opcodes.RETURN);
				break;
			case "ARRAYLENGTH":
				simpleInstruction(Opcodes.ARRAYLENGTH);
				break;
			case "ATHROW":
				simpleInstruction(Opcodes.ATHROW);
				break;
			case "MONITORENTER":
				simpleInstruction(Opcodes.MONITORENTER);
				break;
			case "MONITOREXIT":
				simpleInstruction(Opcodes.MONITOREXIT);
				break;
			case "ILOAD":
				varInstruction(Opcodes.ILOAD, instr);
				break;
			case "LLOAD":
				varInstruction(Opcodes.LLOAD, instr);
				break;
			case "FLOAD":
				varInstruction(Opcodes.FLOAD, instr);
				break;
			case "DLOAD":
				varInstruction(Opcodes.DLOAD, instr);
				break;
			case "ALOAD":
				varInstruction(Opcodes.ALOAD, instr);
				break;
			case "ISTORE":
				varInstruction(Opcodes.ISTORE, instr);
				break;
			case "LSTORE":
				varInstruction(Opcodes.LSTORE, instr);
				break;
			case "FSTORE":
				varInstruction(Opcodes.FSTORE, instr);
				break;
			case "DSTORE":
				varInstruction(Opcodes.DSTORE, instr);
				break;
			case "ASTORE":
				varInstruction(Opcodes.ASTORE, instr);
				break;
			case "RET":
				varInstruction(Opcodes.RET, instr);
				break;
			case "BIPUSH":
				intInstruction(Opcodes.BIPUSH, instr);
				break;
			case "SIPUSH":
				intInstruction(Opcodes.SIPUSH, instr);
				break;
			case "NEWARRAY":
				newArrayInstruction(instr, parentLine);
				break;
			case "LDC":
				loadConstantInstruction(instr, parentLine);
				break;
			case "IINC":
				iIncInstruction(instr);
				break;
			case "LABEL":
				labelInstruction(instr);
				break;
			case "LINENUMBER":
				lineNumberInstruction(instr);
				break;
			case "IFEQ":
				jumpInstruction(instr, Opcodes.IFEQ);
				break;
			case "IFNE":
				jumpInstruction(instr, Opcodes.IFNE);
				break;
			case "IFLT":
				jumpInstruction(instr, Opcodes.IFLT);
				break;
			case "IFGE":
				jumpInstruction(instr, Opcodes.IFGE);
				break;
			case "IFGT":
				jumpInstruction(instr, Opcodes.IFGT);
				break;
			case "IFLE":
				jumpInstruction(instr, Opcodes.IFLE);
				break;
			case "IF_ICMPEQ":
				jumpInstruction(instr, Opcodes.IF_ICMPEQ);
				break;
			case "IF_ICMPNE":
				jumpInstruction(instr, Opcodes.IF_ICMPNE);
				break;
			case "IF_ICMPLT":
				jumpInstruction(instr, Opcodes.IF_ICMPLT);
				break;
			case "IF_ICMPGE":
				jumpInstruction(instr, Opcodes.IF_ICMPGE);
				break;
			case "IF_ICMPGT":
				jumpInstruction(instr, Opcodes.IF_ICMPGT);
				break;
			case "IF_ICMPLE":
				jumpInstruction(instr, Opcodes.IF_ICMPLE);
				break;
			case "IF_ACMPEQ":
				jumpInstruction(instr, Opcodes.IF_ACMPEQ);
				break;
			case "IF_ACMPNE":
				jumpInstruction(instr, Opcodes.IF_ACMPNE);
				break;
			case "GOTO":
				jumpInstruction(instr, Opcodes.GOTO);
				break;
			case "JSR":
				jumpInstruction(instr, Opcodes.JSR);
				break;
			case "IFNULL":
				jumpInstruction(instr, Opcodes.IFNULL);
				break;
			case "IFNONNULL":
				jumpInstruction(instr, Opcodes.IFNONNULL);
				break;
			case "TABLESWITCH": 
				tableSwitchInstruction(instr);
				break;
			case "LOOKUPSWITCH":
				lookupSwitchInstruction(instr);
				break;
			case "GETSTATIC":
				fieldInstruction(Opcodes.GETSTATIC, instr);
				break;
			case "PUTSTATIC":
				fieldInstruction(Opcodes.PUTSTATIC, instr);
				break;
			case "GETFIELD":
				fieldInstruction(Opcodes.GETFIELD, instr);
				break;
			case "PUTFIELD":
				fieldInstruction(Opcodes.PUTFIELD, instr);
				break;
			case "INVOKEVIRTUAL":
				methodInstruction(Opcodes.INVOKEVIRTUAL, instr);
				break;
			case "INVOKESPECIAL":
				methodInstruction(Opcodes.INVOKESPECIAL, instr);
				break;
			case "INVOKESTATIC":
				methodInstruction(Opcodes.INVOKESTATIC, instr);
				break;
			case "INVOKEINTERFACE":
				methodInstruction(Opcodes.INVOKEINTERFACE, instr);
				break;
			case "NEW":
				typeInstruction(Opcodes.NEW, instr);
				break;
			case "ANEWARRAY":
				typeInstruction(Opcodes.ANEWARRAY, instr);
				break;
			case "CHECKCAST":
				typeInstruction(Opcodes.CHECKCAST, instr);
				break;	
			case "INSTANCEOF":
				typeInstruction(Opcodes.INSTANCEOF, instr);
				break;
			case "MULTIANEWARRAY":
				multiNewArrayInstruction(instr);
				break;
			case "INVOKEDYNAMIC":
				invokeDynamicInstruction(instr);
				break;
			case "LOCALVARIABLE":
				localVariableInstruction(instr);
				break;
								
			}
		}

		private void localVariableInstruction(IConstructor instr) {
			String name = AST.$getName(instr);
			String desc = Signature.type(AST.$getType(instr));
			Label start = getOrCreateAsmLabel(((IString) instr.get("start")).getValue());
			Label end = getOrCreateAsmLabel(((IString) instr.get("end")).getValue());
			int var = ((IInteger) instr.get("var")).intValue();

			method.visitLocalVariable(name, desc, null, start, end, var);
		}

		private void newArrayInstruction(IConstructor instr, int parentLine) {
			newArrayWithSizeOnStack(AST.$getType(instr), parentLine);
		}

		private void loadConstantInstruction(IConstructor instr, int parentLine) {
			constExp(AST.$getType(instr), AST.$getConstant(instr), parentLine);
		}

		private void iIncInstruction(IConstructor instr) {
			method.visitIincInsn(AST.$getVar(instr), AST.$getInc(instr));
		}

		private void labelInstruction(IConstructor instr) {
			Label l = getOrCreateAsmLabel(AST.$getLabel(instr));
			method.visitLabel(l);
		}

		private void lineNumberInstruction(IConstructor instr) {
			Label start = getOrCreateAsmLabel(AST.$getLabel(instr));
			method.visitLineNumber(AST.$getLine(instr), start);
		}

		private void invokeDynamicInstruction(IConstructor instr) {
			IConstructor handle = AST.$getHandle(instr);
			IConstructor dynDesc = AST.$getDesc(instr);
			method.visitInvokeDynamicInsn(AST.$getName(dynDesc), Signature.method(dynDesc), bootstrapHandler(handle), bootstrapArgs(handle));
		}

		private void multiNewArrayInstruction(IConstructor instr) {
			method.visitMultiANewArrayInsn(AST.$getRefClassFromType(AST.$getClass(instr), classNode.name), AST.$getNumDimensions(instr));
		}

		private void typeInstruction(int opcode, IConstructor instr) {
			method.visitTypeInsn(opcode, Signature.type(AST.$getType(instr)));
		}

		private void methodInstruction(int opcode, IConstructor instr) {
			IConstructor cls = AST.$getClass(instr);
			IConstructor desc = AST.$getDesc(instr);

			switch (desc.getName()) {
				case "constructorDesc":
					method.visitMethodInsn(opcode, AST.$getRefClassFromType(cls, classNode.name), "<init>", Signature.constructor(desc), false);
					break;
				case "methodDesc":
					method.visitMethodInsn(opcode, AST.$getRefClassFromType(cls, classNode.name), AST.$getName(desc), Signature.method(desc), AST.$getIsInterface(instr));
					break;
				default:
					throw new IllegalArgumentException("unknown method kind: " + instr);
			}
		}
 
		private void fieldInstruction(int opcode, IConstructor instr) {
			IConstructor cls = AST.$getClass(instr);
			IConstructor type = AST.$getType(instr);

			
			method.visitFieldInsn(opcode, AST.$getRefClassFromType(cls, classNode.name), AST.$getName(instr), Signature.type(type));
		}

		private void lookupSwitchInstruction(IConstructor instr) {
			IList lucases = AST.$getCaseLabels(instr);
			IList lukeys = AST.$getCaseKeys(instr);
			Label[] lulabels = new Label[lucases.length()];
			int[] keys = new int[lucases.length()];
			for (int i = 0; i < lulabels.length; i++) {
				lulabels[i] = getOrCreateAsmLabel(((IString) lucases.get(i)).getValue());
				keys[i] = ((IInteger) lukeys.get(i)).intValue();
			}
			method.visitLookupSwitchInsn(getOrCreateAsmLabel(AST.$getDefaultLabel(instr)), keys, lulabels);
		}

		private void tableSwitchInstruction(IConstructor instr) {
			IList cases = AST.$getCaseLabels(instr);
			Label[] labels = new Label[cases.length()];
			for (int i = 0; i < labels.length; i++) {
				labels[i] = getOrCreateAsmLabel(((IString) cases.get(i)).getValue());
			}
			method.visitTableSwitchInsn(AST.$getMin(instr), AST.$getMax(instr), getOrCreateAsmLabel(AST.$getDefaultLabel(instr)), labels);
		}

		private void simpleInstruction(int opcode) {
			method.visitInsn(opcode);
		}

		private void intInstruction(int opcode, IConstructor instr) {
			method.visitIntInsn(opcode, AST.$getIntVal(instr));
		}

		private void varInstruction(int opcode, IConstructor instr) {
			method.visitVarInsn(opcode, AST.$getVar(instr));
		}

		private void jumpInstruction(IConstructor instr, int opcode) {
			method.visitJumpInsn(opcode, getOrCreateAsmLabel(AST.$getLabel(instr)));
		}

		private Label getOrCreateAsmLabel(String label) {
			Label result = asmLabels.get(label);
			if (result == null) {
				result = new Label();
				asmLabels.put(label, result);
			}
			return result;
		}

		private void releaseStat(IConstructor arg, int parentLine) {
			expr(arg, parentLine);
			method.visitInsn(Opcodes.MONITOREXIT);
		}

		private void acquireStat(IConstructor arg, int parentLine) {
			expr(arg, parentLine);
			method.visitInsn(Opcodes.MONITORENTER);
		}

		private void switchStat(String option, IConstructor arg, IList cases, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			switch (option) {
			case "table":
				tableSwitch(arg, cases, continueLabel, joinLabel, line);
				return;
			case "lookup":
				lookupSwitch(arg, cases, continueLabel, joinLabel, line);
				return;
			case "auto":
				autoSwitch(arg, cases, continueLabel, joinLabel, line);
				return;
			}

			lookupSwitch(arg, cases, continueLabel, joinLabel, line);
			return;
		}

		/**
		 * Since there is a choice, we mimick the Java compilers heuristics here. However, it must 
		 * be said that the JVM JIT compiler may optimize either instruction to use any kind of 
		 * implementations and nowadays it thus best to generate the smallest code using a lookup table. 
		 */
		private void autoSwitch(IConstructor arg, IList cases, LeveledLabel continueLabel, LeveledLabel joinLabel, int line) {
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

			long tableSpaceCost = 4 + (long) (max - min + 1); 
			long tableTimeCost  = 3; 
			long lookupSpaceCost = 3 + 2 * (long) labelCount;
			long lookupTimeCost = labelCount;
			long tableCost = tableSpaceCost + 3 * tableTimeCost;
			long lookupCost = lookupSpaceCost + 3 * lookupTimeCost;

			if (labelCount > 0 && tableCost <= lookupCost) {
				tableSwitch(arg, cases, continueLabel, joinLabel, line);
			}
			else {
				lookupSwitch(arg, cases, continueLabel, joinLabel, line);
			}
		}

		private static class CaseLabel extends Label {
			final int key;

			public CaseLabel(int key) {
				this.key = key;
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
		private void lookupSwitch(IConstructor arg, IList cases, LeveledLabel continueLabel, LeveledLabel joinLabel, int line) {
			ArrayList<CaseLabel> labels = new ArrayList<>();
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
					labels.add(new CaseLabel(AST.$getKey(c)));
				}
			}


			// first put the key value on the stack
			expr(arg, line);

			@SuppressWarnings("unchecked")
			ArrayList<CaseLabel> sorted = (ArrayList<CaseLabel>) labels.clone();
			sorted.sort((a,b) -> Integer.compare(a.key, b.key));

			// here come the handlers, in sorted order
			int[] keyArray = sorted.stream().mapToInt(l->l.key).toArray();
			Label[] labelArray = sorted.toArray(new Label[0]);

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
					method.visitLabel(labels.get(i));
				}

				LeveledLabel endCase = newLabel();
				statements(AST.$getBlock(c), continueLabel, joinLabel /* break will jump beyond the switch */, endCase, getLineNumber(c, line));
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
		 * as to trigger cache misses all the time.
		 */
		private void tableSwitch(IConstructor arg, IList cases, LeveledLabel continueLabel, LeveledLabel joinLabel, int line) {
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

			LeveledLabel defaultLabel = hasDefault ? newLabel() : joinLabel;
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
					labels[AST.$getKey(c) - min] = newLabel();
				}
			}

			// first put the key value on the stack
			expr(arg, line);

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

				LeveledLabel endCase = newLabel();
				statements(AST.$getBlock(c), continueLabel, joinLabel /* break will jump beyond the switch */, endCase, getLineNumber(c, line));
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
		private void tryStat(IList block, IList catches, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			if (block.length() == 0) {
				// JVM can not deal with empty catch ranges anyway
				return;
			}

			Label tryStart = newLabel();
			Label tryEnd = newLabel();
			Label[] handlers = new LeveledLabel[catches.length()];

			String finallyVarName = null;
			Builder<IConstructor> finallyCode = null;

			// produce handler registration for every catch block
			for (int i = 0; i < catches.length(); i++) {
				IConstructor catcher = (IConstructor) catches.get(i);
				boolean isFinally = AST.$is("finally", catcher);
				boolean isLast = i == catches.length() - 1;
				handlers[i] = newLabel();

				if (isLast && isFinally) {
					finallyVarName = "finally:" + UUID.randomUUID();
					declareVariable(Types.throwableType(), finallyVarName, null, false, null, line);
					finallyCode = () -> statements(AST.$getBlock(catcher), breakLabel, continueLabel, joinLabel, line);
					pushFinally(finallyCode);
				}
				else if (isFinally) {
					throw new IllegalArgumentException("finally block should be the last handler");
				}
				else {
					IConstructor exceptionType = AST.$getType(catcher);
					String varName = AST.$getName(catcher);
					declareVariable(exceptionType, varName, null, false, null, line);
				}
			}

			// the try block itself
			method.visitLabel(tryStart);
			statements(block, continueLabel, breakLabel, joinLabel, line);
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
					String clsName = AST.$getRefClassFromType(exceptionType, classNode.name);
					method.visitVarInsn(Opcodes.ASTORE, positionOf(varName));
					statements(AST.$getBlock(catcher), continueLabel, breakLabel, joinLabel, getLineNumber(catcher, line));
					method.visitTryCatchBlock(tryStart, tryEnd, handlers[i], clsName);

					if (!isLast) { // jump over the other handlers
						method.visitJumpInsn(Opcodes.GOTO, joinLabel);
					}
				}
			}
		}

		private Builder<IConstructor> popFinally() {
			return tryFinallyNestingLevel.remove(tryFinallyNestingLevel.size() - 1);
		}

		private boolean pushFinally(Builder<IConstructor> finallyCode) {
			return tryFinallyNestingLevel.add(finallyCode);
		}


		/** 
		 * Generating a monitor block is a matter of wrapping a block in MONITORENTER and MONITOREXIT,
		 * but the existence of jump instructions such as throw, return, break and continue interacts with
		 * this simplicity. We generate two nested try-catch blocks to make sure that in all cases when
		 * the monitored block ends, the monitor is indeed closed using MONITOREXIT.
		 */
		private void monitorStat(IConstructor lock, IList block, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			if (block.isEmpty()) {
				return;
			}

			LeveledLabel startExceptionBlock = newLabel();
			LeveledLabel endExceptionBlock = newLabel();
			LeveledLabel handlerStart = newLabel();
			LeveledLabel handlerEnd = newLabel();

			method.visitTryCatchBlock(startExceptionBlock, endExceptionBlock, handlerStart, null);
			method.visitTryCatchBlock(handlerStart, handlerEnd, handlerStart, null);

			String lockVarName = "$lock:" + UUID.randomUUID().toString();
			Builder<IConstructor> finallyCode = () -> {
				lineNumber(line);
				method.visitVarInsn(Opcodes.ALOAD, positionOf(lockVarName));
				method.visitInsn(Opcodes.MONITOREXIT);	
				return null;
			};

			// compute the lock object
			IConstructor type = expr(lock, line);
			// declare lock object var for later usage in the sinks of the control flow
			declareVariable(type, lockVarName, null, false, null, getLineNumber(lock, line));
			// keep the lock object ready for MONITORENTER:
			dup(); 
			// store lock object var for later use
			method.visitVarInsn(Opcodes.ASTORE, positionOf(lockVarName));
			// enter the monitor using the lock object on the stack
			method.visitInsn(Opcodes.MONITORENTER);

			// the block 
			method.visitLabel(startExceptionBlock);
			// register finally handlers for use by return, continue, break and throw:
			pushFinally(finallyCode);
			// compile the code in the block
			statements(block, continueLabel, breakLabel, endExceptionBlock, line);
			// unregister the finally handler
			popFinally();
			// nothing happened so we can exit the monitor cleanly
			method.visitVarInsn(Opcodes.ALOAD, positionOf(lockVarName));
			method.visitInsn(Opcodes.MONITOREXIT);
			method.visitLabel(endExceptionBlock);

			// end of monitor, jump over the catch block to continue with the rest
			method.visitJumpInsn(Opcodes.GOTO, joinLabel); 

			// an exception happened, exit the monitor, and rethrow the exception
			method.visitLabel(handlerStart);
			method.visitVarInsn(Opcodes.ALOAD, positionOf(lockVarName));
			method.visitInsn(Opcodes.MONITOREXIT); 
			method.visitLabel(handlerEnd);
			method.visitInsn(Opcodes.ATHROW); // rethrow
		}

		private void throwStat(IConstructor arg, int parentLine) {
			expr(arg, parentLine);
			method.visitInsn(Opcodes.ATHROW);
		}

		private void whileStat(String label, IConstructor cond, IList body, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			LeveledLabel testConditional = newLabel();

			if (label != null) {
				labels.put("break:" + label, joinLabel);
				labels.put("continue:" + label, testConditional);
			}

			method.visitLabel(testConditional);
			lineNumber(line, testConditional);

			// deal efficiently with negated conditionals
			int cmpCode = Opcodes.IFEQ;
			if (cond.getConstructorType().getName().equals("neg")) {
				cond = expr(AST.$getArg(cond), line);
				cmpCode = Opcodes.IFNE;
			}

			expr(cond, line);
			invertedConditionalFlow(0, cmpCode, 
					() -> statements(body, testConditional, joinLabel, testConditional, line), 
					() -> jumpTo(joinLabel) /* end of loop */, 
					testConditional, getLineNumber(cond, line));

			jumpTo(testConditional); // this might be superfluous
		}

		private void doWhileStat(String label, IConstructor cond, IList body, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			LeveledLabel nextIteration = newLabel();

			if (label != null) {
				labels.put("break:" + label, joinLabel);
				labels.put("continue:" + label, nextIteration);
			}

			method.visitLabel(nextIteration);

			statements(body, nextIteration, joinLabel, nextIteration, line);

			// deal efficiently with negated conditionals
			int cmpCode = Opcodes.IFEQ;
			if (cond.getConstructorType().getName().equals("neg")) {
				cond = expr(AST.$getArg(cond), line);
				cmpCode = Opcodes.IFNE;
			}

			// while(cond)
			expr(cond, line);
			invertedConditionalFlow(0, cmpCode, 
					() -> jumpTo(nextIteration), 
					null /* end of loop */, 
					joinLabel, getLineNumber(cond, line));
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

		private void blockStat(String label, IList body, LeveledLabel joinLabel, int line) {
			LeveledLabel again = newLabel();

			labels.put("break:" + label, joinLabel);
			labels.put("continue:" + label, again);

			if (label != null) {
				method.visitLabel(getOrCreateAsmLabel(label));
			}
			method.visitLabel(again);
			statements(body, again, joinLabel, joinLabel, line);
		}

		private void declStat(IConstructor stat, LeveledLabel joinLabel, int line) {
			IConstructor def = null;
			IList annotations = null;
			IWithKeywordParameters<? extends IConstructor> kws = stat.asWithKeywordParameters();

			if (kws.hasParameter("init")) {
				def = (IConstructor) kws.getParameter("init");
			}

			if (kws.hasParameter("annotations")) {
				annotations = (IList) kws.getParameter("annotations");
			}

			declareVariable(AST.$getType(stat), AST.$getName(stat), def, true, annotations, getLineNumber(stat, line));
		}

		private void forStat(String label, IList init, IConstructor cond, IList next, IList body, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			LeveledLabel testConditional = newLabel();
			LeveledLabel nextIterationLabel = newLabel();

			if (label != null) {
				labels.put("break:" + label, joinLabel);
				labels.put("continue:" + label, nextIterationLabel);
			}

			statements(init, continueLabel /*outerloop*/, breakLabel /*outerloop*/, testConditional /*start of inner loop*/, line);

			method.visitLabel(testConditional);

			// deal efficiently with negated conditionals
			int cmpCode = Opcodes.IFEQ;
			if (cond.getConstructorType().getName().equals("neg")) {
				cond = expr(AST.$getArg(cond), line);
				cmpCode = Opcodes.IFNE;
			}

			expr(cond, line);
			invertedConditionalFlow(0, cmpCode, 
					() -> statements(body, nextIterationLabel, joinLabel, nextIterationLabel, line), 
					() -> jumpTo(joinLabel) /* end of loop */, 
					nextIterationLabel, getLineNumber(cond, line));

			method.visitLabel(nextIterationLabel);
			LeveledLabel endNext = newLabel();
			statements(next, continueLabel /*outerloop */, breakLabel /*outerloop*/, endNext, line);
			method.visitLabel(endNext);
			jumpTo(testConditional); // this might be superfluous
		}

		private IConstructor jumpTo(Label join) {
			method.visitJumpInsn(Opcodes.GOTO, join);
			return null;
		}

		private void ifStat(IConstructor cond, IList thenBlock, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			ifThenElseStat(cond, thenBlock, null, continueLabel, breakLabel, joinLabel, line);
		}

		private void ifThenElseStat(IConstructor cond, IList thenBlock, IList elseBlock, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			Builder<IConstructor> thenBuilder = () -> statements(thenBlock, continueLabel, breakLabel, joinLabel, line);
			Builder<IConstructor> elseBuilder = elseBlock != null ? () -> statements(elseBlock, continueLabel, breakLabel, joinLabel, line) : DONE;
			ifThenElse(cond, thenBuilder, elseBuilder, continueLabel, breakLabel, joinLabel, line);
		}

		private IConstructor ifThenElse(IConstructor cond, Builder<IConstructor> thenBuilder, Builder<IConstructor> elseBuilder, LeveledLabel continueLabel, LeveledLabel breakLabel, LeveledLabel joinLabel, int line) {
			// here we special case for !=, ==, <=, >=, < and >, because
			// there are special jump instructions for these operators on the JVM and we don't want to push
			// a boolean on the stack and then conditionally have to jump on that boolean again:

			switch (cond.getConstructorType().getName()) {
			case "true":
				return thenBuilder.build();
			case "false":
				return elseBuilder.build();
			case "eq":
				return eqExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel, getLineNumber(cond, line));
			case "ne":
				return neExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel, getLineNumber(cond, line));
			case "le":
				return leExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel, getLineNumber(cond, line));
			case "gt":
				return gtExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel, getLineNumber(cond, line));
			case "ge":
				return geExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel, getLineNumber(cond, line));
			case "lt":
				return ltExp(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, joinLabel, getLineNumber(cond, line));
			case "neg":
				// if(!expr) is compiled to IFNE directly without intermediate (inefficient) negation code
				expr(AST.$getArg(cond), line);
				return invertedConditionalFlow(0, Opcodes.IFNE, thenBuilder, elseBuilder, joinLabel, getLineNumber(cond, line));
			default:
				expr(cond, line);
				return invertedConditionalFlow(0, Opcodes.IFEQ, thenBuilder, elseBuilder, joinLabel, getLineNumber(cond, line));
			}
		}

		private void putStaticStat(String cls, IConstructor type, String name, IConstructor arg, int parentLine) {
			expr(arg, parentLine);
			method.visitFieldInsn(Opcodes.PUTSTATIC, cls, name, Signature.type(type));
		}

		private void putFieldStat(String cls, IConstructor receiver, IConstructor type, String name, IConstructor arg, int parentLine) {
			expr(receiver, parentLine);
			expr(arg, parentLine);
			method.visitFieldInsn(Opcodes.PUTFIELD, cls, name, Signature.type(type));
		}

		private IConstructor storeStat(String name, IConstructor expression, int parentLine) {
			int pos = positionOf(name);
			expr(expression, parentLine);

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

		private void returnStat(IConstructor stat, int line) {
			if (stat.getConstructorType().getArity() == 0) {
				method.visitInsn(Opcodes.RETURN);
			}
			else {
				IConstructor type = expr(AST.$getArg(stat), line);

				// return, or break or continue from the finally block,
				// must not execute current finally again (infinite loop),
				// so pop that and push it back when done.
				emitFinally(0);

				lineNumber(getLineNumber(stat, line));
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

		private IConstructor doStat(IConstructor exp, int line) {
			IConstructor type = expr(exp, line); 
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

		/**
		 * 
		 * @param exp        the expression to compile
		 * @param parentLine because the _first_ instruction of an expression must be annotated with the right line number, 
		 *                   we have to push line annotations down the expression tree. Otherwise the people generating the code
		 *                   can't simply annotate statements and expect the debugger to work; they'd have to understand which
		 *                   operation will end up to be pushed first on the bytecode stack. This might be quite opaque if you
		 *                   use library macros and other code generation techniques. 
		 * @return
		 */
		private IConstructor expr(IConstructor exp, int parentLine) {
			int line = getLineNumber(exp, parentLine);

			try {
				switch (exp.getConstructorType().getName()) {
				case "const" : 
					return constExp(AST.$getType(exp), AST.$getConstant(exp), line); 
				case "this" : 
					return loadExp("this", line);
				case "newInstance":
					return newInstanceExp(exp, line);
				case "newArray":
					return newArrayExp(AST.$getType(exp), AST.$getSize(exp), line);
				case "newInitArray":
					return newArrayExp(AST.$getType(exp), AST.$getArgs(exp), line);
				case "alength":
					return alengthExp(AST.$getArg(exp), line);
				case "load" : 
					return loadExp(AST.$getName(exp), line); 
				case "aload" :
					return aaloadExp(AST.$getArray(exp), AST.$getIndex(exp), line);
				case "getStatic":
					return getstaticExp(AST.$getRefClassFromType(AST.$getClass(exp), classNode.name), AST.$getType(exp), AST.$getName(exp), line);
				case "invokeVirtual" : 
					return invokeVirtualExp(AST.$getRefClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp), line);
				case "invokeInterface" : 
					return invokeInterfaceExp(AST.$getRefClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp), line);
				case "invokeSpecial" : 
					return invokeSpecialExp(AST.$getRefClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp), line);
				case "invokeStatic" : 
					return invokeStaticExp(AST.$getRefClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getArgs(exp), line);
				case "invokeDynamic" : 
					return invokeDynamicExp(AST.$getHandle(exp), AST.$getDesc(exp), AST.$getArgs(exp), line);
				case "getField":
					return getfieldExp(AST.$getReceiver(exp), AST.$getRefClassFromType(AST.$getClass(exp), classNode.name), AST.$getType(exp), AST.$getName(exp), line);
				case "instanceof":
					return instanceofExp(AST.$getArg(exp), AST.$getRefClassFromType(exp, classNode.name), line);
				case "sblock":
					return sblockExp(AST.$getStatements(exp), AST.$getArg(exp), line);
				case "null":
					lineNumber(line);
					return nullExp(); 
				case "true":
					lineNumber(line);
					return trueExp();
				case "false":
					lineNumber(line);
					return falseExp();
				case "coerce":
					return coerceExp(AST.$getFrom(exp), AST.$getTo(exp), AST.$getArg(exp), line);
				case "eq":
					eqExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null, line);
					return Types.booleanType();
				case "ne":
					neExp(AST.$getLhs(exp), AST.$getRhs(exp), (Builder<IConstructor>) pushTrue, (Builder<IConstructor>) pushFalse, null, line);
					return Types.booleanType();
				case "le":
					leExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null, line);
					return Types.booleanType();
				case "gt":
					gtExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null, line);
					return Types.booleanType();
				case "ge":
					geExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null, line);
					return Types.booleanType();
				case "lt":
					ltExp(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, null, line);
					return Types.booleanType();
				case "add":
					return addExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "div":
					return divExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "rem":
					return remExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "sub":
					return subExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "mul":
					return mulExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "and":
					return andExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "sand":
					return cond(AST.$getLhs(exp), AST.$getRhs(exp), falseExp(), line);
				case "or":
					return orExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "sor":
					return cond(AST.$getLhs(exp), trueExp(), AST.$getRhs(exp), line);
				case "xor":
					return xorExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "neg":
					return negExp(AST.$getArg(exp), line);
				case "inc":
					return incExp(AST.$getName(exp), AST.$getInc(exp), line);
				case "shr":
					return shrExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "shl":
					return shlExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "ushr":
					return ushrExp(AST.$getLhs(exp), AST.$getRhs(exp), line);
				case "checkcast":
					return checkCastExp(AST.$getArg(exp), AST.$getType(exp), line);
				case "cond":
					return cond(AST.$getCondition(exp), AST.$getThenExp(exp), AST.$getElseExp(exp), line);
				default: 
					throw new IllegalArgumentException("unknown expression: " + exp);                                     
				}
			}
			finally {

			}
		}

		private IConstructor cond(IConstructor cond, IConstructor thenExp, IConstructor elseExp, int line) {
			LeveledLabel joinLabel = newLabel();
			IConstructor res = ifThenElse(cond, () -> expr(thenExp, line), () -> expr(elseExp, line), null, null, joinLabel, line);
			method.visitLabel(joinLabel);
			return res;
		}

		private IConstructor shlExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareShiftArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor prepareShiftArguments(IConstructor lhs, IConstructor rhs, int parentLine) {
			IConstructor type = expr(lhs, parentLine);
			if (expr(rhs, parentLine).getConstructorType() != Types.integerType().getConstructorType()) {
				throw new IllegalArgumentException("shift should get an integer as second parameter");
			}
			return type;
		}

		private IConstructor ushrExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareShiftArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor shrExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareShiftArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor incExp(String name, int inc, int line) {
			lineNumber(line);
			method.visitIincInsn(positionOf(name), inc);
			loadExp(name, line);
			return Types.integerType();
		}

		private IConstructor addExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor subExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor remExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor divExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor mulExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor andExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor orExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor xorExp(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			lineNumber(line);
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

		private IConstructor negExp(IConstructor arg, int line) {
			IConstructor type = expr(arg, line);
			lineNumber(line);
			Switch.type0(type, 
					(z) -> { 
						// TODO: is there really not a better way to negate a boolean on the JVM?
						Label zeroLabel = newLabel();
						Label contLabel = newLabel();
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

		private void invokeSuper(String superclass, IConstructor sig, IList args, int line) {
			loadExp("this", line);
			expressions(args, line);
			lineNumber(line);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, superclass, "<init>", Signature.constructor(sig), false);
			return;
		}

		private void aastoreStat(IConstructor array, IConstructor index, IConstructor arg, int parentLine) {
			IConstructor type = expr(array, parentLine);
			expr(index, parentLine);
			expr(arg, parentLine);
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

		private IConstructor checkCastExp(IConstructor arg, IConstructor type, int line) {
			String cons = type.getConstructorType().getName();

			expr(arg, line);
			// weird inconsistency in CHECKCAST instruction?
			if (cons == "object") {
				lineNumber(line);
				method.visitTypeInsn(Opcodes.CHECKCAST, AST.$getName(type).replace('.','/'));
			}
			else if (cons == "array") {
				lineNumber(line);
				method.visitTypeInsn(Opcodes.CHECKCAST, Signature.type(type));
			}
			else {
				throw new IllegalArgumentException("can not check cast to " + type);
			}

			return type;
		}

		private IConstructor alengthExp(IConstructor arg, int line) {
			expr(arg, line);
			lineNumber(line);
			method.visitInsn(Opcodes.ARRAYLENGTH);
			return Types.integerType();
		}

		private IConstructor newArrayExp(IConstructor type, IConstructor size, int line) {
			expr(size, line);

			if (!type.getConstructorType().getName().equals("array")) {
				throw new IllegalArgumentException("arg should be an array type");
			}

			newArrayWithSizeOnStack(AST.$getArg(type), line);
			return type;
		}

		private IConstructor newArrayExp(IConstructor type, IList elems, int line) {
			lineNumber(line);

			intConstant(elems.length());
			if (!type.getConstructorType().getName().equals("array")) {
				throw new IllegalArgumentException("arg should be an array type");
			}

			newArrayWithSizeOnStack(AST.$getArg(type), line);

			int i = 0;
			for (IValue elem : elems) {
				dup();
				intConstant(i++);
				expr((IConstructor) elem, line);
				arrayStoreExpWithArrayIndexValueOnStack(AST.$getArg(type));
			}

			return type;
		}

		private void newArrayWithSizeOnStack(IConstructor type, int line) {
			lineNumber(line);

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
					(c) -> method.visitTypeInsn(Opcodes.ANEWARRAY, AST.$getRefClassFromType(type, classNode.name)), 
					(a) -> method.visitTypeInsn(Opcodes.ANEWARRAY, AST.$getRefClassFromType(type, classNode.name)),
					(S) -> method.visitTypeInsn(Opcodes.ANEWARRAY, AST.$getRefClassFromType(type, classNode.name))
					);
		}

		private IConstructor ltExp(IConstructor lhs, IConstructor rhs, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			return Switch.type(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel, line),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel, line), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel, line), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel, line), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGE, thenPart, elsePart, joinLabel, line), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFGE, thenPart, elsePart, joinLabel, line),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFGE, thenPart, elsePart, joinLabel, line),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFGE, thenPart, elsePart, joinLabel, line),
					(v) -> { throw new IllegalArgumentException("< on void"); }, 
					(c) -> { throw new IllegalArgumentException("< on class"); }, 
					(a) -> { throw new IllegalArgumentException("< on array"); },
					(S) -> { throw new IllegalArgumentException("< on string"); }
					);
		}

		private IConstructor leExp(IConstructor lhs, IConstructor rhs, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			return Switch.type(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel, line),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel, line), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel, line), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel, line), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPGT, thenPart, elsePart, joinLabel, line), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFGT, thenPart, elsePart, joinLabel, line),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFGT, thenPart, elsePart, joinLabel, line),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFGT, thenPart, elsePart, joinLabel, line),
					(v) -> { throw new IllegalArgumentException("<= on void"); }, 
					(c) -> { throw new IllegalArgumentException("<= on class"); }, 
					(a) -> { throw new IllegalArgumentException("<= on array"); },
					(a) -> { throw new IllegalArgumentException("<= on string"); }
					);
		}

		private IConstructor gtExp(IConstructor lhs, IConstructor rhs, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			return Switch.type(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel, line),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel, line), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel, line), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel, line), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLE, thenPart, elsePart, joinLabel, line), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFLE, thenPart, elsePart, joinLabel, line),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFLE, thenPart, elsePart, joinLabel, line),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFLE, thenPart, elsePart, joinLabel, line),
					(v) -> { throw new IllegalArgumentException("> on void"); }, 
					(c) -> { throw new IllegalArgumentException("> on class"); }, 
					(a) -> { throw new IllegalArgumentException("> on array"); },
					(S) -> { throw new IllegalArgumentException("> on array"); }
					);
		}

		private IConstructor geExp(IConstructor lhs, IConstructor rhs, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			IConstructor type = prepareArguments(lhs, rhs, line);
			return Switch.type(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel, line),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel, line), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel, line), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel, line), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPLT, thenPart, elsePart, joinLabel, line), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFLT, thenPart, elsePart, joinLabel, line),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFLT, thenPart, elsePart, joinLabel, line),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFLT, thenPart, elsePart, joinLabel, line),
					(v) -> { throw new IllegalArgumentException(">= on void"); }, 
					(c) -> { throw new IllegalArgumentException(">= on class"); }, 
					(a) -> { throw new IllegalArgumentException(">= on array"); },
					(S) -> { throw new IllegalArgumentException(">= on array"); }
					);
		}

		private IConstructor eqExp(IConstructor lhs, IConstructor rhs, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			if (lhs.getConstructorType().getName().equals("null")) {
				return isNullTest(rhs, thenPart, elsePart, joinLabel, line);
			}
			else if (rhs.getConstructorType().getName().equals("null")) {
				return isNullTest(lhs, thenPart, elsePart, joinLabel, line);
			}

			IConstructor type = prepareArguments(lhs, rhs, line);

			return Switch.type(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel, line),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel, line), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel, line), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel, line), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPNE, thenPart, elsePart, joinLabel, line), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFNE, thenPart, elsePart, joinLabel, line),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFNE, thenPart, elsePart, joinLabel, line),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFNE, thenPart, elsePart, joinLabel, line),
					(v) -> { throw new IllegalArgumentException(">= on void"); }, 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ACMPNE, thenPart, elsePart, joinLabel, line), 
					(a) -> invertedConditionalFlow(0, Opcodes.IF_ACMPNE, thenPart, elsePart, joinLabel, line),
					(S) -> invertedConditionalFlow(0, Opcodes.IF_ACMPNE, thenPart, elsePart, joinLabel, line)
					);
		}

		private IConstructor prepareArguments(IConstructor lhs, IConstructor rhs, int line) {
			IConstructor type = expr(lhs, line);
			if (type.getConstructorType() != expr(rhs, line).getConstructorType()) {
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
		 * @param line 
		 */
		private IConstructor invertedConditionalFlow(int compare, int opcode, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			Label jump = newLabel();
			Label next = joinLabel == null ? newLabel() : joinLabel;
			IConstructor res1 = null, res2 = null;

			lineNumber(line);

			if (compare != 0) {
				method.visitInsn(compare);
			}

			method.visitJumpInsn(opcode, elsePart != null ? jump : next);
			res1 = thenPart.build();

			if (elsePart != null) {
				jumpTo(next);
				method.visitLabel(jump);
				res2 = elsePart.build();
			}

			if (joinLabel == null) {
				method.visitLabel(next);
				lineNumber(line, next);
			}

			return merge(res1, res2);
		}

		private IConstructor merge(IConstructor res1, IConstructor res2) {
			if (res1 == null) {
				return res2;
			}
			if (res2 == null) {
				return res1;
			}
			if (AST.$is("void", res1)) {
				return res2;
			}
			if (AST.$is("void", res2)) {
				return res1;
			}
			return res1;
		}

		private IConstructor isNullTest(IConstructor arg, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			expr(arg, line);
			return invertedConditionalFlow(0, Opcodes.IFNONNULL, thenPart, elsePart, joinLabel, line);
		}

		private IConstructor isNonNullTest(IConstructor arg, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			expr(arg, line);
			return invertedConditionalFlow(0, Opcodes.IFNULL, thenPart, elsePart, joinLabel, line);
		}

		private IConstructor neExp(IConstructor lhs, IConstructor rhs, Builder<IConstructor> thenPart, Builder<IConstructor> elsePart, LeveledLabel joinLabel, int line) {
			if (lhs.getConstructorType().getName().equals("null")) {
				return isNonNullTest(rhs, thenPart, elsePart, joinLabel, line);
			}
			else if (rhs.getConstructorType().getName().equals("null")) {
				return isNonNullTest(lhs, thenPart, elsePart, joinLabel, line);
			}

			IConstructor type = prepareArguments(lhs, rhs, line);

			return Switch.type(type, 
					(z) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel, line),
					(i) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel, line), 
					(s) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel, line), 
					(b) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel, line), 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, joinLabel, line), 
					(f) -> invertedConditionalFlow(Opcodes.FCMPG, Opcodes.IFEQ, thenPart, elsePart, joinLabel, line),
					(d) -> invertedConditionalFlow(Opcodes.DCMPG, Opcodes.IFEQ, thenPart, elsePart, joinLabel, line),
					(l) -> invertedConditionalFlow(Opcodes.LCMP, Opcodes.IFEQ, thenPart, elsePart, joinLabel, line),
					(v) -> { throw new IllegalArgumentException("!= on void"); }, 
					(c) -> invertedConditionalFlow(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, joinLabel, line), 
					(a) -> invertedConditionalFlow(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, joinLabel, line),
					(S) -> invertedConditionalFlow(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, joinLabel, line)
					);
		}

		private IConstructor coerceExp(IConstructor from, IConstructor to, IConstructor arg, int line) {
			Switch.type0(from,
					(z) -> coerceFromBool(to, arg, line),
					(i) -> coerceFromInt(to, arg, line),
					(s) -> coerceFromShort(to, arg, line),
					(b) -> coerceFromByte(to, arg, line),
					(c) -> coerceFromChar(to, arg, line),
					(f) -> coerceFromFloat(to, arg, line),
					(d) -> coerceFromDouble(to, arg, line),
					(l) -> coerceFromLong(to, arg, line),
					(v) -> failedCoercion("void", to),
					(c) -> coerceFromClass(from, to, arg, line),
					(a) -> coerceFromArray(from, to, arg, line), 
					(S) -> coerceFromString(from, to, arg, line) 
					);
			return to;
		}

		private void coerceFromString(IConstructor from, IConstructor to, IConstructor arg, int line) {
			lineNumber(line);
			Switch.type0(to, 
					(z) -> {
						expr(arg, line);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "parseBoolean", Signature.stringType, false);
					}, 
					(i) -> {
						expr(arg, line);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "I", false);
					}, 
					(s) -> {
						expr(arg, line);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "parseShort", "S", false);
					},
					(b) -> {
						expr(arg, line);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "parseByte", "B", false);
					}, 
					(c) -> failedCoercion("string", to), 
					(f) -> {
						expr(arg, line);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "parseFloat", "F", false);
					}, 
					(d) -> {
						expr(arg, line);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "parseDouble", "D", false);
					}, 
					(j) -> {
						expr(arg, line);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "parseLong", "J", false);
					}, 
					(v) -> failedCoercion("string", to), 
					(a) -> failedCoercion("string", to),
					(c) -> failedCoercion("object", to),
					(S) -> { /* identity */ }
					);
		}

		private void coerceFromBool(IConstructor to, IConstructor arg, int line) {
			throw new IllegalArgumentException("Can not coerce " + "bool" + " to " + to.getConstructorType().getName());
		}

		private void failedCoercion(String from, IConstructor to) {
			throw new IllegalArgumentException("Can not coerce " + from + " to " + to.getConstructorType().getName());
		}

		private void coerceFromArray(IConstructor from, IConstructor to, IConstructor arg, int line) {
			lineNumber(line);
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
					(c) -> failedCoercion("object", to),
					(a) -> coerceArrayToArray(from, to, arg),
					(S) -> failedCoercion("string", to) // TODO byteArray?
					);
		}

		private void coerceArrayToArray(IConstructor from, IConstructor to, IConstructor arg) {
			// TODO: what are we going to do here?
			failedCoercion(from.toString(), to);
		}

		private void coerceFromLong(IConstructor to, IConstructor arg, int line) {
			expr(arg, line);
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
					(c) -> failedCoercion("object", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromClass(IConstructor from, IConstructor to, IConstructor arg, int line) {
			String cls = AST.$getName(from);
			expr(arg, line);

			Switch.type0(to,
					(z) -> {
						if (cls.equals("java/lang/Boolean")) {
							expr(from, line);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "booleanValue", "()Z", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(i) -> {
						if (cls.equals("java/lang/Integer")) {
							expr(from, line);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "intValue", "()I", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(s) -> {
						if (cls.equals("java/lang/Integer")) {
							expr(from, line);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "shortValue", "()S", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(b) -> {
						if (cls.equals("java/lang/Integer")) {
							expr(from, line);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "byteValue", "()B", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(c) -> failedCoercion(cls, arg),
					(f) -> {
						if (cls.equals("java/lang/Float")) {
							expr(from, line);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Float", "floatValue", "()F", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(d) -> {
						if (cls.equals("java/lang/Double")) {
							expr(from, line);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Double", "doubleValue", "()D", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(l) -> {
						if (cls.equals("java/lang/Long")) {
							expr(from, line);
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
							failedCoercion("object", to);
						}
					},
					(a) -> failedCoercion("array", to),
					(S) -> {
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromDouble(IConstructor to, IConstructor arg, int line) {
			expr(arg, line);

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
					(c) -> failedCoercion("object", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromFloat(IConstructor to, IConstructor arg, int line) {
			expr(arg, line);
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
					(c) -> failedCoercion("object", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromChar(IConstructor to, IConstructor arg, int line) {
			expr(arg, line);
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
					(c) -> failedCoercion("object", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromByte(IConstructor to, IConstructor arg, int line) {
			expr(arg, line);
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
					(c) -> failedCoercion("object", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromShort(IConstructor to, IConstructor arg, int line) {
			expr(arg, line);
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
					(c) -> failedCoercion("object", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromInt(IConstructor to, IConstructor arg, int line) {
			expr(arg, line);
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
					(c) -> failedCoercion("object", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
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

		private IConstructor sblockExp(IList block, IConstructor arg, int line) {
			LeveledLabel blockEnd = newLabel();
			statements(block, null, null, blockEnd, line);
			method.visitLabel(blockEnd);
			IConstructor type = expr(arg, line);
			return type;
		}

		private LeveledLabel newLabel(ArrayList<Builder<IConstructor>> level) {
			return new LeveledLabel(level.size());
		}

		private IConstructor instanceofExp(IConstructor arg, String cls, int line) {
			expr(arg, line);
			lineNumber(line);
			method.visitTypeInsn(Opcodes.INSTANCEOF, cls);
			return Types.booleanType();
		}

		private IConstructor getfieldExp(IConstructor receiver, String cls, IConstructor type, String name, int line) {
			expr(receiver, line);
			lineNumber(line);
			method.visitFieldInsn(Opcodes.GETFIELD, cls, name, Signature.type(type));
			return type;
		}

		private IConstructor newInstanceExp(IConstructor exp, int line) {
			IConstructor type = AST.$getClass(exp);
			String cls = AST.$getRefClassFromType(type, classNode.name);
			String desc = Signature.constructor(AST.$getDesc(exp));
			method.visitTypeInsn(Opcodes.NEW, cls);
			dup();
			expressions(AST.$getArgs(exp), line);
			lineNumber(line);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, "<init>", desc, false);
			return type;
		}

		private IConstructor aaloadExp(IConstructor array, IConstructor index, int line) {
			IConstructor type = expr(array, line);
			expr(index, line);
			lineNumber(line);
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

		private IConstructor getstaticExp(String cls, IConstructor type, String name, int line) {
			lineNumber(line);
			method.visitFieldInsn(Opcodes.GETSTATIC, cls, name, Signature.type(type));
			return type;
		}

		private IConstructor invokeSpecialExp(String cls, IConstructor sig, IConstructor receiver, IList args, int line) {
			expr(receiver, line);
			expressions(args, line);
			lineNumber(line);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor invokeDynamicExp(IConstructor handler, IConstructor sig, IList args, int line) {
			String name = AST.$getName(sig);
			Handle bootstrapper = bootstrapHandler(handler);
			Object[] bArgs = bootstrapArgs(handler);

			// push arguments to the method on the stack, (including the receiver, if its not a static method)
			expressions(args, line);

			lineNumber(line);

			// invoke the dynamic handle (and registeres it as side-effect using the bootstrap method)
			method.visitInvokeDynamicInsn(name, Signature.method(sig), bootstrapper, bArgs);

			return AST.$getReturn(sig);
		}

		private Object[] bootstrapArgs(IConstructor handler) {
			IList args = AST.$getArgs(handler);
			Object[] results = new Object[args.length()];
			Class<?> clzTmp;

			try {
				int i = 0;
				for (IValue elem : args) {
					IConstructor cons = (IConstructor) elem;

					switch(cons.getConstructorType().getName()) {
					case "stringInfo":
						results[i++] = ((IString) cons.get("s")).getValue();
						break;
					case "classInfo":
						results[i++] = Signature.forName(((IString) cons.get("name")).getValue());
						break;
					case "integerInfo":
						results[i++] = ((IInteger) cons.get("i")).intValue();
						break;
					case "longInfo":
						results[i++] = ((IInteger) cons.get("l")).longValue();
						break;
					case "floatInfo":
						results[i++] = ((IReal) cons.get("f")).floatValue();
						break;
					case "doubleInfo":
						results[i++] = ((IReal) cons.get("d")).doubleValue();
						break;
					case "virtualHandle": 
						clzTmp = Class.forName(AST.$getRefClassFromType(AST.$getClass(cons), classNode.name));
						results[i++] = MethodHandles.lookup().findVirtual(clzTmp, AST.$getName(cons), Signature.methodType(AST.$getDesc(cons)));
						break;
					case "specialHandle": 
						clzTmp = Class.forName(AST.$getRefClassFromType(AST.$getClass(cons), classNode.name));
						Class<?> specialTmp = Class.forName(AST.$getRefClassFromType(AST.$getSpecial(cons), classNode.name));
						results[i++] = MethodHandles.lookup().findSpecial(clzTmp, AST.$getName(cons), Signature.methodType(AST.$getDesc(cons)), specialTmp);
						break;
					case "getterHandle": 
						clzTmp = Class.forName(AST.$getRefClassFromType(AST.$getClass(cons), classNode.name));
						results[i++] = MethodHandles.lookup().findGetter(clzTmp, AST.$getName(cons), Signature.binaryClass(AST.$getType(cons)));
						break;
					case "setterHandle": 
						clzTmp = Class.forName(AST.$getRefClassFromType(AST.$getClass(cons), classNode.name));
						results[i++] = MethodHandles.lookup().findSetter(clzTmp, AST.$getName(cons), Signature.binaryClass(AST.$getType(cons)));
						break;
					case "staticGetterHandle": 
						clzTmp = Class.forName(AST.$getRefClassFromType(AST.$getClass(cons), classNode.name));
						results[i++] = MethodHandles.lookup().findStaticGetter(clzTmp, AST.$getName(cons), Signature.binaryClass(AST.$getType(cons)));
						break;
					case "staticSetterHandle": 
						clzTmp = Class.forName(AST.$getRefClassFromType(AST.$getClass(cons), classNode.name));
						results[i++] = MethodHandles.lookup().findStaticSetter(clzTmp, AST.$getName(cons), Signature.binaryClass(AST.$getType(cons)));
						break;
					case "constructorHandle": 	
						clzTmp = Class.forName(AST.$getRefClassFromType(AST.$getClass(cons), classNode.name));
						results[i++] = MethodHandles.lookup().findConstructor(clzTmp, Signature.constructorType(AST.$getDesc(cons)));
						break;
					case "methodTypeInfo":
						results[i++] = Signature.methodType(AST.$getDesc(cons));
						break;
					}
				}
			} catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException | NoSuchFieldException e) {
				throw new IllegalArgumentException("Could not construct the right type for building argument for bootstrap method", e);
			} 

			return results;
		}

		private Handle bootstrapHandler(IConstructor handler) {
			return new Handle(Opcodes.H_INVOKESTATIC,
					AST.$getRefClassFromType(AST.$getClass(handler), classNode.name),
					AST.$getName(handler),
					Signature.method(AST.$getDesc(handler)),
					false
					);
		}

		private IConstructor invokeVirtualExp(String cls, IConstructor sig, IConstructor receiver, IList args, int line) {
			expr(receiver, line);
			expressions(args, line);

			lineNumber(line);
			method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor invokeInterfaceExp(String interf, IConstructor sig, IConstructor receiver, IList args, int line) {
			expr(receiver, line);
			expressions(args, line);
			lineNumber(line);
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, interf, AST.$getName(sig), Signature.method(sig), true);
			return AST.$getReturn(sig);
		}

		private IConstructor expressions(IList args, int parentLine) {
			if (args.length() == 0) {
				return null;
			}
			else {
				expr((IConstructor) args.get(0), parentLine);
				expressions(args.delete(0), parentLine);
			}
			return null;
		}

		private IConstructor invokeStaticExp(String cls, IConstructor sig, IList args, int line) {
			expressions(args, line);
			lineNumber(line);
			method.visitMethodInsn(Opcodes.INVOKESTATIC, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor loadExp(String name, int line) {
			int pos = positionOf(name);
			IConstructor type = variableTypes.get(pos);

			lineNumber(line);

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
				method.visitLdcInsn(Integer.valueOf(constant));
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
				method.visitLdcInsn(Long.valueOf(constant));
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
				method.visitLdcInsn(Float.valueOf(Float.toString(constant)));
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
				method.visitLdcInsn(Double.valueOf(Double.toString(constant)));
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

		private IConstructor constExp(IConstructor type, IValue constant, int line) {
			lineNumber(line);

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
							constantArray(AST.$getArg(type), (IList) constant, line);
						}
						else {
							{ throw new IllegalArgumentException("array constant without list input"); }	
						}
					}, 
					(S) -> stringConstant(AST.$getStringConstant(constant))
					);

			return type;
		}

		private void lineNumber(int line, Label label) {
			if (debug && line != -1) {
				method.visitLineNumber(line, label);
				currentLine = line;
			}
		}

		private void lineNumber(int line) {
			if (debug && line != -1 && line != currentLine) {
				Label label = new Label();
				method.visitLabel(label);
				method.visitLineNumber(line, label);
				currentLine = line;
			}
		}

		private void constantArray(IConstructor type, IList constant, int line) {
			intConstant(constant.length());
			newArrayWithSizeOnStack(type, line);
			int index = 0;

			for (IValue elem : constant) {
				dup();
				intConstant(index);
				constExp((IConstructor) elem, elem, line);
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

		private void field(ClassNode classNode, IConstructor cons, boolean interf, int parentLine) {
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

			FieldNode fieldNode = new FieldNode(access, name, signature, null, value);

			if (kws.hasParameter("annotations")) {
				annotations((a, b) -> fieldNode.visitAnnotation(a, b), (IList) kws.getParameter("annotations"));
			}

			classNode.fields.add(fieldNode);
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

		public static String constructor(IConstructor sig) {
			StringBuilder val = new StringBuilder();
			val.append("(");
			for (IValue formal : AST.$getFormals(sig)) {
				val.append(type((IConstructor) formal));
			}
			val.append(")V");
			return val.toString();
		}

		public static String method(IConstructor sig) {
			StringBuilder val = new StringBuilder();
			val.append("(");
			for (IValue formal : AST.$getFormals(sig)) {
				val.append(type((IConstructor) formal));
			}
			val.append(")");
			val.append(type(AST.$getReturn(sig)));
			return val.toString();
		}

		public static MethodType methodType(IConstructor sig) {
			return MethodType.fromMethodDescriptorString(method(sig), Signature.class.getClassLoader() /* TODO */);
		}

		public static MethodType constructorType(IConstructor sig) {
			return MethodType.fromMethodDescriptorString(constructor(sig), Signature.class.getClassLoader() /* TODO */);
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

		public static Class<?> forName(String name) {
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

		public static int $getMin(IConstructor exp) {
			return ((IInteger) exp.get("min")).intValue();
		}

		public static int $getMax(IConstructor exp) {
			return ((IInteger) exp.get("max")).intValue();
		}

		public static String $getDefaultLabel(IConstructor instr) {
			return ((IString) instr.get("defaultLabel")).getValue(); 
		}

		public static int $getVar(IConstructor exp) {
			return ((IInteger) exp.get("var")).intValue();
		}

		public static int $getNumDimensions(IConstructor exp) {
			return ((IInteger) exp.get("numDimensions")).intValue();
		}

		public static int $getIntVal(IConstructor exp) {
			return ((IInteger) exp.get("val")).intValue();
		}

		public static IList $getInstructions(IConstructor stat) {
			return (IList) stat.get("instructions");
		}

		public static IList $getCaseLabels(IConstructor stat) {
			return (IList) stat.get("labels");
		}

		public static IList $getCaseKeys(IConstructor stat) {
			return (IList) stat.get("keys");
		}

		public static boolean $getIsInterface(IConstructor stat) {
			return ((IBool) stat.get("isInterface")).getValue();
		}

		public static IConstructor $getSpecial(IConstructor cons) {
			return (IConstructor) cons.get("special");
		}

		public static IConstructor $getThenExp(IConstructor exp) {
			return (IConstructor) exp.get("thenExp");
		}

		public static IConstructor $getElseExp(IConstructor exp) {
			return (IConstructor) exp.get("elseExp");
		}

		public static IConstructor $getHandle(IConstructor exp) {
			return (IConstructor) exp.get("handle");
		}

		public static IValue $getVal(IConstructor anno) {
			return anno.get("val");
		}

		public static String $getRetention(IConstructor elem) {
			if (elem.asWithKeywordParameters().hasParameter("retention")) {
				return ((IString) elem.asWithKeywordParameters().getParameter("runtime")).getValue();
			}

			return "class";
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

		public static String $getClassStr(IConstructor parameter) {
			return ((IString) parameter.get("class")).getValue();
		}

		public static String $getRefClassFromType(IConstructor type, String currentClass) {
			return Switch.type(type, 
					(z) -> { throw new IllegalArgumentException("expected object type"); }, 
					(i) -> { throw new IllegalArgumentException("expected object type"); }, 
					(s) -> { throw new IllegalArgumentException("expected object type"); }, 
					(b) -> { throw new IllegalArgumentException("expected object type"); },
					(c) -> { throw new IllegalArgumentException("expected object type"); }, 
					(f) -> { throw new IllegalArgumentException("expected object type"); }, 
					(d) -> { throw new IllegalArgumentException("expected object type"); }, 
					(j) -> { throw new IllegalArgumentException("expected object type"); }, 
					(v) -> { throw new IllegalArgumentException("expected object type"); },
					(c) -> {
						String name = AST.$getName(type).replace('.','/');
						if (name.equals("<current>")) {
							name = currentClass;
						}
						return name;
					},
					(a) -> { throw new IllegalArgumentException("can not instantiate array types, use newArray instead of newInstance"); }, 
					(s) -> "java/lang/String");
		}

		public static String $getConstructorName(IValue parameter) {
			return ((IConstructor) parameter).getConstructorType().getName();
		}

		public static String $getName(IConstructor exp) {
			return ((IString) exp.get("name")).getValue();
		}

		public static String $getAnnotationName(IConstructor exp) {
			IWithKeywordParameters<? extends IConstructor> kws = exp.asWithKeywordParameters();

			if (kws.hasParameter("name")) {
				return ((IString) kws.getParameter("name")).getValue();
			}

			return "value";
		}

		public static String $getAnnoClass(IConstructor exp) {
			return ((IString) exp.get("annoClass")).getValue();
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

		public static IList $getAnnotations(IWithKeywordParameters<? extends IConstructor> kws) {
			return (IList) kws.getParameter("annotations");
		}

		public static IList $getFieldsParameter(IWithKeywordParameters<? extends IConstructor> kws) {
			return (IList) kws.getParameter("fields");
		}

		public static String $getSourceParameter(IWithKeywordParameters<? extends IConstructor> kws) {
			return kws.getParameter("source").toString();
		}

		public static ISet $getModifiers(IConstructor o) {
			return (ISet) o.asWithKeywordParameters().getParameter("modifiers");
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

		public static int $getLine(IConstructor instr) {
			return ((IInteger) instr.get("line")).intValue();
		}

		public static int $getVersionCode(IConstructor version) {
			switch (version.getConstructorType().getName()) {
			case "v1_6": return Opcodes.V1_6;
			case "v1_7": return Opcodes.V1_7;
			case "v1_8": return Opcodes.V1_8;
			case "v9"  : return Opcodes.V9;
			case "v10"  : return Opcodes.V10;
			case "v11"  : return Opcodes.V11;
			case "v12"  : return Opcodes.V12;
			case "v13"  : return Opcodes.V13;
			case "v14"  : return Opcodes.V14;
			case "v15"  : return Opcodes.V15;
			case "v16"  : return Opcodes.V16;
			case "v17"  : return Opcodes.V17;
			case "v18"  : return Opcodes.V18;
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
			case "object" :
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
			case "object" :
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
			case "object" :
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
		private static final Type REF = tf.constructor(store, TYPE, "object", tf.stringType(), "name");

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
