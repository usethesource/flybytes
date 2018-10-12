
public class HelloWorld {
	public static void main(String[] args) {
		System.err.println(g()); 
	}

	private static int g() {
		int x = 2;
		switch (x) {
		case 0: x = 3;
		case 1: return 2;
		case 2: return 3;
		default: return 4;
		}
	}
}
