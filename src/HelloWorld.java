
public class HelloWorld {
	public static void main(String[] args) {
		System.err.println(g(14)); 
	}

	private static int g(int x) {
		switch (x) {
		case 1: return 1;
		case 2: return 2;
		default: return x - 1;
		}
	}
}
