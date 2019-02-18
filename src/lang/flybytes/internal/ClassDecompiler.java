package lang.flybytes.internal;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;

import java.io.IOException;

import org.rascalmpl.objectweb.asm.ClassReader;
import org.rascalmpl.objectweb.asm.tree.ClassNode;
import org.rascalmpl.uri.URIResolverRegistry;

/**
 * Produces a Flybytes AST from a JVM class in bytecode format, with the limitation
 * that it does not recover Expressions and Statements of the method bodies, but rather lists of Instructions
 * to be processed later by a downstream decompilation step. 
 */
public class ClassDecompiler {

	public IConstructor decompile(ISourceLocation classLoc) throws IOException {
		ClassReader reader = new ClassReader(URIResolverRegistry.getInstance().getInputStream(classLoc));
		return decompile(reader);
	}
	
	private IConstructor decompile(ClassReader reader) {
		ClassNode cn = new ClassNode();
		reader.accept(cn, ClassReader.SKIP_FRAMES);
		
	}
	
	
}
