package lang.mujava.internal;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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

	public ClassCompiler(IValueFactory vf) {
		super();
	}

	public void compile(IConstructor cls, ISourceLocation classFile, IBool enableAsserts, IConstructor version, IEvaluatorContext ctx) {
		this.out = ctx.getStdOut();

		try (OutputStream output = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			new Compile(cw, AST.$getVersionCode(version), out).compileClass(cls);
			output.write(cw.toByteArray());
		} catch (Throwable e) {
			// TODO better error handling
			e.printStackTrace(out);
		}
	}

	/**
	 * The Compile class encapsulates a single run of the muJava -> JVM bytecode compiler
	 * for a single Class definition.
	 */
	private static class Compile {
		private final ClassWriter cw;
		private final int version;
		private final PrintWriter out;
		private IConstructor[] variableTypes;
		private String[] variableNames;
		private int variableCounter;
		private Label scopeStart;
		private Label scopeEnd;
		private MethodNode method;
		private IConstructor classType;

		public Compile(ClassWriter cw, int version, PrintWriter out) {
			this.cw = cw;
			this.version = version;
			this.out = out;
		}

		public void compileClass(IConstructor o) {
			ClassNode classNode = new ClassNode();
			IWithKeywordParameters<? extends IConstructor> kws = o.asWithKeywordParameters();

			classType = AST.$getType(o);
			classNode.version = version;
			classNode.name = AST.$getName(classType);
			classNode.signature = "L" + classNode.name + ";"; 

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
					interfaces.add(AST.$string(v).replaceAll("\\.","/"));
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

			if (kws.hasParameter("fields")) {
				compileMethods(classNode, AST.$getMethodsParameter(kws));
			}

			// now stream the entire class to a (hidden) bytearray
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
			String name = AST.$getName(sig);

			IList sigFormals = AST.$getFormals(sig);
			IList varFormals = AST.$getFormals(cons);
			IConstructor block = AST.$getBlock(cons);
			IList locals = AST.$getVariables(block);

			if (sigFormals.length() != varFormals.length()) {
				throw new IllegalArgumentException("type signature of " + name + " has different number of types (" + sigFormals.length() + ") from formal parameters (" + varFormals.length() + "), see: " + sigFormals + " versus " + varFormals);
			}

			method = new MethodNode(modifiers, name, Signature.method(sig), null, null);

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

				// initializing all locals to avoid confusing JVM crashes when
				// using uninitialized variables:
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
						(a) -> variableCounter++
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
							}
							);
				}
			}
		}

		private void compileBlock(IConstructor block) {
			compileVariables(AST.$getVariables(block), true);
			compileStatements(AST.$getStatements(block));
		}

		private void compileStatements(IList statements) {
			for (IValue elem : statements) {
				compileStatement((IConstructor) elem);
			}
		}

		private void compileStatement(IConstructor stat) {
			switch (stat.getConstructorType().getName()) {
			case "do" : 
				compileStat_Do(AST.$getType(stat), (IConstructor) stat.get("exp")); 
				break;
			case "store" : 
				compileStat_Store(stat); 
				break;
			case "return" : 
				compileStat_Return(stat);
				break;
			}
		}

		private void compileStat_Store(IConstructor stat) {
			String name = AST.$getName(stat);
			int pos = positionOf(name);
			compileExpression(AST.$getValue(stat));

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
					(a) -> { /* array */ method.visitVarInsn(Opcodes.ASTORE, pos); }
					);
		}

		private void compileStat_Return(IConstructor stat) {
			if (stat.getConstructorType().getArity() == 0) {
				method.visitInsn(Opcodes.RETURN);
			}
			else {
				compileExpression(AST.$getArg(stat));

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
						(a) -> { /* array */ method.visitInsn(Opcodes.ARETURN); }
						);
			}
		}

		private void compileStat_Do(IConstructor type, IConstructor exp) {
			compileExpression(exp);
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
					(a) -> pop()
					);
		}

		private void pop() {
			method.visitInsn(Opcodes.POP);
		}

		private void pop2() {
			method.visitInsn(Opcodes.POP2);
		}

		private void compileExpression(IConstructor exp) {
			switch (exp.getConstructorType().getName()) {
			case "const" : 
				compileExpression_Const(AST.$getType(exp), AST.$getConstant(exp)); 
				break;
			case "this" : 
				compileExpression_Load("this"); 
				break;
			case "newInstance":
				compileExpression_NewInstance(exp);
				break;
			case "newArray":
				compileExpression_NewArray(AST.$getType(exp), AST.$getSize(exp));
				break;
			case "alength":
				compileExpression_ALength(AST.$getArg(exp));
				break;
			case "load" : 
				compileExpression_Load(AST.$getName(exp)); 
				break;
			case "aaload" :
				compileExpression_AALoad(AST.$getArray(exp), AST.$getIndex(exp));
				break;
			case "astore" :
				// astore(Type \type, Expression array, Expression index, Expression arg)
				compileExpression_AStore(AST.$getType(exp), AST.$getArray(exp), AST.$getIndex(exp), AST.$getArg(exp));
				break;
			case "getStatic":
				compileGetStatic(AST.$getClass(exp), AST.$getType(exp), AST.$getName(exp));
				break;
			case "invokeVirtual" : 
				compileInvokeVirtual(AST.$getClass(exp), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
				break;
			case "invokeInterface" : 
				compileInvokeInterface(AST.$getClass(exp), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
				break;
			case "invokeSpecial" : 
				compileInvokeSpecial(AST.$getClass(exp), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
				break;
			case "invokeStatic" : 
				compileInvokeStatic(AST.$getClass(exp), AST.$getDesc(exp), AST.$getArgs(exp));
				break;
			case "getField":
				compileGetField(AST.$getReceiver(exp), AST.$getClass(exp), AST.$getType(exp), AST.$getName(exp));
				break;
			case "instanceof":
				compileInstanceof(AST.$getArg(exp), AST.$getClass(exp));
				break;
			case "block":
				compileBlock(AST.$getStatements(exp), AST.$getArg(exp));
				break;
			case "null":
				if (exp.getConstructorType().getArity() == 0) {
					compileNull();  // null constant
				}
				else { 
					compileNull(AST.$getArg(exp)); // null check 
				}
				break;
			case "nonnull":
				compileNonNull(AST.$getArg(exp)); // null check 
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
				compileEq(AST.$getLhs(exp), AST.$getRhs(exp));
				break;
			case "neq":
				compileNeq(AST.$getLhs(exp), AST.$getRhs(exp));
				break;
			case "le":
				compileLe(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				break;
			case "gt":
				compileGt(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				break;
			case "ge":
				compileGe(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				break;
			case "lt":
				compileLt(AST.$getType(exp), AST.$getLhs(exp), AST.$getRhs(exp));
				break;
			case "checkcast":
				compileCheckCast(AST.$getArg(exp), AST.$getType(exp));
				break;
			default: 
				throw new IllegalArgumentException("unknown expression: " + exp);                                     
			}
		}

		private void compileExpression_AStore(IConstructor type, IConstructor array, IConstructor index,
				IConstructor arg) {
			compileExpression(array);
			compileExpression(index);
			compileExpression(arg);
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
					(a) -> method.visitInsn(Opcodes.AASTORE)
					);
		}

		private void compileCheckCast(IConstructor arg, IConstructor type) {
			compileExpression(arg);
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

		private void compileExpression_ALength(IConstructor $getArg) {
			method.visitInsn(Opcodes.ARRAYLENGTH);
		}

		private void compileExpression_NewArray(IConstructor type, IConstructor size) {
			compileExpression(size);
			
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
					(c) -> method.visitTypeInsn(Opcodes.ANEWARRAY, AST.$string(AST.$getArg(type)))
					);
		}

		private void compileLt(IConstructor type, IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs);
			compileExpression(rhs);
			Label jump = new Label();
			Switch.type0(type, 
					(z) -> method.visitJumpInsn(Opcodes.IF_ICMPGE, jump),
					(i) -> method.visitJumpInsn(Opcodes.IF_ICMPGE, jump), 
					(s) -> method.visitJumpInsn(Opcodes.IF_ICMPGE, jump), 
					(b) -> method.visitJumpInsn(Opcodes.IF_ICMPGE, jump), 
					(c) -> method.visitJumpInsn(Opcodes.IF_ICMPGE, jump), 
					(f) -> { method.visitInsn(Opcodes.FCMPG); method.visitJumpInsn(Opcodes.IFGE, jump); }, 
					(d) -> { method.visitInsn(Opcodes.DCMPG); method.visitJumpInsn(Opcodes.IFGE, jump); }, 
					(l) -> { method.visitInsn(Opcodes.LCMP); method.visitJumpInsn(Opcodes.IFGE, jump); }, 
					(v) -> { throw new IllegalArgumentException("< on void"); }, 
					(c) -> { throw new IllegalArgumentException("< on class"); }, 
					(a) -> { throw new IllegalArgumentException("< on array"); }
					);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}

		private void compileLe(IConstructor type, IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs);
			compileExpression(rhs);
			Label jump = new Label();
			Switch.type0(type, 
					(z) -> method.visitJumpInsn(Opcodes.IF_ICMPGT, jump),
					(i) -> method.visitJumpInsn(Opcodes.IF_ICMPGT, jump), 
					(s) -> method.visitJumpInsn(Opcodes.IF_ICMPGT, jump), 
					(b) -> method.visitJumpInsn(Opcodes.IF_ICMPGT, jump), 
					(c) -> method.visitJumpInsn(Opcodes.IF_ICMPGT, jump), 
					(f) -> { method.visitInsn(Opcodes.FCMPG); method.visitJumpInsn(Opcodes.IFGT, jump); }, 
					(d) -> { method.visitInsn(Opcodes.DCMPG); method.visitJumpInsn(Opcodes.IFGT, jump); }, 
					(l) -> { method.visitInsn(Opcodes.LCMP); method.visitJumpInsn(Opcodes.IFGT, jump); }, 
					(v) -> { throw new IllegalArgumentException("< on void"); }, 
					(c) -> { throw new IllegalArgumentException("< on class"); }, 
					(a) -> { throw new IllegalArgumentException("< on array"); }
					);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}

		private void compileGt(IConstructor type, IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs);
			compileExpression(rhs);
			Label jump = new Label();
			Switch.type0(type, 
					(z) -> method.visitJumpInsn(Opcodes.IF_ICMPLE, jump),
					(i) -> method.visitJumpInsn(Opcodes.IF_ICMPLE, jump), 
					(s) -> method.visitJumpInsn(Opcodes.IF_ICMPLE, jump), 
					(b) -> method.visitJumpInsn(Opcodes.IF_ICMPLE, jump), 
					(c) -> method.visitJumpInsn(Opcodes.IF_ICMPLE, jump), 
					(f) -> { method.visitInsn(Opcodes.FCMPG); method.visitJumpInsn(Opcodes.IFLE, jump); }, 
					(d) -> { method.visitInsn(Opcodes.DCMPG); method.visitJumpInsn(Opcodes.IFLE, jump); }, 
					(l) -> { method.visitInsn(Opcodes.LCMP); method.visitJumpInsn(Opcodes.IFLE, jump); }, 
					(v) -> { throw new IllegalArgumentException("< on void"); }, 
					(c) -> { throw new IllegalArgumentException("< on class"); }, 
					(a) -> { throw new IllegalArgumentException("< on array"); }
					);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}

		private void compileGe(IConstructor type, IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs);
			compileExpression(rhs);
			Label jump = new Label();
			Switch.type0(type, 
					(z) -> method.visitJumpInsn(Opcodes.IF_ICMPLT, jump),
					(i) -> method.visitJumpInsn(Opcodes.IF_ICMPLT, jump), 
					(s) -> method.visitJumpInsn(Opcodes.IF_ICMPLT, jump), 
					(b) -> method.visitJumpInsn(Opcodes.IF_ICMPLT, jump), 
					(c) -> method.visitJumpInsn(Opcodes.IF_ICMPLT, jump), 
					(f) -> { method.visitInsn(Opcodes.FCMPG); method.visitJumpInsn(Opcodes.IFLT, jump); }, 
					(d) -> { method.visitInsn(Opcodes.DCMPG); method.visitJumpInsn(Opcodes.IFLT, jump); }, 
					(l) -> { method.visitInsn(Opcodes.LCMP); method.visitJumpInsn(Opcodes.IFLT, jump); }, 
					(v) -> { throw new IllegalArgumentException("< on void"); }, 
					(c) -> { throw new IllegalArgumentException("< on class"); }, 
					(a) -> { throw new IllegalArgumentException("< on array"); }
					);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}

		private void compileEq(IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs);
			compileExpression(rhs);
			Label jump = new Label();
			method.visitJumpInsn(Opcodes.IF_ICMPNE, jump);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}

		private void compileNull(IConstructor arg) {
			compileExpression(arg);
			Label jump = new Label();
			method.visitJumpInsn(Opcodes.IFNONNULL, jump);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}

		private void compileNonNull(IConstructor arg) {
			compileExpression(arg);
			Label jump = new Label();
			method.visitJumpInsn(Opcodes.IFNULL, jump);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}

		private void compileNeq(IConstructor lhs, IConstructor rhs) {
			compileExpression(lhs);
			compileExpression(rhs);
			Label jump = new Label();
			method.visitJumpInsn(Opcodes.IF_ICMPEQ, jump);
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(jump);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
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
					(a) -> coerceFromArray(from, to, arg) 
					);
		}

		private void coerceFromBool(IConstructor to, IConstructor arg) {
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
					(a) -> coerceArrayToArray(from, to, arg) 
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
					(a) -> failedCoercion("array", to)
					);
		}

		private void coerceFromClass(IConstructor from, IConstructor to, IConstructor arg) {
			String cls = AST.$getName(from);

			Switch.type0(to,
					(z) -> {
						if (cls.equals("java/lang/Boolean")) {
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "booleanValue", "()Z", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(i) -> {
						if (cls.equals("java/lang/Integer")) {
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "intValue", "()I", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(s) -> {
						if (cls.equals("java/lang/Integer")) {
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "shortValue", "()S", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(b) -> {
						if (cls.equals("java/lang/Integer")) {
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "byteValue", "()B", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(c) -> failedCoercion(cls, arg),
					(f) -> {
						if (cls.equals("java/lang/Float")) {
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Float", "floatValue", "()F", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(d) -> {
						if (cls.equals("java/lang/Double")) {
							method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Double", "doubleValue", "()D", false);
						}
						else {
							failedCoercion(cls, to);
						}
					},
					(l) -> {
						if (cls.equals("java/lang/Long")) {
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
					(a) -> failedCoercion("array", to)
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
					(a) -> failedCoercion("array", to)
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
					(a) -> failedCoercion("array", to)
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
					(a) -> failedCoercion("array", to)
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
					(a) -> failedCoercion("array", to)
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
					(a) -> failedCoercion("array", to)
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
					(a) -> failedCoercion("array", to)
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

		private void compileBlock(IList block, IConstructor arg) {
			compileStatements(block);
			compileExpression(arg);
		}

		private void compileInstanceof(IConstructor arg, String cls) {
			compileExpression(arg);
			method.visitTypeInsn(Opcodes.INSTANCEOF, cls);
		}

		private void compileGetField(IConstructor receiver, String cls, IConstructor type, String name) {
			compileExpression(receiver);
			method.visitFieldInsn(Opcodes.GETFIELD, cls, name, Signature.type(type));
		}

		private void compileExpression_NewInstance(IConstructor exp) {
			compileExpressionList(AST.$getArgs(exp));
			String cls = AST.$getClass(exp);
			String desc = Signature.method(AST.$getDesc(exp));
			method.visitTypeInsn(Opcodes.NEW, cls);
			dup();
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, "<init>", desc, false);
		}

		private void compileExpression_AALoad(IConstructor array, IConstructor index) {
			compileExpression(array);
			compileExpression(index);
			method.visitInsn(Opcodes.AALOAD);
		}

		private void compileGetStatic(String cls, IConstructor type, String name) {
			method.visitFieldInsn(Opcodes.GETSTATIC, cls, name, Signature.type(type));
		}

		private void compileInvokeSpecial(String cls, IConstructor sig, IConstructor receiver, IList args) {
			compileExpression(receiver);
			compileExpressionList(args);

			method.visitMethodInsn(Opcodes.INVOKESPECIAL, cls, AST.$getName(sig), Signature.method(sig), false);
		}

		private void compileInvokeVirtual(String cls, IConstructor sig, IConstructor receiver, IList args) {
			compileExpression(receiver);
			compileExpressionList(args);

			method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cls, AST.$getName(sig), Signature.method(sig), false);
		}

		private void compileInvokeInterface(String interf, IConstructor sig, IConstructor receiver, IList args) {
			compileExpression(receiver);
			compileExpressionList(args);

			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, interf, AST.$getName(sig), Signature.method(sig), false);
		}

		private void compileExpressionList(IList args) {
			for (IValue elem : args) {
				compileExpression((IConstructor) elem);
			}
		}

		private void compileInvokeStatic(String cls, IConstructor sig, IList args) {
			compileExpressionList(args);

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
					(a,p) -> method.visitVarInsn(Opcodes.ALOAD, p)
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
			switch(AST.$getConstructorName(type)) {
			case "integer":
			case "character":
			case "byte":
				intConstant(AST.$getIntegerConstant(constant));
				break;
			case "long":
				longConstant(AST.$getLongConstant(constant));
				break;
			case "float":
				floatConstant(AST.$getFloatConstant(constant));
				break;
			case "double":
				doubleConstant(AST.$getDoubleConstant(constant));
				break;
			case "boolean":
				booleanConstant(AST.$getBoolean(constant));
				break;
			default:
				throw new IllegalArgumentException("not supported: " + constant.toString());
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

			int access = Opcodes.ACC_PRIVATE;
			if (kws.hasParameter("modifiers")) {
				access = compileAccessCode(AST.$getModifiersParameter(kws));
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
					return AST.$getBoolean(parameter);
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
					return AST.$getIntegerConstant(parameter);
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
					return AST.$getDouble(parameter);
				}

				@Override
				public Object visitParameter(Type arg0)  {
					return null;
				}

				@Override
				public Object visitRational(Type arg0)  {
					return AST.$getDouble(parameter);
				}

				@Override
				public Object visitReal(Type arg0)  {
					return ((IReal) parameter).doubleValue();
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
	private static class Signature {
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

		private static String type(IConstructor t) {
			IConstructor type = (IConstructor) t;

			return Switch.type(type,
					(z) -> { return "Z";},
					(i) -> { return "I";},
					(s) -> { return "S";},
					(b) -> { return "B";},
					(c) -> { return "C";},
					(f) -> { return "F";},
					(d) -> { return "D";},
					(l) -> { return "J";},
					(v) -> { return "V";},
					(c) -> { return "L" + AST.$getName(c).replaceAll("\\.", "/") + ";"; },
					(a) -> {  return "[" + type(AST.$getArg(a)); }
					);
		}
	}

	/**
	 * Wrappers to get stuff out of the Class ASTs
	 */
	private static class AST {

		public static IValue $getConstant(IConstructor exp) {
			return exp.get("constant");
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

		public static String $getClass(IValue parameter) {
			return ((IString) ((IConstructor) parameter).get("class")).getValue().replaceAll("\\.", "/");
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
			return ((IString) kws.getParameter("super")).getValue().replaceAll("\\.","/");
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

	private static class Switch {
		/**
		 * Dispatch on a consumer on a type. The idea is to never accidentally forget a type using this higher-order function.
		 * @param type
		 */
		public static void type0(IConstructor type, Consumer<IConstructor> bools, Consumer<IConstructor> ints, Consumer<IConstructor> shorts, Consumer<IConstructor> bytes, Consumer<IConstructor> chars, Consumer<IConstructor> floats, Consumer<IConstructor> doubles, Consumer<IConstructor> longs, Consumer<IConstructor> voids, Consumer<IConstructor> classes, Consumer<IConstructor> arrays) {
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
			default:
				throw new IllegalArgumentException("type not supported: " + type);
			}
		}

		/**
		 * Dispatch on a function on a type. The idea is to never accidentally forget a type using this higher-order function.
		 * @param type
		 */
		public static <T> T type(IConstructor type, Function<IConstructor, T> bools, Function<IConstructor, T> ints, Function<IConstructor, T> shorts, Function<IConstructor, T> bytes, Function<IConstructor, T> chars, Function<IConstructor, T> floats, Function<IConstructor, T> doubles, Function<IConstructor, T> longs, Function<IConstructor, T> voids, Function<IConstructor, T> classes, Function<IConstructor, T> arrays) {
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
			default:
				throw new IllegalArgumentException("type not supported: " + type);
			}
		}

		/**
		 * Dispatch a consumer on a type and pass a parameter
		 * @param type
		 */
		public static <T> void type(IConstructor type, T arg,  BiConsumer<IConstructor,T> bools, BiConsumer<IConstructor,T> ints, BiConsumer<IConstructor,T> shorts, BiConsumer<IConstructor,T> bytes, BiConsumer<IConstructor,T> chars, BiConsumer<IConstructor,T> floats, BiConsumer<IConstructor,T> doubles, BiConsumer<IConstructor,T> longs, BiConsumer<IConstructor,T> voids, BiConsumer<IConstructor,T> classes, BiConsumer<IConstructor,T> arrays) {
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
			default:
				throw new IllegalArgumentException("type not supported: " + type);
			}
		}
	}
}
