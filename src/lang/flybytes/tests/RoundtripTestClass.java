package lang.flybytes.tests;

public class RoundtripTestClass {
	int ofield1 = 1;
	double ofield2 = .1;
	static int sfield1;
	
	static {
		System.err.println("static initializer");
		sfield1 = 1;
	}
	
	int simpleIfThenElse(int x) {
		if (x < 2) {
			x = 2;
		}
		else {
			x = 3;
		}
		
		return x;
	}
	
	int simpleWhileLoop(int x) {
		while (x < 10) {
			x++;
		}
		
		return x;
	}
	
	int simpleDoWhileLoop(int x) {
		do {
			x++;
		}
		while (x < 10);
		
		return x;
	}
	
	int simpleForLoop(int x) {
		int l = 0;
		for (int i = 0; i < x; i++) {
			l += i;
		}
	
		return l;
	}
	
	int forLoopWithMultipleWalkers(int x) {
		int l = 0;
		for (int i = 0, j = 0; i < x || j < x; i++, j++) {
			l += i + j;
			System.err.println(l);
		}
	
		return l;
	}
	
	int simpleThrow(int x) throws Exception {
		throw new Exception("catch!");
	}
	
	int simpleSwitchWithDefault(int x) throws Exception {
		switch(x) {
		case 0:
			x = 1;
			break;
		case 1:
			x = 2;
			break;
			// fallthrough
		case 2:
			x = 3;
			break;
		case 3:
			x = 4;
			break;
		default:
			x = 5;
		}
		
		return x;
	}
	
	int simpleSwitchWithoutDefault(int x) throws Exception {
		switch(x) {
		case 0:
			x = 1;
			break;
		case 1:
			x = 2;
			break;
		case 2:
			x = 3;
			break;
		case 3:
			x = 4;
			break;
		}
		
		return x;
	}
	
	int switchWithFallThrough(int x) throws Exception {
		switch(x) {
		case 0:
			x = 1;
			break;
		case 1:
			x = 2;
			// fallthrough
		case 2:
			x = 3;
			break;
		}
		
		return x;
	}
	
	int switchWithEmptyCase(int x) throws Exception {
		switch(x) {
		case 0:
			x = 1;
			break;
		case 1:
		case 2:
			x = 2;
			break;
		}
		
		return x;
	}
	
	int switchWithLastEmptyCase(int x) throws Exception {
		switch(x) {
		case 0:
			x = 1;
			break;
		case 1:
		case 2:
			x = 2;
			break;
		case 3:
		}
		
		return x;
	}
	
	int switchWithLastEmptyCaseToDefault(int x) throws Exception {
		switch(x) {
		case 0:
			x = 1;
			break;
		case 1:
		case 2:
			x = 2;
			break;
		case 3:
		default:
			x = 3;
		}
		
		return x;
	}
	
	int switchWithOnlyFallThroughs(int x) throws Exception {
		switch(x) {
		case 0:
			x++;
		case 1:
			x++;
		case 2:
			x++;
		case 3:
			x++;
		}
		
		return x;
	}

	
}
