package lang.flybytes.tests;

public class RoundtripTestClass {
	int ofield1 = 1;
	double ofield2 = .1;
	static int sfield1;
	
	static {
		System.err.println("static initializer");
		sfield1 = 1;
	}
	
	int m1(int x) {
		if (x < 2) {
			x = 2;
		}
		else {
			x = 3;
		}
		
		return x;
	}
	
	int m2(int x) {
		while (x < 10) {
			x++;
		}
		
		return x;
	}
	
	int m3(int x) {
		do {
			x++;
		}
		while (x < 10);
		
		return x;
	}
	
	int m4(int x) {
		int l = 0;
		for (int i = 0; i < x; i++) {
			l += i;
		}
	
		return l;
	}
	
	int m5(int x) {
		int l = 0;
		for (int i = 0, j = 0; i < x || j < x; i++, j++) {
			l += i + j;
			System.err.println(l);
		}
	
		return l;
	}
	
	int m6(int x) {
		boolean y = true && false || true;
		if (!y) {
			System.err.println("ja!");
		}
		return 0;
	}
	
}
