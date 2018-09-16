package lang.mujava.internal;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;

public class ClassRunner {
	private final IValueFactory vf;

	public ClassRunner(IValueFactory vf) {
		this.vf = vf;
	}

	public void runMain(ISourceLocation classfile, IList args, IList classpath) {
		throw new RuntimeException("not implemented");
	}
}
