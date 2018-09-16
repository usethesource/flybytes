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
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWithKeywordParameters;

public class ClassCompiler {
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
				classNode.fields = compileFields((IList) kws.getParameter("fields"));
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

		private List<FieldNode> compileFields(IList fields) {
			// TODO Auto-generated method stub
			return Collections.emptyList();
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
