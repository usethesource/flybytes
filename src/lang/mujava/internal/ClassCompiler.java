package lang.mujava.internal;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

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
		private String[] variableTypes;
		private String[] variableNames;
		private int variableCounter;
		private Label scopeStart;
		private Label scopeEnd;
		private MethodNode method;

		public Compile(ClassWriter cw, int version, PrintWriter out) {
			this.cw = cw;
			this.version = version;
			this.out = out;
		}
		
		public void compileClass(IConstructor o) {
			ClassNode classNode = new ClassNode();
			IWithKeywordParameters<? extends IConstructor> kws = o.asWithKeywordParameters();
			
			classNode.version = version;
			classNode.name = AST.$getName(o);
			classNode.signature = null; // optional meta-data about Java generics
			
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
			variableCounter = 0;
			variableTypes = new String[varFormals.length() + locals.length()];
			variableNames = new String[varFormals.length() + locals.length()];
			scopeStart = new Label();
			scopeEnd = new Label();
			
			method.visitCode(); 
			method.visitLabel(scopeStart);
			compileVariables(varFormals);
			compileBlock(block);
			method.visitLabel(scopeEnd);
			method.visitEnd();
			
			classNode.methods.add(method);
		}

		private void compileVariables(IList formals) {
			for (IValue elem : formals) {
				IConstructor var = (IConstructor) elem;
				variableTypes[variableCounter] = Signature.type(AST.$getType(var));
				variableNames[variableCounter] = AST.$getName(var);
				method.visitLocalVariable(variableNames[variableCounter], variableTypes[variableCounter], null, scopeStart, scopeEnd, 0);
				variableCounter++;
			}
		}

		private void compileBlock(IConstructor block) {
			compileVariables(AST.$getVariables(block));
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
				compileDo(AST.$getIsVoid(stat), (IConstructor) stat.get("exp")); 
				break;
			case "return" : 
				method.visitInsn(Opcodes.RETURN);
				break;
			}
		}

		private void compileDo(boolean isVoid, IConstructor exp) {
			compileExpression(exp);
			if (!isVoid) {
				pop();
			}
		}

		private void pop() {
			method.visitInsn(Opcodes.POP);
		}

		private void compileExpression(IConstructor exp) {
			out.println(exp);
			switch (exp.getConstructorType().getName()) {
			case "const" : 
				compileExpression_Const(AST.$getType(exp), AST.$getConstant(exp)); 
				break;
			case "load" : 
				compileExpression_Load(AST.$getName(exp)); 
				break;
			case "aaload" :
				compileExpression_Index(AST.$getArray(exp), AST.$getIndex(exp));
				break;
			case "getStatic":
				compileGetStatic(AST.$getClass(exp), AST.$getType(exp), AST.$getName(exp));
				break;
			case "invokeVirtual" : 
				compileInvokeVirtual(AST.$getClass(exp), AST.$getDesc(exp), AST.$getReceiver(exp), AST.$getArgs(exp));
				break;
			case "invokeStatic" : 
				compileInvokeStatic(AST.$getClass(exp), AST.$getDesc(exp), AST.$getArgs(exp));
				break;
			default: 
				System.err.println("ignoring unknown expression kind " + exp);                                     
			}
		}

		private void compileExpression_Index(IConstructor array, IConstructor index) {
			compileExpression(array);
			compileExpression(index);
			method.visitInsn(Opcodes.AALOAD);
		}

		private void compileGetStatic(String cls, IConstructor type, String name) {
			method.visitFieldInsn(Opcodes.GETSTATIC, cls, name, Signature.type(type));
		}
		
		private void compileInvokeVirtual(String cls, IConstructor sig, IConstructor receiver, IList args) {
			compileExpression(receiver);
			
			for (IValue elem : args) {
				compileExpression((IConstructor) elem);
			}
			
			method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cls, AST.$getName(sig), Signature.method(sig), false);
		}
		
		private void compileInvokeStatic(String cls, IConstructor sig, IList args) {
			for (IValue elem :args) {
				compileExpression((IConstructor) elem);
			}
			
			method.visitMethodInsn(Opcodes.INVOKESTATIC, cls, AST.$getName(sig), Signature.method(sig), false);
		}
		
		private void compileExpression_Load(String name) {
			int pos = positionOf(name);
			
			switch (variableTypes[pos]) {
			case "integer": 
			case "byte":
			case "character":
				intConstant(pos);
				method.visitInsn(Opcodes.ILOAD);
				break;
			case "float":
				intConstant(pos);
				method.visitInsn(Opcodes.FLOAD);
				break;
			case "long":
				intConstant(pos);
				method.visitInsn(Opcodes.LLOAD);
				break;
			default:
				method.visitVarInsn(Opcodes.ALOAD, pos);
			}
		}

		private void intConstant(int constant) {
			switch (constant) {
			case 0: method.visitInsn(Opcodes.ICONST_0); break;
			case 1: method.visitInsn(Opcodes.ICONST_1); break;
			case 2: method.visitInsn(Opcodes.ICONST_2); break;
			case 3: method.visitInsn(Opcodes.ICONST_3); break;
			case 4: method.visitInsn(Opcodes.ICONST_4); break;
			case 5: method.visitInsn(Opcodes.ICONST_5); break;
			default: method.visitIntInsn(Opcodes.BIPUSH, constant);
			}
		}

		private int positionOf(String name) {
			for (int pos = 0; pos < variableNames.length; pos++) {
				if (variableNames[pos].equals(name)) {
					return pos;
				}
			}
			
			throw new IllegalArgumentException("name not found: " + name);
		}

		private void compileExpression_Const(IConstructor type, IValue constant) {
			switch(AST.$getConstructorName(type)) {
			case "integer":
				intConstant(AST.$getInteger(constant));
				break;
			default:
				throw new IllegalArgumentException("not supported: " + constant.toString());
			}
		}

		@SuppressWarnings("unused")
		private void swap() {
			method.visitInsn(Opcodes.SWAP);
		}

		@SuppressWarnings("unused")
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
				case "public": res += Opcodes.ACC_PUBLIC; break;
				case "private": res +=  Opcodes.ACC_PRIVATE; break;
				case "protected": res += Opcodes.ACC_PROTECTED; break;
				case "static": res += Opcodes.ACC_STATIC; break;
				case "final": res += Opcodes.ACC_FINAL; break;
				case "abstract": res += Opcodes.ACC_ABSTRACT; break;
				case "interface": res += Opcodes.ACC_INTERFACE; break;
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
					return AST.$getInteger(parameter);
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
			
			switch (type.getConstructorType().getName()) {
			case "byte": return "B";
			case "short": return "S";
			case "character": return "C";
			case "integer": return "I";
			case "float" : return "F";
			case "double" : return "D";
			case "long" : return "J";
			case "void" : return "V";
			case "classType" : return "L" + AST.$getName(type).replaceAll("\\.", "/") + ";";
			case "array" : return "[" + type(AST.$getArg(type));
			default:
				throw new IllegalArgumentException(type.toString());
			}
		}
	}
	
	/**
	 * Wrappers to get stuff out of the Class ASTs
	 */
	private static class AST {

		public static IValue $getConstant(IConstructor exp) {
			return exp.get("constant");
		}
		
		public static IConstructor $getIndex(IConstructor exp) {
			return (IConstructor) exp.get("index");
		}

		public static IConstructor $getArray(IConstructor exp) {
			return (IConstructor) exp.get("array");
		}

		public static boolean $getIsVoid(IConstructor exp) {
			return ((IBool) exp.get("isVoid")).getValue();
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

		public static int $getInteger(IValue parameter) {
			return ((IInteger) parameter).intValue();
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
}
