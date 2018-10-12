
public class HelloWorld {
	public static void main(String[] args) {
		System.err.println(g(14)); 
	}

	private static int g(int x) {
		switch (x) {
		case 42: return 42;
		case 12: return 12;
		}
		return 0;
	}
}
