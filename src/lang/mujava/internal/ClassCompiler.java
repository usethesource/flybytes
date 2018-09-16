package lang.mujava.internal;

import io.usethesource.vallang.IBool;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;

public class ClassCompiler {
	private final IValueFactory vf;

	public ClassCompiler(IValueFactory vf) {
		this.vf = vf;
	}

	public void compile(IConstructor cls, ISourceLocation classfile, IBool enableAsserts, IConstructor version) {
		throw new RuntimeException("not implemented");
	}
}
