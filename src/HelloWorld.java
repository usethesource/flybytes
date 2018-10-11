
public class HelloWorld {
	public static void main(String[] args) {
		System.err.println(g()); 
	}

	private static int g() {
		int j = 0;
		for (int i = 0; i < 10; i++) {
			try {
				System.err.println("j:" + j);
				continue;
			}
			finally {
				j++;
			}
		}
		return j;
	}
}
