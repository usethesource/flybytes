package lang.flybytes.tests.examples;

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
	
	int lotsOfDecls() {
		int x = 1;
		int y = x + 1;
		int z = y + 1;
		return x + y + z;
	}
	
	int simpleForLoop(int x) {
		int l = 0;
		for (int i = 0; i < x; i++) {
			l += i;
		}
	
		return l;
	}
	
	int nestedForLoop(int x, int y) {
		int l = 0;
		
		for (int i = 0; i < x; i++) {
			for (int j = 0; j < y; j++) {
				l += i + j;
			}
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
	
	int switchWithReverseFallThroughs(int x) throws Exception {
		switch(x) {
		case 3:
			x++;
		case 2:
			x++;
		case 1:
			x++;
		case 0:
			x++;
		}
		
		return x;
	}
	
	int switchWithReverseFallThroughsWithHoles(int x) throws Exception {
		switch(x) {
		case 5:
			x++;
		case 3:
			x++;
		case 1:
			x++;
		case 0:
			x++;
		}
		
		return x;
	}

	int tryCatch(int x) {
		try {
			return x / 0;
		}
		catch (ArithmeticException e) {
			x += 1;
		}
		catch (Throwable y) {
			System.err.println(x);
		}
		
		return x;
	}
	
	int tryMultiCatch(int x) {
		try {
			x =  x / 0;
		}
		catch (ArithmeticException | IllegalArgumentException e) {
			x += 1;
		}
		
		return x;
	}
	
	int tryMultiCatchReturnFromBlock(int x) {
		try {
			return  x / 0;
		}
		catch (ArithmeticException | IllegalArgumentException e) {
			x += 1;
		}
		
		return x;
	}

	int tryCatchFinally(int x) {
		try {
			return x / 0;
		}
		catch (Throwable e) {
			return 0;
		}
		finally {
			System.err.println("watch out!");
		}
	}
	
	

}
