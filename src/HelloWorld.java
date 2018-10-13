
public class HelloWorld {
	public static void main(String[] args) {
		System.err.println(g(14)); 
	}

	private static int g(int x) {
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
