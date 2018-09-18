package lang.mujava.internal;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.objectweb.asm.ClassWriter;
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
	
	private static class Compile {
		private final ClassWriter cw;
		private final int version;
		@SuppressWarnings("unused") // needed for debug prints
		private final PrintWriter out;
		private IList varFormals;

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
				classNode.superName = "java.lang.Object";
			}
	
			if (kws.hasParameter("interfaces")) {
				ArrayList<String> interfaces = new ArrayList<String>();
				for (IValue v : AST.$getInterfaces(kws)) {
					interfaces.add(AST.$string(v));
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
			varFormals = AST.$getFormals(cons);
			
			if (sigFormals.length() != varFormals.length()) {
				throw new IllegalArgumentException("type signature of " + name + " has different number of types (" + sigFormals.length() + ") from formal parameters (" + varFormals.length() + "), see: " + sigFormals + " versus " + varFormals);
			}
			
			MethodNode mn = new MethodNode(modifiers, name, Signature.method(sig), null, null);
			compileBlock(mn, AST.$getBlock(cons));
			
			// TODO: add instructions, try catch, variables
			classNode.methods.add(mn);
		}

		private void compileBlock(MethodNode mn, IConstructor block) {
			mn.visitCode();
			compileVariables(mn, AST.$getVariables(block));
			compileStatements(mn, AST.$getStatements(block));
			mn.visitEnd();
		}

		private void compileStatements(MethodNode mn, IList statements) {
			for (IValue elem : statements) {
				IConstructor stat = (IConstructor) elem;
				compileStatement(mn, stat);
			}
		}

		private void compileStatement(MethodNode mn, IConstructor stat) {
			switch (stat.getConstructorType().getName()) {
			case "stdout" : compileStdout(mn, (IConstructor) stat.get("e"));
			case "return" : mn.visitInsn(Opcodes.RETURN);
			}
		}

		private void compileStdout(MethodNode mn, IConstructor arg) {
			getStatic(mn, System.class, "out", PrintStream.class);
			compileExpression(mn, arg);
			invokeVirtual(mn, PrintStream.class, "println", null, Object.class);
		}
		
		private void compileExpression(MethodNode mn, IConstructor exp) {
			switch (exp.getConstructorType().getName()) {
			case "const" : compileExpression_Const(mn, AST.$getType(exp), AST.$getConstant(exp));
			case "loadParameter" : compileExpression_Parameter(mn, AST.$getType(exp), AST.$getName(exp));
			default: 
				System.err.println("ignoring unknown expression kind " + exp);                                     
			}
		}

		private void compileExpression_Parameter(MethodNode mn, IConstructor type, String name) {
			int pos = positionOf(varFormals, name);
			
			switch (type.getConstructorType().getName()) {
			case "integer": 
			case "byte":
			case "character":
				intConstant(mn, pos);
				mn.visitInsn(Opcodes.ILOAD);
				break;
			case "float":
				intConstant(mn, pos);
				mn.visitInsn(Opcodes.FLOAD);
				break;
			case "long":
				intConstant(mn, pos);
				mn.visitInsn(Opcodes.LLOAD);
				break;
			default:
				mn.visitVarInsn(Opcodes.ALOAD, pos);
			}
		}

		private void intConstant(MethodNode mn, int constant) {
			switch (constant) {
			case 0: mn.visitInsn(Opcodes.ICONST_0); break;
			case 1: mn.visitInsn(Opcodes.ICONST_1); break;
			case 2: mn.visitInsn(Opcodes.ICONST_2); break;
			case 3: mn.visitInsn(Opcodes.ICONST_3); break;
			case 4: mn.visitInsn(Opcodes.ICONST_4); break;
			case 5: mn.visitInsn(Opcodes.ICONST_5); break;
			default: mn.visitIntInsn(Opcodes.BIPUSH, constant);
			}
		}

		private int positionOf(IList varFormals, String name) {
			int pos = 0;
			
			for (IValue elem : varFormals) {
				IConstructor var = (IConstructor) elem;
				if (AST.$getName(var).equals(name)) {
					return pos;
				}
				
				pos++;
			}
			
			throw new IllegalArgumentException("name not found: " + name);
		}

		private void compileExpression_Const(MethodNode mn, IConstructor type, IValue constant) {
			mn.visitInsn(Opcodes.ICONST_0); // TODO
		}

		private void getStatic(MethodNode mn, Class<?> cls, String name, Class<?> type) {
			mn.visitFieldInsn(Opcodes.GETSTATIC, cls.getCanonicalName().replaceAll("\\.","/"), "out", Signature.type(type));
		}
		
		private void invokeVirtual(MethodNode mn, Class<?> cls, String name, Class<?> ret, Class<?>... formals) {
			mn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cls.getCanonicalName().replaceAll("\\.","/"), name, Signature.method(ret, formals), false);
		}

		@SuppressWarnings("unused")
		private void swap(MethodNode mn) {
			mn.visitInsn(Opcodes.SWAP);
		}

		@SuppressWarnings("unused")
		private void dup(MethodNode mn) {
			mn.visitInsn(Opcodes.DUP);
		}

		private void compileVariables(MethodNode mn, IList variables) {
			for (IValue elem : variables) {
				IConstructor var = (IConstructor) elem;
				compileVariable(mn, var);
			}
			
		}

		private void compileVariable(MethodNode mn, IConstructor var) {
			// TODO Auto-generated method stub
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
				case "private": return Opcodes.ACC_PUBLIC;
				case "protected": return Opcodes.ACC_PUBLIC;
				}
			}

			return 0;
		}
		
		private int compileModifiers(ISet modifiers) {
			int res = 0;
			for (IValue cons : modifiers) {
				switch (((IConstructor) cons).getName()) {
				case "public": res += Opcodes.ACC_PUBLIC; break;
				case "private": res +=  Opcodes.ACC_PUBLIC; break;
				case "protected": res += Opcodes.ACC_PUBLIC; break;
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

		
		private static String method(Class<?> ret, Class<?>... formals) {
			StringBuilder val = new StringBuilder();
			val.append("(");
			for (Class<?> formal : formals) {
				val.append(type(formal));
			}
			val.append(")");
			val.append(ret == null ? "V" : type(ret));
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
		
		private static String type(Class<?> type) {
			return "L" + type.getCanonicalName().replaceAll("\\.", "/") + ";";
		}
	}
	
	/**
	 * Wrappers to get stuff out of the Class ASTs
	 */
	private static class AST {

		public static IValue $getConstant(IConstructor exp) {
			return exp.get("constant");
		}
		
		public static IConstructor $getReturn(IConstructor sig) {
			return (IConstructor) sig.get("return");
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
			return ((IString) kws.getParameter("super")).getValue();
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
