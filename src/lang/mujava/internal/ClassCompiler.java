package lang.mujava.internal;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.rascalmpl.uri.URIResolverRegistry;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWithKeywordParameters;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.visitors.NullVisitor;

public class ClassCompiler {
	private final IValueFactory vf;

	public ClassCompiler(IValueFactory vf) {
		this.vf = vf;
	}

	public void compile(IConstructor cls, ISourceLocation classFile, IBool enableAsserts, IConstructor version) {
		try (OutputStream output = URIResolverRegistry.getInstance().getOutputStream(classFile, false)) {
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			cls.accept(new Visitor(cw, versionCode(version)));
			output.write(cw.toByteArray());
		} catch (Throwable e) {
			// TODO better error handling
			e.printStackTrace();
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

	private static class Visitor extends NullVisitor<IValue, Throwable> {
		private final ClassWriter cw;
		private int version;

		public Visitor(ClassWriter cw, int version) {
			this.cw = cw;
			this.version = version;
		}
		
		@Override
		public IValue visitConstructor(IConstructor o) throws Throwable {
			Type cons = o.getConstructorType();
			switch (cons.getName()) {
			case "class": compileClass(o);
			default:
				throw new IllegalArgumentException(cons.getName());
			}
		}

		private void compileClass(IConstructor o) {
//			data Class
//			  = class(str name, 
//			      set[Modifier] modifiers = {\public()},
//			      str super = "java.lang.Object",
//			      list[str] interfaces = [],
//			      list[Field] fields = [], 
//			      list[Method] methods = [],
//			      list[Annotation] annotations = [],
//			      list[Class] children = [],
//			      loc source = |unknown:///|
//			    );
			ClassNode classNode = new ClassNode();
			IWithKeywordParameters<? extends IConstructor> kws = o.asWithKeywordParameters();
			
			classNode.version = version;
			classNode.access = accessCode((ISet) o.get("modifiers")); 
			classNode.name = ((IString) o.get("name")).getValue();
			classNode.signature = null; // meta-data about Java generics
			
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
