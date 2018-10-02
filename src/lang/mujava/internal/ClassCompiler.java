package lang.mujava.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.ValueFactoryFactory;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
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

	public void compileClass(IConstructor cls, ISourceLocation classFile, IBool enableAsserts, IConstructor version, IEvaluatorContext ctx) {
		this.out = ctx.getStdOut();

		try (OutputStream output = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);
			new Compile(cw, AST.$getVersionCode(version), out).compileClass(cls);

			output.write(cw.toByteArray());
		} catch (Throwable e) {
			// TODO better error handling
			e.printStackTrace(out);
		}
	}

	public IValue loadClass(IConstructor cls, IConstructor output, IList classpath, IBool enableAsserts, IConstructor version, IEvaluatorContext ctx) {
		this.out = ctx.getStdOut();

		try {
			String className = AST.$getName(AST.$getType(cls));
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			new Compile(cw, AST.$getVersionCode(version), out).compileClass(cls);

			Class<?> loaded = loadClass(className, cw);

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
			throw e;
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

	private Class<?> loadClass(String className, ClassWriter cw) {
		Class<?> loaded = new ClassLoader(getClass().getClassLoader()) {
			public Class<?> defineClass(byte[] bytes) {
				return super.defineClass(className, bytes, 0, bytes.length);
			}	
		}.defineClass(cw.toByteArray());

		return loaded;
	}


	/**
	 * The Compile class encapsulates a single run of the muJava -> JVM bytecode compiler
	 * for a single Class definition.
	 */
	private static class Compile {
		private static final IList EMPTYLIST = ValueFactoryFactory.getValueFactory().list();
		private static final Builder<?> DONE = () -> { return null; };
		private final ClassVisitor cw;
		private final int version;
		@SuppressWarnings("unused")
		private final PrintWriter out;
		private IConstructor[] variableTypes;
		private String[] variableNames;
		private IConstructor[] variableDefaults;
		private boolean hasDefaultConstructor = false;
		private boolean hasStaticInitializer;
		private Map<String, IConstructor> fieldInitializers = new HashMap<>();
		private Map<String, IConstructor> staticFieldInitializers = new HashMap<>();
		private int variableCounter;
		private Label scopeStart;
		private Label scopeEnd;
		private MethodNode method;
		private IConstructor classType;
		private ClassNode classNode;
		private final Builder<?> pushTrue = () -> compileTrue();
		private final Builder<?> pushFalse = () -> compileFalse();
		

		public Compile(ClassVisitor cw, int version, PrintWriter out) {
			this.cw = cw;
			this.version = version;
			this.out = out;
		}

		public void compileClass(IConstructor o) {
			classNode = new ClassNode();
			IWithKeywordParameters<? extends IConstructor> kws = o.asWithKeywordParameters();

			classType = AST.$getType(o);
			classNode.version = version;
			classNode.signature = null; /* anything else leads to the class extending itself! */
			classNode.name = AST.$getName(classType);

			if (kws.hasParameter("modifiers")) {
				classNode.access = compileAccessCode(AST.$getModifiers(o));
			}
			else {
				classNode.access = Opcodes.ACC_PUBLIC;
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
					interfaces.add(AST.$string(v).replace('.','/'));
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
				compileFields(classNode, AST.$getFieldsParameter(kws));
			}

			if (kws.hasParameter("methods")) {
				compileMethods(classNode, AST.$getMethodsParameter(kws));
			}

			if (!hasDefaultConstructor) {
				generateDefaultConstructor(classNode);
			}
			
			if (!hasStaticInitializer && !staticFieldInitializers.isEmpty()) {
				compileStaticInitializer(classNode, null);
			}

			classNode.accept(cw);
		}

		private void generateDefaultConstructor(ClassNode cn) {
			method = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
			method.visitCode();
			Label l0 = new Label();
			Label l1 = new Label();
			method.visitLocalVariable("this", "L" + cn.name + ";", null, l0, l1, 0);
			
			// this = new MyClass(...)
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cn.superName, "<init>", "()V", false);
			
			// this.a = blaBla;
			// ...
			compileFieldInitializers(classNode, method);
			
			// return
			method.visitInsn(Opcodes.RETURN);
			method.visitLabel(l1);
			
			method.visitMaxs(1, 1);
			method.visitEnd();
			classNode.methods.add(method);
		}

		private void compileFields(ClassNode classNode, IList fields) {
			for (IValue field : fields) {
				IConstructor cons = (IConstructor) field;
				compileField(classNode, cons);
			}
		}

		private void compileMethods(ClassNode classNode, IList methods) {
			for (IValue field : methods) {
				IConstructor cons = (IConstructor) field;
				if (cons.getConstructorType().getName().equals("static")) {
					compileStaticInitializer(classNode, cons);
				}
				else {
					compileMethod(classNode, cons);
				}
			}
		}

		private void compileStaticInitializer(ClassNode classNode, IConstructor cons) {
			if (hasStaticInitializer) {
				throw new IllegalArgumentException("can only have one static initializer per class");
			}
			else {
				hasStaticInitializer = true;
			}
			
			IConstructor block = cons != null ? AST.$getBlock(cons) : null;
			IList locals = cons != null ? AST.$getVariables(block) : EMPTYLIST;
			
			method = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);

			int slotCount = 2 /* for wide vars*/ * locals.length(); // total number of vars 
			variableTypes = new IConstructor[slotCount];
			variableNames = new String[slotCount];
			variableDefaults = new IConstructor[slotCount];

			scopeStart = new Label();
			scopeEnd = new Label();

			method.visitCode(); 
			method.visitLabel(scopeStart);
			
			compileStaticFieldInitializers(classNode, method);
			
			if (block != null) {
				compileBlock(block);
			}

			method.visitInsn(Opcodes.RETURN);
			method.visitLabel(scopeEnd);
			method.visitMaxs(0, 0);
			method.visitEnd();

			classNode.methods.add(method);
		}
		

		private void compileMethod(ClassNode classNode, IConstructor cons) {
			IWithKeywordParameters<? extends IConstructor> kws = cons.asWithKeywordParameters();
			
			int modifiers = Opcodes.ACC_PUBLIC;
			if (kws.hasParameter("modifiers")) {
				modifiers = compileModifiers(AST.$getModifiersParameter(kws));
			}

			IConstructor sig = AST.$getDesc(cons);
			boolean isConstructor = sig.getConstructorType().getName().equals("constructorDesc");
			String name = isConstructor ? "<init>" : AST.$getName(sig);
			IList sigFormals = AST.$getFormals(sig);
			hasDefaultConstructor |= (isConstructor && sigFormals.isEmpty());
			IList varFormals = AST.$getFormals(cons);
			IConstructor block = AST.$getBlock(cons);
			IList locals = AST.$getVariables(block);

			if (sigFormals.length() != varFormals.length()) {
				throw new IllegalArgumentException("type signature of " + name + " has different number of types (" + sigFormals.length() + ") from formal parameters (" + varFormals.length() + "), see: " + sigFormals + " versus " + varFormals);
			}

			method = new MethodNode(modifiers, name, isConstructor ? Signature.constructor(sig) : Signature.method(sig), null, null);

			boolean isStatic = (modifiers & Opcodes.ACC_STATIC) != 0;

			variableCounter = isStatic ? 0 : 1;
			int slotCount = 2 /* for wide vars*/ 
					      * (varFormals.length() + locals.length()) // total number of vars 
					      + variableCounter /* room for first "this" variable */;
			variableTypes = new IConstructor[slotCount];
			variableNames = new String[slotCount];
			variableDefaults = new IConstructor[slotCount];

			if (!isStatic) {
				variableTypes[0] = classType;
				variableNames[0] = "this";
			}

			scopeStart = new Label();
			scopeEnd = new Label();

			method.visitCode(); 
			method.visitLabel(scopeStart);
			
			if (!isStatic) {
				// generate the variable for the implicit this reference
				method.visitLocalVariable("this", Signature.type(classType), null, scopeStart, scopeEnd, 0);
			}

			compileLocalVariables(varFormals, false /* no initialization */);
			
			if (isConstructor && !fieldInitializers.isEmpty()) {
				compileFieldInitializers(classNode, method);
			}
			
			compileBlock(block);

			method.visitLabel(scopeEnd);
			method.visitMaxs(0, 0);
			method.visitEnd();

			classNode.methods.add(method);
		}

		private void compileFieldInitializers(ClassNode classNode, MethodNode method) {
			for (String field : fieldInitializers.keySet()) {
				IConstructor def = fieldInitializers.get(field);
				IConstructor exp = (IConstructor) def.asWithKeywordParameters().getParameter("default");
				compileExpression_Load("this");
				compileExpression(exp);
				method.visitFieldInsn(Opcodes.PUTFIELD, classNode.name, field, Signature.type(AST.$getType(def)));
			}
		}
		
		private void compileStaticFieldInitializers(ClassNode classNode, MethodNode method) {
			for (String field : staticFieldInitializers.keySet()) {
				IConstructor def = staticFieldInitializers.get(field);
				IConstructor exp = (IConstructor) def.asWithKeywordParameters().getParameter("default");
				compileExpression(exp);
				method.visitFieldInsn(Opcodes.PUTSTATIC, classNode.name, field, Signature.type(AST.$getType(def)));
			}
		}

		private void compileLocalVariables(IList formals, boolean initialize) {
			int startLocals = variableCounter;

			for (IValue elem : formals) {
				IConstructor var = (IConstructor) elem;

				variableTypes[variableCounter] = AST.$getType(var);
				variableNames[variableCounter] = AST.$getName(var);
				variableDefaults[variableCounter] = AST.$getDefault(var);
				method.visitLocalVariable(variableNames[variableCounter], Signature.type(variableTypes[variableCounter]), null, scopeStart, scopeEnd, variableCounter);

				Switch.type0(variableTypes[variableCounter], 
						(z) -> variableCounter++,
						(i) -> variableCounter++,
						(s) -> variableCounter++,
						(b) -> variableCounter++,
						(c) -> variableCounter++,
						(f) -> variableCounter++,
						(d) -> { 
							// doubles take up 2 stack positions
							variableCounter+=2; 
						}, 
						(l) -> { 
							// longs take up 2 stack positions
							variableCounter+=2; 
						}, 
						(v) -> { 
							throw new IllegalArgumentException("void variable"); 
						},
						(c) -> variableCounter++,
						(a) -> variableCounter++,
						(S) -> variableCounter++
						);
			}

			if (initialize) {
				// now all formals and local variables are declared.
				// initializing local variables to avoid hard-to-analyze JVM crashes
				for (int i = startLocals; i < variableTypes.length; i++) {
					if (variableTypes[i] == null) {
						continue;
					}
					
					if (variableDefaults[i] == null) {
						computeDefaultValueForVariable(i);
					}
					else {
						compileStat_Store(variableNames[i], variableDefaults[i]);
					}
				}
			}
		}

		private void computeDefaultValueForVariable(int i) {
			Switch.type(variableTypes[i], i,
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

		private void compileBlock(IConstructor block) {
			compileLocalVariables(AST.$getVariables(block), true);
			compileStatements(AST.$getStatements(block), DONE);
		}

		/**
		 * We use a continuation passing style here, such that when branching
		 * code is generated we can generate the right code in the right place 
		 * without superfluous additional labels and gotos.
		 */
		private Void compileStatements(IList statements, Builder<?> continuation) {
			if (statements.length() == 0) {
				continuation.build();
			}
			else {
				compileStatement(
						(IConstructor) statements.get(0),
						() -> compileStatements(statements.delete(0), continuation)
						);
			}
			return null;
		}

		private void compileStatement(IConstructor stat, Builder<?> continuation) {
			switch (stat.getConstructorType().getName()) {
			case "do" : 
				compileStat_Do((IConstructor) stat.get("exp"));
				continuation.build();
				break;
			case "store" : 
				compileStat_Store(AST.$getName(stat), AST.$getValue(stat)); 
				continuation.build();
				break;
			case "astore" :
				compileStat_AAStore(AST.$getArray(stat), AST.$getIndex(stat), AST.$getArg(stat));
				continuation.build();
				break;
			case "putField":
				compileStat_PutField(AST.$getClassFromType(AST.$getClass(stat), classNode.name), AST.$getReceiver(stat), AST.$getType(stat), AST.$getName(stat), AST.$getArg(stat));
				continuation.build();
				break;
			case "putStatic":
				compileStat_PutStatic(AST.$getClassFromType(AST.$getClass(stat), classNode.name), AST.$getType(stat), AST.$getName(stat), AST.$getArg(stat));
				continuation.build();
				break;
			case "return" : 
				compileStat_Return(stat);
				// dropping the continuation, there is nothing to do after return!
				break;
			case "if":
				if (stat.getConstructorType().getArity() == 3) {
					compileStat_IfThenElse(AST.$getCondition(stat), AST.$getThenBlock(stat), AST.$getElseBlock(stat), continuation);
				}
				else {
					assert stat.getConstructorType().getArity() == 2;
					compileStat_If(AST.$getCondition(stat), AST.$getThenBlock(stat), continuation);
				}
				break;
			case "for":
				compileStat_For(AST.$getInit(stat), AST.$getCondition(stat), AST.$getNext(stat), AST.$getStatements(stat), continuation);
				break;
			}
		}

		private void compileStat_For(IList init, IConstructor cond, IList next, IList body, Builder<?> continuation) {
			compileStatements(init, DONE);
			Label start = new Label();
			Label join = new Label();
			
			// TODO: this can be done better
			method.visitLabel(start);
			if (cond.getConstructorType().getName().equals("neg")) {
				compileExpression(AST.$getArg(cond));
				compileConditionalInverted(0, Opcodes.IFNE, () -> compileStatements(body, DONE), () -> jumpTo(join), DONE);
			}
			else {
				compileExpression(cond);
				compileConditionalInverted(0, Opcodes.IFEQ, () -> compileStatements(body, DONE), () -> jumpTo(join), DONE);
			}
			compileStatements(next, DONE);
			jumpTo(start);
			method.visitLabel(join);
			continuation.build();
		}

		private Void jumpTo(Label join) {
			method.visitJumpInsn(Opcodes.GOTO, join);
			return null;
		}

		private void compileStat_If(IConstructor cond, IList thenBlock, Builder<?> continuation) {
			compileStat_IfThenElse(cond, thenBlock, null, continuation);
		}

		private void compileStat_IfThenElse(IConstructor cond, IList thenBlock, IList elseBlock, Builder<?> continuation) {
			Builder<?> thenBuilder = () -> compileStatements(thenBlock, DONE);
			Builder<?> elseBuilder = elseBlock != null ? () -> compileStatements(elseBlock, DONE) : DONE;

			// here we special case for !=, ==, <=, >=, < and >, null, nonnull, because
			// there are special jump instructions for these operators on the JVM and we don't want to push
			// a boolean on the stack and then conditionally have to jump on that boolean again:
			switch (cond.getConstructorType().getName()) {
			case "true":
				thenBuilder.build();
				continuation.build();
				break;
			case "false":
				elseBuilder.build();
				continuation.build();
				break;
			case "eq":
				compileEq(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				return;
			case "ne":
				compileNe(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				return;
			case "le":
				compileLe(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				return;
			case "gt":
				compileGt(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				return;
			case "ge":
				compileGe(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				return;
			case "lt":
				compileLt(AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				return;
			case "null":
				if (cond.getConstructorType().getArity() != 1) {
					throw new IllegalArgumentException("null check without a parameter");
				}
				compileNull(AST.$getArg(cond), thenBuilder, elseBuilder, continuation);
				return;
			case "nonnull":
				if (cond.getConstructorType().getArity() != 1) {
					throw new IllegalArgumentException("nonnull check without a parameter");
				}
				compileNull(AST.$getArg(cond), thenBuilder, elseBuilder, continuation);
				return;
			case "neg":
				// if(!expr) is compiled to IFNE directly without intermediate (inefficient) negation code
				compileExpression(AST.$getArg(cond));
				compileConditionalInverted(0, Opcodes.IFNE, thenBuilder, elseBuilder, continuation);
				return;
			default:
				compileExpression(cond);
				compileConditionalInverted(0, Opcodes.IFEQ, thenBuilder, elseBuilder, continuation);
				return;
			}
		}

		private void compileStat_PutStatic(String cls, IConstructor type, String name, IConstructor arg) {
			compileExpression(arg);
			method.visitFieldInsn(Opcodes.PUTSTATIC, cls, name, Signature.type(type));
		}

		private void compileStat_PutField(String cls, IConstructor receiver, IConstructor type, String name, IConstructor arg) {
			compileExpression(receiver);
			compileExpression(arg);
			method.visitFieldInsn(Opcodes.PUTFIELD, cls, name, Signature.type(type));
		}

		private void compileStat_Store(String name, IConstructor expression) {
			int pos = positionOf(name);
			compileExpression(expression);

			Switch.type0(variableTypes[pos],
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
		}

		private void compileStat_Return(IConstructor stat) {
			if (stat.getConstructorType().getArity() == 0) {
				method.visitInsn(Opcodes.RETURN);
			}
			else {
				IConstructor type = compileExpression(AST.$getArg(stat));

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

		private Void compileStat_Do(IConstructor exp) {
			IConstructor type = compileExpression(exp); 
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

		private IConstructor compileExpression(IConstructor exp) {
			switch (exp.getConstructorType().getName()) {
			case "const" : 
				return compileExpression_Const(AST.$getType(exp), AST.$getConstant(exp)); 
			case "this" : 
				return compileExpression_Load("this");
			case "newInstance":
				return compileExpression_NewInstance(exp);
			case "newArray":
				if (exp.get(1) instanceof IList) {
					return compileExpression_NewArray(AST.$getType(exp), AST.$getArgs(exp));
				}
				else {
					return compileExpression_NewArraySize(AST.$getType(exp), AST.$getSize(exp));
				}
			case "alength":
				return compileExpression_ALength(AST.$getArg(exp));
			case "load" : 
				return compileExpression_Load(AST.$getName(exp)); 
			case "aload" :
				return compileExpression_AALoad(AST.$getArray(exp), AST.$getIndex(exp));
			case "getStatic":
				return compileGetStatic(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getType(exp), AST.$getName(exp));
			case "invokeVirtual" : 
				return compileInvokeVirtual(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
			case "invokeInterface" : 
				return compileInvokeInterface(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
			case "invokeSpecial" : 
				return compileInvokeSpecial(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
			case "invokeSuper" : 
				return compileInvokeSuper(classNode.superName, AST.$getDesc(exp), AST.$getArgs(exp));
			case "invokeStatic" : 
				return compileInvokeStatic(AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getDesc(exp), AST.$getArgs(exp));
			case "getField":
				return compileGetField(AST.$getReceiver(exp), AST.$getClassFromType(AST.$getClass(exp), classNode.name), AST.$getType(exp), AST.$getName(exp));
			case "instanceof":
				return compileInstanceof(AST.$getArg(exp), AST.$getClassFromType(exp, classNode.name));
			case "block":
				return compileBlock(AST.$getStatements(exp), AST.$getArg(exp));
			case "null":
				if (exp.getConstructorType().getArity() == 0) {
					return compileNull();  // null constant
				}
				else { 
					return compileNull(AST.$getArg(exp), pushTrue, pushFalse, DONE); // null check 
				}
			case "nonnull":
				return compileNonNull(AST.$getArg(exp), pushTrue, pushFalse, DONE); // null check 
			case "true":
				return compileTrue();
			case "false":
				return compileFalse();
			case "coerce":
				return compileCoerce(AST.$getFrom(exp), AST.$getTo(exp), AST.$getArg(exp));
			case "eq":
				return compileEq(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, DONE);
			case "ne":
				return compileNe(AST.$getLhs(exp), AST.$getRhs(exp), (Builder<?>) pushTrue, (Builder<?>) pushFalse, DONE);
			case "le":
				return compileLe(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, DONE);
			case "gt":
				return compileGt(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, DONE);
			case "ge":
				return compileGe(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, DONE);
			case "lt":
				return compileLt(AST.$getLhs(exp), AST.$getRhs(exp), pushTrue, pushFalse, DONE);
			case "add":
				return compileAdd(AST.$getLhs(exp), AST.$getRhs(exp));
			case "div":
				return compileDiv(AST.$getLhs(exp), AST.$getRhs(exp));
			case "rem":
				return compileRem(AST.$getLhs(exp), AST.$getRhs(exp));
			case "sub":
				return compileSub(AST.$getLhs(exp), AST.$getRhs(exp));
			case "mul":
				return compileMul(AST.$getLhs(exp), AST.$getRhs(exp));
			case "and":
				return compileAnd(AST.$getLhs(exp), AST.$getRhs(exp));
			case "or":
				return compileOr(AST.$getLhs(exp), AST.$getRhs(exp));
			case "xor":
				return compileXor(AST.$getLhs(exp), AST.$getRhs(exp));
			case "neg":
				return compileNeg(AST.$getArg(exp));
			case "inc":
				return compileInc(AST.$getName(exp), AST.$getInc(exp));
			case "shr":
				return compileShr(AST.$getLhs(exp), AST.$getRhs(exp));
			case "shl":
				return compileShl(AST.$getLhs(exp), AST.$getRhs(exp));
			case "ushr":
				return compileUShr(AST.$getLhs(exp), AST.$getRhs(exp));
			case "checkcast":
				return compileCheckCast(AST.$getArg(exp), AST.$getType(exp));
			default: 
				throw new IllegalArgumentException("unknown expression: " + exp);                                     
			}
		}

		private IConstructor compileShl(IConstructor lhs, IConstructor rhs) {
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
			IConstructor type = compileExpression(lhs);
			if (compileExpression(rhs).getConstructorType() != Types.integerType().getConstructorType()) {
				throw new IllegalArgumentException("shift should get an integer as second parameter");
			}
			return type;
		}

		private IConstructor compileUShr(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileShr(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileInc(String name, int inc) {
			method.visitIincInsn(positionOf(name), inc);
			return Types.integerType();
		}

		private IConstructor compileAdd(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileSub(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileRem(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileDiv(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileMul(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileAnd(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileOr(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileXor(IConstructor lhs, IConstructor rhs) {
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

		private IConstructor compileNeg(IConstructor arg) {
			IConstructor type = compileExpression(arg);
			Switch.type0(type, 
					(z) -> { 
						// TODO: is there really not a better way to negate a boolean on the JVM?
						Label zeroLabel = new Label();
						Label contLabel = new Label();
						method.visitJumpInsn(Opcodes.IFEQ, zeroLabel);
						compileFalse();
						jumpTo(contLabel);
						method.visitLabel(zeroLabel);
						compileTrue();
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


		private IConstructor compileInvokeSuper(String superclass, IConstructor sig, IList args) {
			compileExpression_Load("this");
			compileExpressionList(args);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, superclass, "<init>", Signature.constructor(sig), false);
			return Types.voidType();
		}

		private void compileStat_AAStore(IConstructor array, IConstructor index,
				IConstructor arg) {
			IConstructor type = compileExpression(array);
			compileExpression(index);
			compileExpression(arg);
			compileArrayStoreWithArrayIndexValueOnStack(AST.$getArg(type));
		}

		private void compileArrayStoreWithArrayIndexValueOnStack(IConstructor type) {
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

		private IConstructor compileCheckCast(IConstructor arg, IConstructor type) {
			String cons = type.getConstructorType().getName();

			// weird inconsistency in CHECKCAST instruction?
			if (cons == "class") {
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

		private IConstructor compileExpression_ALength(IConstructor arg) {
			compileExpression(arg);
			method.visitInsn(Opcodes.ARRAYLENGTH);
			return Types.integerType();
		}

		private IConstructor compileExpression_NewArraySize(IConstructor type, IConstructor size) {
			compileExpression(size);
			
			if (!type.getConstructorType().getName().equals("array")) {
				throw new IllegalArgumentException("arg should be an array type");
			}
			compileNewArrayWithSizeOnStack(AST.$getArg(type));
			return type;
		}

		private IConstructor compileExpression_NewArray(IConstructor type, IList elems) {
			intConstant(elems.length());
			if (!type.getConstructorType().getName().equals("array")) {
				throw new IllegalArgumentException("arg should be an array type");
			}
			
			compileNewArrayWithSizeOnStack(AST.$getArg(type));

			int i = 0;
			for (IValue elem : elems) {
				dup();
				intConstant(i++);
				compileExpression((IConstructor) elem);
				compileArrayStoreWithArrayIndexValueOnStack(type);
			}
			
			return type;
		}

		private void compileNewArrayWithSizeOnStack(IConstructor type) {
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

		private IConstructor compileLt(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			IConstructor type = prepareArguments(lhs, rhs);
			
			Switch.type0(type, 
					(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, thenPart, elsePart, continuation),
					(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, thenPart, elsePart, continuation), 
					(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, thenPart, elsePart, continuation), 
					(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, thenPart, elsePart, continuation), 
					(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, thenPart, elsePart, continuation), 
					(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFGE, thenPart, elsePart, continuation),
					(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFGE, thenPart, elsePart, continuation),
					(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFGE, thenPart, elsePart, continuation),
					(v) -> { throw new IllegalArgumentException("< on void"); }, 
					(c) -> { throw new IllegalArgumentException("< on class"); }, 
					(a) -> { throw new IllegalArgumentException("< on array"); },
					(S) -> { throw new IllegalArgumentException("< on string"); }
					);
			return Types.booleanType();
		}

		private IConstructor compileLe(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			IConstructor type = prepareArguments(lhs, rhs);
			Switch.type0(type, 
					(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, thenPart, elsePart, continuation),
					(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, thenPart, elsePart, continuation), 
					(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, thenPart, elsePart, continuation), 
					(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, thenPart, elsePart, continuation), 
					(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, thenPart, elsePart, continuation), 
					(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFGT, thenPart, elsePart, continuation),
					(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFGT, thenPart, elsePart, continuation),
					(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFGT, thenPart, elsePart, continuation),
					(v) -> { throw new IllegalArgumentException("<= on void"); }, 
					(c) -> { throw new IllegalArgumentException("<= on class"); }, 
					(a) -> { throw new IllegalArgumentException("<= on array"); },
					(a) -> { throw new IllegalArgumentException("<= on string"); }
					);
			return Types.booleanType();
		}

		private IConstructor compileGt(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			IConstructor type = prepareArguments(lhs, rhs);
			
			Switch.type0(type, 
					(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, thenPart, elsePart, continuation),
					(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, thenPart, elsePart, continuation), 
					(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, thenPart, elsePart, continuation), 
					(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, thenPart, elsePart, continuation), 
					(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, thenPart, elsePart, continuation), 
					(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFLE, thenPart, elsePart, continuation),
					(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFLE, thenPart, elsePart, continuation),
					(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFLE, thenPart, elsePart, continuation),
					(v) -> { throw new IllegalArgumentException("> on void"); }, 
					(c) -> { throw new IllegalArgumentException("> on class"); }, 
					(a) -> { throw new IllegalArgumentException("> on array"); },
					(S) -> { throw new IllegalArgumentException("> on array"); }
					);
			return Types.booleanType();
		}

		private IConstructor compileGe(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			IConstructor type = prepareArguments(lhs, rhs);
			
			Switch.type0(type, 
					(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, thenPart, elsePart, continuation),
					(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, thenPart, elsePart, continuation), 
					(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, thenPart, elsePart, continuation), 
					(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, thenPart, elsePart, continuation), 
					(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, thenPart, elsePart, continuation), 
					(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFLT, thenPart, elsePart, continuation),
					(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFLT, thenPart, elsePart, continuation),
					(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFLT, thenPart, elsePart, continuation),
					(v) -> { throw new IllegalArgumentException(">= on void"); }, 
					(c) -> { throw new IllegalArgumentException(">= on class"); }, 
					(a) -> { throw new IllegalArgumentException(">= on array"); },
					(S) -> { throw new IllegalArgumentException(">= on array"); }
					);
			return Types.booleanType();
		}

		private IConstructor compileEq(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			if (lhs.getConstructorType().getName().equals("null")) {
				return compileNull(rhs, thenPart, elsePart, continuation);
			}
			else if (rhs.getConstructorType().getName().equals("null")) {
				return compileNull(lhs, thenPart, elsePart, continuation);
			}
			
			IConstructor type = prepareArguments(lhs, rhs);
			
			Switch.type0(type, 
					(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, thenPart, elsePart, continuation),
					(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, thenPart, elsePart, continuation), 
					(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, thenPart, elsePart, continuation), 
					(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, thenPart, elsePart, continuation), 
					(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, thenPart, elsePart, continuation), 
					(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFNE, thenPart, elsePart, continuation),
					(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFNE, thenPart, elsePart, continuation),
					(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFNE, thenPart, elsePart, continuation),
					(v) -> { throw new IllegalArgumentException(">= on void"); }, 
					(c) -> compileConditionalInverted(0, Opcodes.IF_ACMPNE, thenPart, elsePart, continuation), 
					(a) -> compileConditionalInverted(0, Opcodes.IF_ACMPNE, thenPart, elsePart, continuation),
					(S) -> compileConditionalInverted(0, Opcodes.IF_ACMPNE, thenPart, elsePart, continuation)
					);
			return Types.booleanType();
		}

		private IConstructor prepareArguments(IConstructor lhs, IConstructor rhs) {
			IConstructor type = compileExpression(lhs);
			if (type.getConstructorType() != compileExpression(rhs).getConstructorType()) {
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
		 * @param continuation emit code for what runs after this conditional
		 */
		private void compileConditionalInverted(int compare, int opcode, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			// TODO: is this the most efficient encoding? probably not. 
			Label jump = new Label();
			Label join = new Label();

			if (compare != 0) {
				method.visitInsn(compare);
			}
			method.visitJumpInsn(opcode, jump);
			thenPart.build();
			jumpTo(join);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			elsePart.build();
			method.visitLabel(join);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			continuation.build();
		}

		private IConstructor compileNull(IConstructor arg, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			compileExpression(arg);
			compileConditionalInverted(0, Opcodes.IFNONNULL, thenPart, elsePart, continuation);
			return Types.booleanType();
		}

		private IConstructor compileNonNull(IConstructor arg, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			compileExpression(arg);
			compileConditionalInverted(0, Opcodes.IFNULL, thenPart, elsePart, continuation);
			return Types.booleanType();
		}

		private IConstructor compileNe(IConstructor lhs, IConstructor rhs, Builder<?> thenPart, Builder<?> elsePart, Builder<?> continuation) {
			if (lhs.getConstructorType().getName().equals("null")) {
				return compileNonNull(rhs, thenPart, elsePart, continuation);
			}
			else if (rhs.getConstructorType().getName().equals("null")) {
				return compileNonNull(lhs, thenPart, elsePart, continuation);
			}
			
			IConstructor type = prepareArguments(lhs, rhs);
			
			Switch.type0(type, 
					(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, continuation),
					(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, continuation), 
					(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, continuation), 
					(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, continuation), 
					(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, thenPart, elsePart, continuation), 
					(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFEQ, thenPart, elsePart, continuation),
					(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFEQ, thenPart, elsePart, continuation),
					(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFEQ, thenPart, elsePart, continuation),
					(v) -> { throw new IllegalArgumentException("!= on void"); }, 
					(c) -> compileConditionalInverted(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, continuation), 
					(a) -> compileConditionalInverted(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, continuation),
					(S) -> compileConditionalInverted(0, Opcodes.IF_ACMPEQ, thenPart, elsePart, continuation)
					);
			
			return Types.booleanType();
		}

		private IConstructor compileCoerce(IConstructor from, IConstructor to, IConstructor arg) {
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
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "parseBoolean", Signature.stringType, false);
					}, 
					(i) -> {
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "I", false);
					}, 
					(s) -> {
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "parseShort", "S", false);
					},
					(b) -> {
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "parseByte", "B", false);
					}, 
					(c) -> failedCoercion("string", to), 
					(f) -> {
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "parseFloat", "F", false);
					}, 
					(d) -> {
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "parseDouble", "D", false);
					}, 
					(j) -> {
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "parseLong", "J", false);
					}, 
					(v) -> failedCoercion("string", to), 
					(a) -> failedCoercion("string", to),
					(c) -> failedCoercion("class", to),
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
					(c) -> failedCoercion("class", to),
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
					(v) -> { pop(); compileNull(); },
					(c) -> failedCoercion("class", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private void coerceFromClass(IConstructor from, IConstructor to, IConstructor arg) {
			String cls = AST.$getName(from);

			Switch.type0(to,
					(z) -> {
						if (cls.equals("java/lang/Boolean")) {
							compileExpression(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "booleanValue", "()Z", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(i) -> {
						if (cls.equals("java/lang/Integer")) {
							compileExpression(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "intValue", "()I", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(s) -> {
						if (cls.equals("java/lang/Integer")) {
							compileExpression(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "shortValue", "()S", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(b) -> {
						if (cls.equals("java/lang/Integer")) {
							compileExpression(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "byteValue", "()B", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(c) -> failedCoercion(cls, arg),
					(f) -> {
						if (cls.equals("java/lang/Float")) {
							compileExpression(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Float", "floatValue", "()F", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(d) -> {
						if (cls.equals("java/lang/Double")) {
							compileExpression(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Double", "doubleValue", "()D", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(l) -> {
						if (cls.equals("java/lang/Long")) {
							compileExpression(from);
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Long", "longValue", "()L", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(v) -> { pop(); compileNull(); },
					(c) -> {
						if (cls.equals(AST.$getName(to))) {
							/* do nothing */
						}
						else {
							failedCoercion("class", to);
						}
					},
					(a) -> failedCoercion("array", to),
					(S) -> {
						compileExpression(arg);
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
					(v) -> { pop(); compileNull(); },
					(c) -> failedCoercion("class", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						compileExpression(arg);
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
					(v) -> { pop(); compileNull(); },
					(c) -> failedCoercion("class", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						compileExpression(arg);
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
					(v) -> { pop(); compileNull(); },
					(c) -> failedCoercion("class", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						compileExpression(arg);
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
					(v) -> { pop(); compileNull(); },
					(c) -> failedCoercion("class", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						compileExpression(arg);
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
					(v) -> { pop(); compileNull(); },
					(c) -> failedCoercion("class", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						compileExpression(arg);
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
					(v) -> { pop(); compileNull(); },
					(c) -> failedCoercion("class", to),
					(a) -> failedCoercion("array", to),
					(S) -> {
						compileExpression(arg);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
					}
					);
		}

		private IConstructor compileFalse() {
			method.visitInsn(Opcodes.ICONST_0);
			return Types.booleanType();
		}

		private IConstructor compileTrue() {
			method.visitInsn(Opcodes.ICONST_1);
			return Types.booleanType();
		}

		private IConstructor compileNull() {
			method.visitInsn(Opcodes.ACONST_NULL);
			return Types.voidType();
		}

		private IConstructor compileBlock(IList block, IConstructor arg) {
			compileStatements(block, DONE);
			IConstructor type = compileExpression(arg);
			return type;
		}

		private IConstructor compileInstanceof(IConstructor arg, String cls) {
			compileExpression(arg);
			method.visitTypeInsn(Opcodes.INSTANCEOF, cls);
			return Types.booleanType();
		}

		private IConstructor compileGetField(IConstructor receiver, String cls, IConstructor type, String name) {
			compileExpression(receiver);
			method.visitFieldInsn(Opcodes.GETFIELD, cls, name, Signature.type(type));
			return type;
		}

		private IConstructor compileExpression_NewInstance(IConstructor exp) {
			compileExpressionList(AST.$getArgs(exp));
			IConstructor type = AST.$getClass(exp);
			String cls = AST.$getClassFromType(type, classNode.name);
			String desc = Signature.constructor(AST.$getDesc(exp));
			method.visitTypeInsn(Opcodes.NEW, cls);
			dup();
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, "<init>", desc, false);
			return type;
		}

		private IConstructor compileExpression_AALoad(IConstructor array, IConstructor index) {
			IConstructor type = compileExpression(array);
			compileExpression(index);
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

		private IConstructor compileGetStatic(String cls, IConstructor type, String name) {
			method.visitFieldInsn(Opcodes.GETSTATIC, cls, name, Signature.type(type));
			return type;
		}

		private IConstructor compileInvokeSpecial(String cls, IConstructor sig, IConstructor receiver, IList args) {
			compileExpression(receiver);
			compileExpressionList(args);

			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor compileInvokeVirtual(String cls, IConstructor sig, IConstructor receiver, IList args) {
			compileExpression(receiver);
			compileExpressionList(args);

			method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor compileInvokeInterface(String interf, IConstructor sig, IConstructor receiver, IList args) {
			compileExpression(receiver);
			compileExpressionList(args);

			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, interf, AST.$getName(sig), Signature.method(sig), true);
			return AST.$getReturn(sig);
		}

		private Void compileExpressionList(IList args) {
			if (args.length() == 0) {
				return null;
			}
			else {
				compileExpression((IConstructor) args.get(0));
				compileExpressionList(args.delete(0));
			}
			return null;
		}

		private IConstructor compileInvokeStatic(String cls, IConstructor sig, IList args) {
			compileExpressionList(args);

			method.visitMethodInsn(Opcodes.INVOKESTATIC, cls, AST.$getName(sig), Signature.method(sig), false);
			return AST.$getReturn(sig);
		}

		private IConstructor compileExpression_Load(String name) {
			int pos = positionOf(name);
			IConstructor type = variableTypes[pos];

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
			for (int pos = 0; pos < variableNames.length; pos++) {
				if (name.equals(variableNames[pos])) {
					return pos;
				}
			}

			throw new IllegalArgumentException("name not found: " + name);
		}

		private IConstructor compileExpression_Const(IConstructor type, IValue constant) {
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
			compileNewArrayWithSizeOnStack(type);
			int index = 0;

			for (IValue elem : constant) {
				dup();
				intConstant(index);
				compileExpression_Const((IConstructor) elem, elem);
				compileArrayStoreWithArrayIndexValueOnStack(type);
			}
		}

		private void booleanConstant(boolean val) {
			if (val) {
				compileTrue();
			}
			else {
				compileFalse();
			}
		}

		private void dup() {
			method.visitInsn(Opcodes.DUP);
		}

		private void compileField(ClassNode classNode, IConstructor cons) {
			IWithKeywordParameters<? extends IConstructor> kws = cons.asWithKeywordParameters();
			int access = 0;

			if (kws.hasParameter("modifiers")) {
				access = compileModifiers(AST.$getModifiersParameter(kws));
			}
			else {
				access = Opcodes.ACC_PRIVATE;
			}

			String name = AST.$getName(cons);

			IConstructor type = AST.$getType(cons);
			String signature = Signature.type(type);

			Object value = null;
			if (kws.hasParameter("default")) {
				IConstructor defaultExpr = (IConstructor) kws.getParameter("default");
				
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

		private int compileAccessCode(ISet modifiers) {
			for (IValue cons : modifiers) {
				switch (((IConstructor) cons).getName()) {
				case "public": return Opcodes.ACC_PUBLIC;
				case "private": return Opcodes.ACC_PRIVATE;
				case "protected": return Opcodes.ACC_PROTECTED;
				}
			}

			return 0;
		}

		private int compileModifiers(ISet modifiers) {
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

		public static IValue $getConstant(IConstructor exp) {
			return exp.get("constant");
		}

		public static boolean $is(String string, IConstructor parameter) {
			return parameter.getConstructorType().getName().equals(string);
		}

		public static IConstructor $getDefault(IConstructor var) {
			IWithKeywordParameters<? extends IConstructor> kws = var.asWithKeywordParameters();
			
			if (!kws.hasParameter("default")) {
				return null;
			}

			return (IConstructor) kws.getParameter("default");
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

		public static IConstructor $getBlock(IConstructor cons) {
			return (IConstructor) cons.get("block");
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
			return ((IString) kws.getParameter("super")).getValue().replace('.','/');
		}

		public static String $string(IValue v) {
			return ((IString) v).getValue();
		}

		public static IValue $getDefaultParameter(IWithKeywordParameters<? extends IConstructor> kws) {
			return kws.getParameter("default");
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
			case "class" :
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
			case "class" :
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
			case "class" :
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
		
		static IConstructor booleanType() {
			return BOOL_CONS;
		}
		
		static IConstructor integerType() {
			return INTEGER_CONS;
		}
		
		static IConstructor voidType() {
			return VOID_CONS;
		}
	}
}

