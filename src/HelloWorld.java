public class HelloWorld {
	public static void main(String[] args)
	{
		Runnable r = () -> System.out.println("Hello");
		r.run();
	}

}
