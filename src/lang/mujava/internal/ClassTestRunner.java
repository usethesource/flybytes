package lang.mujava.internal;

import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValueFactory;

public class ClassTestRunner {
	private final IValueFactory vf;
	
	public ClassTestRunner(IValueFactory vf) {
		this.vf = vf;
	}
	
	public void runTests(ISourceLocation classfile, IList classpath) {
		throw new RuntimeException("not implemented");
	}
}
