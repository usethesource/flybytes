package lang.mujava.internal;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
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
import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.uri.URIResolverRegistry;

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
import io.usethesource.vallang.type.ITypeVisitor;
import io.usethesource.vallang.type.Type;

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

	public IValue loadClass(IConstructor cls, IList classpath, IBool enableAsserts, IConstructor version, IEvaluatorContext ctx) {
		this.out = ctx.getStdOut();

		try {
			String className = AST.$getName(AST.$getType(cls));
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			new Compile(cw, AST.$getVersionCode(version), out).compileClass(cls);

			Class<?> loaded = loadClass(className, cw);

			Mirror m = new Mirror(vf, ctx.getCurrentEnvt().getStore(), ctx);

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
		private static final Builder DONE = () -> {};
		private final ClassVisitor cw;
		private final int version;
//		private final PrintWriter out;
		private IConstructor[] variableTypes;
		private String[] variableNames;
		private int variableCounter;
		private Label scopeStart;
		private Label scopeEnd;
		private MethodNode method;
		private IConstructor classType;
		private ClassNode classNode;

		public Compile(ClassVisitor cw, int version, PrintWriter out) {
			this.cw = cw;
			this.version = version;
//			this.out = out;
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

			classNode.accept(cw);
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
				compileMethod(classNode, cons);
			}
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
			IList varFormals = AST.$getFormals(cons);
			IConstructor block = AST.$getBlock(cons);
			IList locals = AST.$getVariables(block);

			if (sigFormals.length() != varFormals.length()) {
				throw new IllegalArgumentException("type signature of " + name + " has different number of types (" + sigFormals.length() + ") from formal parameters (" + varFormals.length() + "), see: " + sigFormals + " versus " + varFormals);
			}

			method = new MethodNode(modifiers, name, isConstructor ? Signature.constructor(sig) : Signature.method(sig), null, null);

			// "this" is the implicit first argument for all non-static methods
			boolean isStatic = (modifiers & Opcodes.ACC_STATIC) != 0;

			variableCounter = isStatic ? 0 : 1;
			variableTypes = new IConstructor[2 /*for wide var */ * (varFormals.length() + locals.length()) + variableCounter];
			variableNames = new String[2 /*for wide vars*/ * (varFormals.length() + locals.length()) + variableCounter];

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

			compileVariables(varFormals, false /* no initialization */);
			compileBlock(block);

			method.visitLabel(scopeEnd);
			method.visitMaxs(0, 0);
			method.visitEnd();

			classNode.methods.add(method);
		}

		private void compileVariables(IList formals, boolean initialize) {
			int startLocals = variableCounter;

			for (IValue elem : formals) {
				IConstructor var = (IConstructor) elem;

				variableTypes[variableCounter] = AST.$getType(var);
				variableNames[variableCounter] = AST.$getName(var);
				method.visitLocalVariable(variableNames[variableCounter], Signature.type(variableTypes[variableCounter]), null, scopeStart, scopeEnd, variableCounter);

				Switch.type0(variableTypes[variableCounter], 
						(z)  -> variableCounter++,
						(ii) -> variableCounter++,
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
			}
		}

		private void compileBlock(IConstructor block) {
			compileVariables(AST.$getVariables(block), true);
			compileStatements(AST.$getStatements(block), DONE);
		}

		/**
		 * We use a continuation passing style here, such that when branching
		 * code is generated we can generate the right code in the right place 
		 * without superfluous additional labels and gotos.
		 */
		private void compileStatements(IList statements, Builder continuation) {
			if (statements.length() == 0) {
				continuation.build();
			}
			else {
				compileStatement(
						(IConstructor) statements.get(0),
						() -> compileStatements(statements.delete(0), continuation)
						);
			}
		}

		private void compileStatement(IConstructor stat, Builder continuation) {
			switch (stat.getConstructorType().getName()) {
			case "do" : 
				compileStat_Do(AST.$getType(stat), (IConstructor) stat.get("exp"));
				continuation.build();
				break;
			case "store" : 
				compileStat_Store(stat); 
				continuation.build();
				break;
			case "aastore" :
				compileStat_AAStore(AST.$getType(stat), AST.$getArray(stat), AST.$getIndex(stat), AST.$getArg(stat));
				continuation.build();
				break;
			case "putField":
				compileStat_PutField(AST.$getClass(stat, classNode.name), AST.$getReceiver(stat), AST.$getType(stat), AST.$getName(stat), AST.$getArg(stat));
				continuation.build();
				break;
			case "putStatic":
				compileStat_PutStatic(AST.$getClass(stat, classNode.name), AST.$getType(stat), AST.$getName(stat), AST.$getArg(stat));
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

		private void compileStat_For(IList $getInit, IConstructor $getCondition, IConstructor $getNext, IList $getStatements, Builder continuation) {
			throw new UnsupportedOperationException();
		}

		private void compileStat_If(IConstructor cond, IList thenBlock, Builder continuation) {
			compileStat_IfThenElse(cond, thenBlock, null, continuation);
		}

		private void compileStat_IfThenElse(IConstructor cond, IList thenBlock, IList elseBlock, Builder continuation) {
			Builder thenBuilder = () -> compileStatements(thenBlock, DONE);
			Builder elseBuilder = elseBlock != null ? () -> compileStatements(elseBlock, DONE) : DONE;

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
				compileEq(AST.$getType(cond), AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				break;
			case "ne":
				compileNeq(AST.$getType(cond), AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				break;
			case "le":
				compileLe(AST.$getType(cond), AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				break;
			case "gt":
				compileGt(AST.$getType(cond), AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				break;
			case "ge":
				compileGe(AST.$getType(cond), AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				break;
			case "lt":
				compileLt(AST.$getType(cond), AST.$getLhs(cond), AST.$getRhs(cond), thenBuilder, elseBuilder, continuation);
				break;
			case "null":
				if (cond.getConstructorType().getArity() != 1) {
					throw new IllegalArgumentException("null check without a parameter");
				}
				compileNull(AST.$getArg(cond), thenBuilder, elseBuilder, continuation);
				break;
			case "nonnull":
				if (cond.getConstructorType().getArity() != 1) {
					throw new IllegalArgumentException("nonnull check without a parameter");
				}
				compileNull(AST.$getArg(cond), thenBuilder, elseBuilder, continuation);
				break;
			default:
				compileConditionalInverted(
						() -> compileExpression(cond, () -> compileTrue()),
						0, 
						Opcodes.IF_ICMPNE, 
						thenBuilder,
						elseBuilder,
						continuation
						);
			}
		}

		private void compileStat_PutStatic(String cls, IConstructor type, String name, IConstructor arg) {
			compileExpression(arg, DONE);
			method.visitFieldInsn(Opcodes.PUTSTATIC, cls, name, Signature.type(type));
		}

		private void compileStat_PutField(String cls, IConstructor receiver, IConstructor type, String name, IConstructor arg) {
			compileExpression(receiver, () -> compileExpression(arg, DONE));
			method.visitFieldInsn(Opcodes.PUTFIELD, cls, name, Signature.type(type));
		}

		private void compileStat_Store(IConstructor stat) {
			String name = AST.$getName(stat);
			int pos = positionOf(name);
			compileExpression(AST.$getValue(stat), DONE);

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
				compileExpression(AST.$getArg(stat), DONE);

				Switch.type0(AST.$getType(stat),
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

		private void compileStat_Do(IConstructor type, IConstructor exp) {
			compileExpression(exp, () -> 
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
					));
		}

		private void pop() {
			method.visitInsn(Opcodes.POP);
		}

		private void pop2() {
			method.visitInsn(Opcodes.POP2);
		}

		private void compileExpression(IConstructor exp, Builder continuation) {
			switch (exp.getConstructorType().getName()) {
			case "const" : 
				compileExpression_Const(AST.$getType(exp), AST.$getConstant(exp)); 
				continuation.build();
				break;
			case "this" : 
				compileExpression_Load("this");
				continuation.build();
				break;
			case "newInstance":
				compileExpression_NewInstance(exp);
				continuation.build();
				break;
			case "newArray":
				if (exp.get(1) instanceof IList) {
					compileExpression_NewArray(AST.$getType(exp), AST.$getArgs(exp));
				}
				else {
					compileExpression_NewArraySize(AST.$getType(exp), AST.$getSize(exp));
				}
				continuation.build();
				break;
			case "alength":
				compileExpression_ALength(AST.$getArg(exp));
				continuation.build();
				break;
			case "load" : 
				compileExpression_Load(AST.$getName(exp)); 
				continuation.build();
				break;
			case "aaload" :
				compileExpression_AALoad(AST.$getArray(exp), AST.$getIndex(exp));
				continuation.build();
				break;
		
			case "getStatic":
				compileGetStatic(AST.$getClass(exp, classNode.name), AST.$getType(exp), AST.$getName(exp));
				continuation.build();
				break;
			case "invokeVirtual" : 
				compileInvokeVirtual(AST.$getClass(exp, classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
				continuation.build();
				break;
			case "invokeInterface" : 
				compileInvokeInterface(AST.$getClass(exp, classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
				continuation.build();
				break;
			case "invokeSpecial" : 
				compileInvokeSpecial(AST.$getClass(exp, classNode.name), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
				continuation.build();
				break;
			case "invokeSuper" : 
				compileInvokeSuper(classNode.superName, AST.$getDesc(exp), AST.$getArgs(exp));
				continuation.build();
				break;
			case "invokeStatic" : 
				compileInvokeStatic(AST.$getClass(exp, classNode.name), AST.$getDesc(exp), AST.$getArgs(exp));
				continuation.build();
				break;
			case "getField":
				compileGetField(AST.$getReceiver(exp), AST.$getClass(exp, classNode.name), AST.$getType(exp), AST.$getName(exp));
				continuation.build();
				break;
			case "instanceof":
				compileInstanceof(AST.$getArg(exp), AST.$getClass(exp, classNode.name));
				continuation.build();
				break;
			case "block":
				compileBlock(AST.$getStatements(exp), AST.$getArg(exp), continuation);
				break;
			case "null":
				if (exp.getConstructorType().getArity() == 0) {
					compileNull();  // null constant
					continuation.build();
				}
				else { 
					compileNull(AST.$getArg(exp), () -> compileTrue(), () -> compileFalse(), continuation); // null check 
				}
				break;
			case "nonnull":
				compileNonNull(AST.$getArg(exp), () -> compileTrue(), () -> compileFalse(),continuation); // null check 
				break;
			case "true":
				compileTrue();
				break;
			case "false":
				compileFalse();
				break;
			case "coerce":
				compileCoerce(AST.$getFrom(exp), AST.$getTo(exp), AST.$getArg(exp));
				break;
			case "eq":
				compileEq(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp), () -> compileTrue(), () -> compileFalse(), continuation);
				break;
			case "ne":
				compileNeq(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp), () -> compileTrue(), () -> compileFalse(), continuation);
				break;
			case "le":
				compileLe(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp), () -> compileTrue(), () -> compileFalse(), continuation);
				break;
			case "gt":
				compileGt(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp), () -> compileTrue(), () -> compileFalse(), continuation);
				break;
			case "ge":
				compileGe(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp), () -> compileTrue(), () -> compileFalse(), continuation);
				break;
			case "lt":
				compileLt(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp), () -> compileTrue(), () -> compileFalse(),continuation);
				break;
			case "add":
				compileAdd(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "div":
				compileDiv(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "rem":
				compileRem(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "sub":
				compileSub(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "mul":
				compileMul(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "and":
				compileAnd(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "or":
				compileOr(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "xor":
				compileXor(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "neg":
				compileNeg(AST.$getType(exp), AST.$getArg(exp));
				continuation.build();
				break;
			case "inc":
				compileInc(AST.$getName(exp), AST.$getInc(exp));
				continuation.build();
				break;
			case "shr":
				compileShr(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "shl":
				compileShl(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "ushr":
				compileUShr(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				continuation.build();
				break;
			case "checkcast":
				compileCheckCast(AST.$getArg(exp), AST.$getType(exp));
				continuation.build();
				break;
			default: 
				throw new IllegalArgumentException("unknown expression: " + exp);                                     
			}
		}

		private void compileShl(IConstructor type, IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs, DONE);
			compileExpression(rhs, DONE);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.ISHL),
					(i) -> method.visitInsn(Opcodes.ISHL), 
					(s) -> method.visitInsn(Opcodes.ISHL), 
					(b) -> method.visitInsn(Opcodes.ISHL), 
					(c) -> method.visitInsn(Opcodes.ISHL), 
					(f) -> { throw new IllegalArgumentException("xor on void"); },
					(d) -> { throw new IllegalArgumentException("xor on void"); },
					(l) -> method.visitInsn(Opcodes.LSHL),
					(v) -> { throw new IllegalArgumentException("xor on void"); }, 
					(c) -> { throw new IllegalArgumentException("xor on object"); },
					(a) -> { throw new IllegalArgumentException("xor on array"); },
					(S) -> { throw new IllegalArgumentException("xor on string"); }
					);
		}

		private void compileUShr(IConstructor type, IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs, DONE);
			compileExpression(rhs, DONE);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.IUSHR),
					(i) -> method.visitInsn(Opcodes.IUSHR), 
					(s) -> method.visitInsn(Opcodes.IUSHR), 
					(b) -> method.visitInsn(Opcodes.IUSHR), 
					(c) -> method.visitInsn(Opcodes.IUSHR), 
					(f) -> { throw new IllegalArgumentException("xor on void"); },
					(d) -> { throw new IllegalArgumentException("xor on void"); },
					(l) -> method.visitInsn(Opcodes.LUSHR),
					(v) -> { throw new IllegalArgumentException("xor on void"); }, 
					(c) -> { throw new IllegalArgumentException("xor on object"); },
					(a) -> { throw new IllegalArgumentException("xor on array"); },
					(S) -> { throw new IllegalArgumentException("xor on string"); }
					);
		}

		private void compileShr(IConstructor type, IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs, DONE);
			compileExpression(rhs, DONE);
			Switch.type0(type, 
					(z) -> method.visitInsn(Opcodes.ISHR),
					(i) -> method.visitInsn(Opcodes.ISHR), 
					(s) -> method.visitInsn(Opcodes.ISHR), 
					(b) -> method.visitInsn(Opcodes.ISHR), 
					(c) -> method.visitInsn(Opcodes.ISHR), 
					(f) -> { throw new IllegalArgumentException("xor on void"); },
					(d) -> { throw new IllegalArgumentException("xor on void"); },
					(l) -> method.visitInsn(Opcodes.LSHR),
					(v) -> { throw new IllegalArgumentException("xor on void"); }, 
					(c) -> { throw new IllegalArgumentException("xor on object"); },
					(a) -> { throw new IllegalArgumentException("xor on array"); },
					(S) -> { throw new IllegalArgumentException("xor on string"); }
					);
		}

	private void compileInc(String name, int inc) {
		method.visitIincInsn(positionOf(name), inc); 
	}

	private void compileAdd(IConstructor type, IConstructor lhs, IConstructor rhs) {
		compileExpression(lhs, DONE);
		compileExpression(rhs, DONE);
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
	}

	private void compileSub(IConstructor type, IConstructor lhs, IConstructor rhs) {
		compileExpression(lhs, DONE);
		compileExpression(rhs, DONE);
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
	}

	private void compileRem(IConstructor type, IConstructor lhs, IConstructor rhs) {
		compileExpression(lhs, DONE);
		compileExpression(rhs, DONE);
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
	}

	private void compileDiv(IConstructor type, IConstructor lhs, IConstructor rhs) {
		compileExpression(lhs, DONE);
		compileExpression(rhs, DONE);
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
	}

	private void compileMul(IConstructor type, IConstructor lhs, IConstructor rhs) {
		compileExpression(lhs, DONE);
		compileExpression(rhs, DONE);
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
	}

	private void compileAnd(IConstructor type, IConstructor lhs, IConstructor rhs) {
		compileExpression(lhs, DONE);
		compileExpression(rhs, DONE);
		Switch.type0(type, 
				(z) -> method.visitInsn(Opcodes.IAND),
				(i) -> method.visitInsn(Opcodes.IAND), 
				(s) -> method.visitInsn(Opcodes.IAND), 
				(b) -> method.visitInsn(Opcodes.IAND), 
				(c) -> method.visitInsn(Opcodes.IAND), 
				(f) -> { throw new IllegalArgumentException("and on void"); },
				(d) -> { throw new IllegalArgumentException("and on void"); },
				(l) -> { throw new IllegalArgumentException("and on void"); },
				(v) -> { throw new IllegalArgumentException("and on void"); }, 
				(c) -> { throw new IllegalArgumentException("and on object"); },
				(a) -> { throw new IllegalArgumentException("and on array"); },
				(S) -> { throw new IllegalArgumentException("and on string"); }
				);
	}

	private void compileOr(IConstructor type, IConstructor lhs, IConstructor rhs) {
		compileExpression(lhs, DONE);
		compileExpression(rhs, DONE);
		Switch.type0(type, 
				(z) -> method.visitInsn(Opcodes.IOR),
				(i) -> method.visitInsn(Opcodes.IOR), 
				(s) -> method.visitInsn(Opcodes.IOR), 
				(b) -> method.visitInsn(Opcodes.IOR), 
				(c) -> method.visitInsn(Opcodes.IOR), 
				(f) -> { throw new IllegalArgumentException("or on void"); },
				(d) -> { throw new IllegalArgumentException("or on void"); },
				(l) -> { throw new IllegalArgumentException("or on void"); },
				(v) -> { throw new IllegalArgumentException("or on void"); }, 
				(c) -> { throw new IllegalArgumentException("or on object"); },
				(a) -> { throw new IllegalArgumentException("or on array"); },
				(S) -> { throw new IllegalArgumentException("or on string"); }
				);
	}

	private void compileXor(IConstructor type, IConstructor lhs, IConstructor rhs) {
		compileExpression(lhs, DONE);
		compileExpression(rhs, DONE);
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
	}

	private void compileNeg(IConstructor type, IConstructor arg) {
		compileExpression(arg, DONE);
		Switch.type0(type, 
				(z) -> method.visitInsn(Opcodes.INEG),
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
	}


	private void compileInvokeSuper(String superclass, IConstructor sig, IList args) {
		compileExpression_Load("this");
		compileExpressionList(args, DONE);
		method.visitMethodInsn(Opcodes.INVOKESPECIAL, superclass, "<init>", Signature.constructor(sig), false);
	}

	private void compileStat_AAStore(IConstructor type, IConstructor array, IConstructor index,
			IConstructor arg) {
		// passing continuations make this look complex.
		//   * first compile array, 
		//   * then the index, 
		//   * then the store instruction:
		compileExpression(array, 
				() -> compileExpression(index, 
						() -> compileExpression(arg,
								() -> compileArrayStoreWithArrayIndexValueOnStack(type)
								)
						)
				);
	}

	private void compileArrayStoreWithArrayIndexValueOnStack(IConstructor type) {
		Switch.type0(type, 
				(z) -> method.visitInsn(Opcodes.IASTORE),
				(i) -> method.visitInsn(Opcodes.IASTORE),
				(s) -> method.visitInsn(Opcodes.IASTORE),
				(b) -> method.visitInsn(Opcodes.IASTORE),
				(c) -> method.visitInsn(Opcodes.IASTORE),
				(f) -> method.visitInsn(Opcodes.FASTORE),
				(d) -> method.visitInsn(Opcodes.DASTORE),
				(l) -> method.visitInsn(Opcodes.LASTORE),
				(v) -> { throw new IllegalArgumentException("store void in array"); },
				(c) -> method.visitInsn(Opcodes.AASTORE),
				(a) -> method.visitInsn(Opcodes.AASTORE),
				(S) -> method.visitInsn(Opcodes.AASTORE)
				);
	}

	private void compileCheckCast(IConstructor arg, IConstructor type) {
		compileExpression(arg, DONE);
		String cons = type.getConstructorType().getName();

		// weird inconsistency in CHECKCAST instruction?
		if (cons == "classType") {
			method.visitTypeInsn(Opcodes.CHECKCAST, AST.$getName(type));
		}
		else if (cons == "array") {
			method.visitTypeInsn(Opcodes.CHECKCAST, Signature.type(type));
		}
		else {
			throw new IllegalArgumentException("can not check cast to " + type);
		}
	}

	private void compileExpression_ALength(IConstructor arg) {
		compileExpression(arg, DONE);
		method.visitInsn(Opcodes.ARRAYLENGTH);
	}

	private void compileExpression_NewArraySize(IConstructor type, IConstructor size) {
		compileExpression(size, () -> compileNewArrayWithSizeOnStack(type));
	}
	
	private void compileExpression_NewArray(IConstructor type, IList elems) {
		intConstant(elems.length());
		compileNewArrayWithSizeOnStack(type);
		
		int i = 0;
		for (IValue elem : elems) {
			dup();
			intConstant(i++);
			compileExpression((IConstructor) elem, DONE);
			compileArrayStoreWithArrayIndexValueOnStack(type);
		}
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

	private void compileLt(IConstructor type, IConstructor lhs, IConstructor rhs, Builder thenPart, Builder elsePart, Builder continuation) {
		Switch.type0(type, 
				(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, lhs, rhs, thenPart, elsePart, continuation),
				(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, lhs, rhs, thenPart, elsePart, continuation), 
				(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, lhs, rhs, thenPart, elsePart, continuation), 
				(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, lhs, rhs, thenPart, elsePart, continuation), 
				(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPGE, lhs, rhs, thenPart, elsePart, continuation), 
				(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFGE, lhs, rhs, thenPart, elsePart, continuation),
				(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFGE, lhs, rhs, thenPart, elsePart, continuation),
				(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFGE, lhs, rhs, thenPart, elsePart, continuation),
				(v) -> { throw new IllegalArgumentException("< on void"); }, 
				(c) -> { throw new IllegalArgumentException("< on class"); }, 
				(a) -> { throw new IllegalArgumentException("< on array"); },
				(S) -> { throw new IllegalArgumentException("< on string"); }
				);
	}

	private void compileLe(IConstructor type, IConstructor lhs, IConstructor rhs, Builder thenPart, Builder elsePart, Builder continuation) {
		Switch.type0(type, 
				(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, lhs, rhs, thenPart, elsePart, continuation),
				(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, lhs, rhs, thenPart, elsePart, continuation), 
				(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, lhs, rhs, thenPart, elsePart, continuation), 
				(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, lhs, rhs, thenPart, elsePart, continuation), 
				(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPGT, lhs, rhs, thenPart, elsePart, continuation), 
				(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFGT, lhs, rhs, thenPart, elsePart, continuation),
				(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFGT, lhs, rhs, thenPart, elsePart, continuation),
				(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFGT, lhs, rhs, thenPart, elsePart, continuation),
				(v) -> { throw new IllegalArgumentException("<= on void"); }, 
				(c) -> { throw new IllegalArgumentException("<= on class"); }, 
				(a) -> { throw new IllegalArgumentException("<= on array"); },
				(a) -> { throw new IllegalArgumentException("<= on string"); }
				);
	}

	private void compileGt(IConstructor type, IConstructor lhs, IConstructor rhs, Builder thenPart, Builder elsePart, Builder continuation) {
		Switch.type0(type, 
				(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, lhs, rhs, thenPart, elsePart, continuation),
				(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, lhs, rhs, thenPart, elsePart, continuation), 
				(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, lhs, rhs, thenPart, elsePart, continuation), 
				(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, lhs, rhs, thenPart, elsePart, continuation), 
				(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPLE, lhs, rhs, thenPart, elsePart, continuation), 
				(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFLE, lhs, rhs, thenPart, elsePart, continuation),
				(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFLE, lhs, rhs, thenPart, elsePart, continuation),
				(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFLE, lhs, rhs, thenPart, elsePart, continuation),
				(v) -> { throw new IllegalArgumentException("> on void"); }, 
				(c) -> { throw new IllegalArgumentException("> on class"); }, 
				(a) -> { throw new IllegalArgumentException("> on array"); },
				(S) -> { throw new IllegalArgumentException("> on array"); }
				);
	}

	private void compileGe(IConstructor type, IConstructor lhs, IConstructor rhs, Builder thenPart, Builder elsePart, Builder continuation) {
		Switch.type0(type, 
				(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, lhs, rhs, thenPart, elsePart, continuation),
				(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, lhs, rhs, thenPart, elsePart, continuation), 
				(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, lhs, rhs, thenPart, elsePart, continuation), 
				(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, lhs, rhs, thenPart, elsePart, continuation), 
				(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPLT, lhs, rhs, thenPart, elsePart, continuation), 
				(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFLT, lhs, rhs, thenPart, elsePart, continuation),
				(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFLT, lhs, rhs, thenPart, elsePart, continuation),
				(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFLT, lhs, rhs, thenPart, elsePart, continuation),
				(v) -> { throw new IllegalArgumentException(">= on void"); }, 
				(c) -> { throw new IllegalArgumentException(">= on class"); }, 
				(a) -> { throw new IllegalArgumentException(">= on array"); },
				(S) -> { throw new IllegalArgumentException(">= on array"); }
				);
	}

	private void compileEq(IConstructor type, IConstructor lhs, IConstructor rhs, Builder thenPart, Builder elsePart, Builder continuation) {
		Switch.type0(type, 
				(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, lhs, rhs, thenPart, elsePart, continuation),
				(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, lhs, rhs, thenPart, elsePart, continuation), 
				(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, lhs, rhs, thenPart, elsePart, continuation), 
				(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, lhs, rhs, thenPart, elsePart, continuation), 
				(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPNE, lhs, rhs, thenPart, elsePart, continuation), 
				(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFNE, lhs, rhs, thenPart, elsePart, continuation),
				(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFNE, lhs, rhs, thenPart, elsePart, continuation),
				(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFNE, lhs, rhs, thenPart, elsePart, continuation),
				(v) -> { throw new IllegalArgumentException(">= on void"); }, 
				(c) -> compileConditionalInverted(0, Opcodes.IF_ACMPNE, lhs, rhs, thenPart, elsePart, continuation), 
				(a) -> compileConditionalInverted(0, Opcodes.IF_ACMPNE, lhs, rhs, thenPart, elsePart, continuation),
				(S) -> compileConditionalInverted(0, Opcodes.IF_ACMPNE, lhs, rhs, thenPart, elsePart, continuation)
				);
	}

	@FunctionalInterface
	private static interface Builder { 
		void build();
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
	private void compileConditionalInverted(Builder args, int compare, int opcode, Builder thenPart, Builder elsePart, Builder continuation) {
		args.build();
		// TODO: is this the most efficient encoding? probably not. 
		Label jump = new Label();
		Label join = new Label();

		if (compare != 0) {
			method.visitInsn(compare);
		}
		method.visitJumpInsn(opcode, jump);
		thenPart.build();
		method.visitJumpInsn(Opcodes.GOTO, join);
		method.visitInsn(Opcodes.GOTO);
		method.visitLabel(jump);
		method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		elsePart.build();
		method.visitLabel(join);
		continuation.build();
	}

	private void compileConditionalInverted(int compare, int opcode, IConstructor lhs, IConstructor rhs, Builder thenPart, Builder elsePart, Builder continuation) {
		compileConditionalInverted(
				() -> compileExpression(lhs, 
						() -> compileExpression(rhs, DONE)), 
				compare, 
				opcode, 
				thenPart, 
				elsePart, 
				continuation);
	}

	private void compileConditionalInverted(int compare, int opcode, IConstructor arg, Builder thenPart, Builder elsePart, Builder continuation) {
		compileConditionalInverted(
				() -> compileExpression(arg, DONE),
				compare, 
				opcode, 
				thenPart, 
				elsePart, 
				continuation);
	}

	private void compileNull(IConstructor arg, Builder thenPart, Builder elsePart, Builder continuation) {
		compileConditionalInverted(0, Opcodes.IFNONNULL, arg, thenPart, elsePart, continuation);
	}

	private void compileNonNull(IConstructor arg, Builder thenPart, Builder elsePart, Builder continuation) {
		compileConditionalInverted(0, Opcodes.IFNULL, arg, thenPart, elsePart, continuation);
	}

	private void compileNeq(IConstructor type, IConstructor lhs, IConstructor rhs, Builder thenPart, Builder elsePart, Builder continuation) {
		Switch.type0(type, 
				(z) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, lhs, rhs, thenPart, elsePart, continuation),
				(i) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, lhs, rhs, thenPart, elsePart, continuation), 
				(s) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, lhs, rhs, thenPart, elsePart, continuation), 
				(b) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, lhs, rhs, thenPart, elsePart, continuation), 
				(c) -> compileConditionalInverted(0, Opcodes.IF_ICMPEQ, lhs, rhs, thenPart, elsePart, continuation), 
				(f) -> compileConditionalInverted(Opcodes.FCMPG, Opcodes.IFEQ, lhs, rhs, thenPart, elsePart, continuation),
				(d) -> compileConditionalInverted(Opcodes.DCMPG, Opcodes.IFEQ, lhs, rhs, thenPart, elsePart, continuation),
				(l) -> compileConditionalInverted(Opcodes.LCMP, Opcodes.IFEQ, lhs, rhs, thenPart, elsePart, continuation),
				(v) -> { throw new IllegalArgumentException(">= on void"); }, 
				(c) -> compileConditionalInverted(0, Opcodes.IF_ACMPEQ, lhs, rhs, thenPart, elsePart, continuation), 
				(a) -> compileConditionalInverted(0, Opcodes.IF_ACMPEQ, lhs, rhs, thenPart, elsePart, continuation),
				(S) -> compileConditionalInverted(0, Opcodes.IF_ACMPEQ, lhs, rhs, thenPart, elsePart, continuation)
				);
	}

	private void compileCoerce(IConstructor from, IConstructor to, IConstructor arg) {
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
	}

	private void coerceFromString(IConstructor from, IConstructor to, IConstructor arg) {
		Switch.type0(to, 
				(z) -> {
					compileExpression(arg, DONE);
					method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "parseBoolean", Signature.stringType, false);
				}, 
				(i) -> {
					compileExpression(arg, DONE);
					method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "I", false);
				}, 
				(s) -> {
					compileExpression(arg, DONE);
					method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "parseShort", "S", false);
				},
				(b) -> {
					compileExpression(arg, DONE);
					method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "parseByte", "B", false);
				}, 
				(c) -> failedCoercion("string", to), 
				(f) -> {
					compileExpression(arg, DONE);
					method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "parseFloat", "F", false);
				}, 
				(d) -> {
					compileExpression(arg, DONE);
					method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "parseDouble", "D", false);
				}, 
				(j) -> {
					compileExpression(arg, DONE);
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
					compileExpression(arg, DONE);
					method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
				}
				);
	}

	private void coerceFromClass(IConstructor from, IConstructor to, IConstructor arg) {
		String cls = AST.$getName(from);

		Switch.type0(to,
				(z) -> {
					if (cls.equals("java/lang/Boolean")) {
						compileExpression(from, DONE);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "booleanValue", "()Z", false);
					}
					else {
						failedCoercion(cls, to);
					}
				},
				(i) -> {
					if (cls.equals("java/lang/Integer")) {
						compileExpression(from, DONE);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "intValue", "()I", false);
					}
					else {
						failedCoercion(cls, to);
					}
				},
				(s) -> {
					if (cls.equals("java/lang/Integer")) {
						compileExpression(from, DONE);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "shortValue", "()S", false);
					}
					else {
						failedCoercion(cls, to);
					}
				},
				(b) -> {
					if (cls.equals("java/lang/Integer")) {
						compileExpression(from, DONE);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "byteValue", "()B", false);
					}
					else {
						failedCoercion(cls, to);
					}
				},
				(c) -> failedCoercion(cls, arg),
				(f) -> {
					if (cls.equals("java/lang/Float")) {
						compileExpression(from, DONE);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Float", "floatValue", "()F", false);
					}
					else {
						failedCoercion(cls, to);
					}
				},
				(d) -> {
					if (cls.equals("java/lang/Double")) {
						compileExpression(from, DONE);
						method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Double", "doubleValue", "()D", false);
					}
					else {
						failedCoercion(cls, to);
					}
				},
				(l) -> {
					if (cls.equals("java/lang/Long")) {
						compileExpression(from, DONE);
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
					compileExpression(arg, DONE);
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
					compileExpression(arg, DONE);
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
					compileExpression(arg, DONE);
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
					compileExpression(arg, DONE);
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
					compileExpression(arg, DONE);
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
					compileExpression(arg, DONE);
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
					compileExpression(arg, DONE);
					method.visitMethodInsn(Opcodes.INVOKESPECIAL, Signature.objectName, "toString", "()V", false);
				}
				);
	}

	private void compileFalse() {
		method.visitInsn(Opcodes.ICONST_0);
	}

	private void compileTrue() {
		method.visitInsn(Opcodes.ICONST_1);
	}

	private void compileNull() {
		method.visitInsn(Opcodes.ACONST_NULL);
	}

	private void compileBlock(IList block, IConstructor arg, Builder continuation) {
		compileStatements(block, () -> compileExpression(arg, continuation));
	}

	private void compileInstanceof(IConstructor arg, String cls) {
		compileExpression(arg, DONE);
		method.visitTypeInsn(Opcodes.INSTANCEOF, cls);
	}

	private void compileGetField(IConstructor receiver, String cls, IConstructor type, String name) {
		compileExpression(receiver, DONE);
		method.visitFieldInsn(Opcodes.GETFIELD, cls, name, Signature.type(type));
	}

	private void compileExpression_NewInstance(IConstructor exp) {
		compileExpressionList(AST.$getArgs(exp), DONE);
		String cls = AST.$getClass(exp, classNode.name);
		String desc = Signature.constructor(AST.$getDesc(exp));
		method.visitTypeInsn(Opcodes.NEW, cls);
		dup();
		method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, "<init>", desc, false);
	}

	private void compileExpression_AALoad(IConstructor array, IConstructor index) {
		compileExpression(array, DONE);
		compileExpression(index, DONE);
		method.visitInsn(Opcodes.AALOAD);
	}

	private void compileGetStatic(String cls, IConstructor type, String name) {
		method.visitFieldInsn(Opcodes.GETSTATIC, cls, name, Signature.type(type));
	}

	private void compileInvokeSpecial(String cls, IConstructor sig, IConstructor receiver, IList args) {
		compileExpression(receiver, () -> compileExpressionList(args, DONE));

		method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, AST.$getName(sig), Signature.method(sig), false);
	}

	private void compileInvokeVirtual(String cls, IConstructor sig, IConstructor receiver, IList args) {
		compileExpression(receiver, () -> compileExpressionList(args, DONE));

		method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cls, AST.$getName(sig), Signature.method(sig), false);
	}

	private void compileInvokeInterface(String interf, IConstructor sig, IConstructor receiver, IList args) {
		compileExpression(receiver, () -> compileExpressionList(args, DONE));

		method.visitMethodInsn(Opcodes.INVOKEINTERFACE, interf, AST.$getName(sig), Signature.method(sig), false);
	}

	private void compileExpressionList(IList args, Builder continuation) {
		if (args.length() == 0) {
			continuation.build();
		}
		else {
			compileExpression((IConstructor) args.get(0), () -> compileExpressionList(args.delete(0), continuation));
		}
	}

	private void compileInvokeStatic(String cls, IConstructor sig, IList args) {
		compileExpressionList(args, DONE);

		method.visitMethodInsn(Opcodes.INVOKESTATIC, cls, AST.$getName(sig), Signature.method(sig), false);
	}

	private void compileExpression_Load(String name) {
		int pos = positionOf(name);

		Switch.type(variableTypes[pos], pos,
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

	private void compileExpression_Const(IConstructor type, IValue constant) {
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

		String signature = Signature.type(AST.$getType(cons));

		Object value = null;
		if (kws.hasParameter("default")) {
			value = compileValueConstant(AST.$getDefaultParameter(kws));
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

	/**
	 * Converts an arbitary IValue to some arbitrary JVM constant object.
	 * For integers and reals it does something meaningful, for all the other
	 * objects it returns the standard string representation of a value. For
	 * external values it returns 'null';
	 * @param parameter
	 * @return Integer, Double or String object
	 */
	private Object compileValueConstant(IValue parameter) {
		return parameter.getType().accept(new ITypeVisitor<Object, RuntimeException>() {

			@Override
			public Object visitAbstractData(Type arg0) throws RuntimeException {
				if ("null".equals(AST.$getConstructorName(parameter))) {
					return null;
				}

				return parameter.toString();
			}

			@Override
			public Object visitAlias(Type arg0) throws RuntimeException {
				return arg0.getAliased().accept(this);
			}

			@Override
			public Object visitBool(Type arg0) throws RuntimeException {
				return new Boolean(AST.$getBoolean(parameter));
			}

			@Override
			public Object visitConstructor(Type arg0) throws RuntimeException {
				if ("null".equals(AST.$getConstructorName(parameter))) {
					return null;
				}
				return parameter.toString();
			}

			@Override
			public Object visitDateTime(Type arg0) throws RuntimeException {
				return parameter.toString();
			}

			@Override
			public Object visitExternal(Type arg0) throws RuntimeException {
				return null;
			}

			@Override
			public Object visitInteger(Type arg0) throws RuntimeException {
				return new Integer(AST.$getIntegerConstant(parameter));
			}


			@Override
			public Object visitList(Type arg0) throws RuntimeException {
				return parameter.toString();
			}

			@Override
			public Object visitMap(Type arg0)  {
				return parameter.toString();
			}

			@Override
			public Object visitNode(Type arg0)  {
				return parameter.toString();
			}

			@Override
			public Object visitNumber(Type arg0)  {
				return new Double(AST.$getDouble(parameter));
			}

			@Override
			public Object visitParameter(Type arg0)  {
				return null;
			}

			@Override
			public Object visitRational(Type arg0)  {
				return new Double(AST.$getDouble(parameter));
			}

			@Override
			public Object visitReal(Type arg0)  {
				return new Double(((IReal) parameter).doubleValue());
			}

			@Override
			public Object visitSet(Type arg0)  {
				return parameter.toString();
			}

			@Override
			public Object visitSourceLocation(Type arg0)  {
				return ((ISourceLocation) parameter).getURI().toASCIIString();
			}

			@Override
			public Object visitString(Type arg0)  {
				return AST.$string(parameter);
			}

			@Override
			public Object visitTuple(Type arg0)  {
				return parameter.toString();
			}

			@Override
			public Object visitValue(Type arg0)  {
				return parameter.toString();
			}

			@Override
			public Object visitVoid(Type arg0)  {
				return null;
			}
		});
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

	public static IConstructor $getNext(IConstructor stat) {
		return (IConstructor) stat.get("next");
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

	public static String $getClass(IValue parameter, String currentClass) {
		String result = ((IString) ((IConstructor) parameter).get("class")).getValue().replace('.', '/');

		if ("<current>".equals(result)) {
			return currentClass;
		}

		return result;
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
		case "classType" :
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
		case "classType" :
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
		case "classType" :
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
}
