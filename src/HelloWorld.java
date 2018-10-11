
public class HelloWorld {
	public static void main(String[] args) {
		System.err.println(g()); // prints "2"
	}

	@SuppressWarnings("finally")
	private static int g() {
		int i = 0;
		try {
			try {
				System.err.println("step 1");
				throw new IllegalArgumentException();
			}
			catch (IllegalArgumentException e) {
				return 1;
			}
			finally {
				System.err.println("step 2");
				++i;
				// LOAD of i is done before RETURN opcode is executed (then i == 2)
				// Then the FINALLY code is splices 
				return 2; 
			}
		} finally {
			System.err.println("step 3");
		}
	}
}
