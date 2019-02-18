package lang.flybytes.internal;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.IListWriter;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISetWriter;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.objectweb.asm.ClassReader;
import org.rascalmpl.objectweb.asm.Opcodes;
import org.rascalmpl.objectweb.asm.tree.ClassNode;
import org.rascalmpl.objectweb.asm.tree.FieldNode;
import org.rascalmpl.objectweb.asm.tree.MethodNode;
import org.rascalmpl.uri.URIResolverRegistry;

/**
 * Produces a Flybytes AST from a JVM class in bytecode format, with the limitation
 * that it does not recover Expressions and Statements of the method bodies, but rather lists of Instructions
 * to be processed later by a downstream de-compilation step. 
 */
public class ClassDecompiler {
	private final IValueFactory VF;
	private final AST ast;
	
	public ClassDecompiler(IValueFactory VF) {
		this.VF = VF;
		this.ast = new AST(VF);
	}
	
	public IConstructor decompile(ISourceLocation classLoc) {
		try {
			ClassReader reader = new ClassReader(URIResolverRegistry.getInstance().getInputStream(classLoc));
			return decompile(reader);
		}
		catch (IOException e) {
			throw RuntimeExceptionFactory.io(VF.string(e.getMessage()), null, null);
		}
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
		
		if ((cn.access & Opcodes.ACC_INTERFACE) != 0) {
			return ast.Class_interface(objectType(cn.name)).asWithKeywordParameters().setParameters(params);
		}
		else {
			return ast.Class_class(objectType(cn.name)).asWithKeywordParameters().setParameters(params);
		}
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
		ISetWriter sw  = VF.setWriter();

		// mutually exclusive access bits:
		if (set(access, Opcodes.ACC_PUBLIC)) {
			sw.insert(ast.Modifier_public());
		}
		else if (set(access, Opcodes.ACC_PRIVATE)) {
			sw.insert(ast.Modifier_private());
		}
		else if (set(access, Opcodes.ACC_PROTECTED)) {
			sw.insert(ast.Modifier_protected());
		}
		else {
			sw.insert(ast.Modifier_friendly());
		}

		if (set(access, Opcodes.ACC_STATIC)) {
			sw.insert(ast.Modifier_static());
		}

		if (set(access, Opcodes.ACC_FINAL)) {
			sw.insert(ast.Modifier_final());
		}
		
		if (set(access, Opcodes.ACC_SYNCHRONIZED)) {
			sw.insert(ast.Modifier_synchronized());
		}
		
		if (set(access, Opcodes.ACC_ABSTRACT)) {
			sw.insert(ast.Modifier_abstract());
		}

		return sw.done();
	}

	private boolean set(int access, int bit) {
		return (access & bit) != 0;
	}

	private IList methods(List<MethodNode> methods) {
		// TODO Auto-generated method stub
		return VF.list();
	}

	private IList fields(List<FieldNode> fields) {
		IListWriter lw = VF.listWriter();
		
		for (FieldNode fn : fields) {
			lw.append(field(fn));
		}
		
		return lw.done();
	}

	private IValue field(FieldNode fn) {
		Map<String, IValue> params = new HashMap<>();
		
		if (fn.value != null) {
			params.put("init", initializer(fn.value));
		}
		
		params.put("modifiers", modifiers(fn.access));
		
		return ast.Field_field(type(fn.desc), fn.name).asWithKeywordParameters().setParameters(params);
	}

	private IValue initializer(Object value) {
		if (value instanceof String) {
			return ast.Exp_const(ast.Type_string(), VF.string((String) value));
		}
		
		if (value instanceof Float) {
			return ast.Exp_const(ast.Type_float(), VF.real((Float) value));
		}
		
		if (value instanceof Double) {
			return ast.Exp_const(ast.Type_double(), VF.real((Double) value));
		}
		
		if (value instanceof Byte) {
			return ast.Exp_const(ast.Type_byte(), VF.integer((Byte) value));
		}
		
		if (value instanceof Short) {
			return ast.Exp_const(ast.Type_short(), VF.integer((Short) value));
		}
		
		if (value instanceof Character) {
			return ast.Exp_const(ast.Type_character(), VF.integer((Character) value));
		}
		
		if (value instanceof Integer) {
			return ast.Exp_const(ast.Type_integer(), VF.integer((Integer) value));
		}
		
		if (value instanceof Long) {
			return ast.Exp_const(ast.Type_long(), VF.integer((Long) value));
		}
		
		return ast.Exp_null();
	}

	private IConstructor type(String desc) {
		if ("Ljava/lang/String;".equals(desc)) {
			return ast.Type_string();
		}
		
		if (desc.startsWith("L")) {
			return objectType(desc.substring(1, desc.indexOf(";")));
		}
		
		if (desc.startsWith("[")) {
			return ast.Type_array(type(desc.substring(1)));
		}
		
		if ("Z".equals(desc)) {
			return ast.Type_boolean();
		}
		
		if ("I".equals(desc)) {
			return ast.Type_integer();
		}
		
		if ("S".equals(desc)) {
			return ast.Type_short();
		}
		
		if ("B".equals(desc)) {
			return ast.Type_byte();
		}
		
		if ("C".equals(desc)) {
			return ast.Type_character();
		}
		
		if ("F".equals(desc)) {
			return ast.Type_float();
		}
		
		if ("D".equals(desc)) {
			return ast.Type_double();
		}
		
		if ("J".equals(desc)) {
			return ast.Type_long();
		}
		
		if ("V".equals(desc)) {
			return ast.Type_void();
		}
		
		throw new IllegalArgumentException("not a type descriptor: " + desc);
	}
}
