package lang.mujava.internal;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import io.usethesource.vallang.IRational;
import io.usethesource.vallang.IReal;
import io.usethesource.vallang.INumber;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.ITypeVisitor;
import io.usethesource.vallang.type.Type;

public class ClassCompiler {
	@SuppressWarnings("unused")
	private final IValueFactory vf;
	private PrintWriter out;

	public ClassCompiler(IValueFactory vf) {
		this.vf = vf;
	}

	public void compile(IConstructor cls, ISourceLocation classFile, IBool enableAsserts, IConstructor version, IEvaluatorContext ctx) {
		this.out = ctx.getStdOut();
		
		try (OutputStream output = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			new SingleClassCompiler(cw, versionCode(version), out).compileClass(cls);
			output.write(cw.toByteArray());
		} catch (Throwable e) {
			// TODO better error handling
			e.printStackTrace(out);
		}
	}
	
	private int versionCode(IConstructor version) {
		switch (version.getConstructorType().getName()) {
		case "v1_6": return Opcodes.V1_6;
		case "v1_7": return Opcodes.V1_7;
		case "v1_8": return Opcodes.V1_8;
		default:
			throw new IllegalArgumentException(version.toString());
		}
	}

	private static class SingleClassCompiler {
		private final ClassWriter cw;
		private final int version;
		@SuppressWarnings("unused") // needed for debug prints
		private final PrintWriter out;

		public SingleClassCompiler(ClassWriter cw, int version, PrintWriter out) {
			this.cw = cw;
			this.version = version;
			this.out = out;
		}
		
		public void compileClass(IConstructor o) {
			ClassNode classNode = new ClassNode();
			IWithKeywordParameters<? extends IConstructor> kws = o.asWithKeywordParameters();
			
			classNode.version = version;
			classNode.name = ((IString) o.get("name")).getValue();
			classNode.signature = null; // optional meta-data about Java generics
			
			if (kws.hasParameter("modifiers")) {
				classNode.access = accessCode((ISet) o.get("modifiers"));
			}
			else {
				classNode.access = Opcodes.ACC_PUBLIC;
			}
			
			if (kws.hasParameter("super")) {
				classNode.superName = ((IString) kws.getParameter("super")).getValue();
			}
			else {
				classNode.superName = "java.lang.Object";
			}
	
			if (kws.hasParameter("interfaces")) {
				ArrayList<String> interfaces = new ArrayList<String>();
				for (IValue v : ((IList) kws.getParameter("interfaces"))) {
					interfaces.add(((IString) v).getValue());
				}
				classNode.interfaces = interfaces;
			}
			
			if (kws.hasParameter("source")) {
				classNode.sourceFile = kws.getParameter("source").toString();
			}
			else {
				classNode.sourceFile = null;
			}
			
			if (kws.hasParameter("fields")) {
				compileFields(classNode, (IList) kws.getParameter("fields"));
			}
			
			if (kws.hasParameter("fields")) {
				classNode.methods = compileMethods((IList) kws.getParameter("methods"));
			}
			
			classNode.accept(cw);
		}

		private List<MethodNode> compileMethods(IList methods) {
			// TODO Auto-generated method stub
			return Collections.emptyList();
		}

		private void compileFields(ClassNode classNode, IList fields) {
			for (IValue field : fields) {
				IConstructor cons = (IConstructor) field;
				IWithKeywordParameters<? extends IConstructor> kws = cons.asWithKeywordParameters();
				//  = field(Type \type, 
				//         str name, 
				//         Expression \default = \null(), 
				//         set[Modifier] modifiers = {\private()});
				int access;
				if (kws.hasParameter("modifiers")) {
					access = accessCode((ISet) kws.getParameter("modifiers"));
				}
				else {
					access = Opcodes.ACC_PRIVATE;
				}
				
				String name = ((IString) cons.get("name")).getValue();
				
				String signature = signature((IConstructor) cons.get("type"));
				
				Object value = null;
				if (kws.hasParameter("default")) {
					value = value(kws.getParameter("default"));
				}
				
				classNode.fields.add(new FieldNode(access, name, signature, null, value));
			}
		}

		private Object value(IValue parameter) {
			return parameter.getType().accept(new ITypeVisitor<Object, RuntimeException>() {

				@Override
				public Object visitAbstractData(Type arg0) throws RuntimeException {
					if ("null".equals(((IConstructor) parameter).getConstructorType().getName())) {
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
					return ((IBool) parameter).getValue();
				}

				@Override
				public Object visitConstructor(Type arg0) throws RuntimeException {
					if ("null".equals(((IConstructor) parameter).getConstructorType().getName())) {
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
					return ((IInteger) parameter).intValue();
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
					return ((INumber) parameter).toReal(10).doubleValue();
				}

				@Override
				public Object visitParameter(Type arg0)  {
					return null;
				}

				@Override
				public Object visitRational(Type arg0)  {
					return ((IRational) parameter).toReal(10).doubleValue();
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
					return ((IString) parameter).getValue();
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

		private String signature(IConstructor type) {
			switch (type.getConstructorType().getName()) {
			case "byte": return "B";
			case "short": return "S";
			case "character": return "C";
			case "integer": return "I";
			case "float" : return "F";
			case "double" : return "D";
			case "long" : return "J";
			case "void" : return "V";
			case "classType" : return "L" + ((IString) type.get("name")).getValue() + ";";
			case "array" : return "[" + signature((IConstructor) type.get("arg"));
			default:
				throw new IllegalArgumentException(type.toString());
			}
		}

		private int accessCode(ISet modifiers) {
			for (IValue cons : modifiers) {
				switch (((IConstructor) cons).getName()) {
				case "public": return Opcodes.ACC_PUBLIC;
				case "private": return Opcodes.ACC_PUBLIC;
				case "protected": return Opcodes.ACC_PUBLIC;
				}
			}

			return 0;
		}
	}
}
