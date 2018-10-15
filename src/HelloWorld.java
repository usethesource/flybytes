import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

@SupportedSourceVersion(SourceVersion.RELEASE_0)
public class HelloWorld {
	public static void main(String[] args) {
//		System.err.println(g(14)); 
	}

	@Deprecated
	int f = 0;
	
	private static int g(int x ) {
		@Deprecated
		int K = 0;
		for (int i = 0; i < 10; i++) {
			synchronized(new Object()) {
				if (i % 2 == 0) {
					break;
				}
			}
		}
		return x;
	}
}
