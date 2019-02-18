package lang.flybytes.internal;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rascalmpl.objectweb.asm.ClassReader;
import org.rascalmpl.objectweb.asm.tree.ClassNode;
import org.rascalmpl.objectweb.asm.tree.FieldNode;
import org.rascalmpl.objectweb.asm.tree.MethodNode;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.ValueFactoryFactory;

/**
 * Produces a Flybytes AST from a JVM class in bytecode format, with the limitation
 * that it does not recover Expressions and Statements of the method bodies, but rather lists of Instructions
 * to be processed later by a downstream decompilation step. 
 */
public class ClassDecompiler {
	private final IValueFactory VF = ValueFactoryFactory.getValueFactory();
	private final AST ast = new AST(VF);
	
	public IConstructor decompile(ISourceLocation classLoc) throws IOException {
		ClassReader reader = new ClassReader(URIResolverRegistry.getInstance().getInputStream(classLoc));
		return decompile(reader);
	}
	
	private IConstructor decompile(ClassReader reader) {
		ClassNode cn = new ClassNode();
		reader.accept(cn, ClassReader.SKIP_FRAMES);
		Map<String, IValue> params = new HashMap<>();
		
		params.put("fields", fields(cn.fields));
		params.put("methods", methods(cn.methods));
		params.put("modifiers", modifiers(cn.access));
		params.put("super", objectType(cn.superName));
		params.put("interfaces", interfaces(cn.interfaces));
		
		return ast.Class_class(objectType(cn.name)).asWithKeywordParameters().setParameters(params);
	}

	private IList interfaces(List<String> interfaces) {
		IListWriter w = VF.listWriter();
		
		for (String iface : interfaces) {
			w.append(objectType(iface));
		}
		
		return w.done();
	}

	private IConstructor objectType(String name) {
		return ast.Type_object(name.replaceAll("/", "."));
	}

	private ISet modifiers(int access) {
		return VF.set();
	}

	private IList methods(List<MethodNode> methods) {
		// TODO Auto-generated method stub
		return VF.list();
	}

	private IList fields(List<FieldNode> fields) {
		return VF.list();
	}
}
