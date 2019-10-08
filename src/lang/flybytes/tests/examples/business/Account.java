package lang.flybytes.tests.examples.business;

public class Account {
    protected String number;
    protected double balance;
    protected String name;

    void credit (double value) {
        balance = balance + value;
    }

    void debit (double value){
        balance = balance - value;
    }

    public String getNumber() {
        return number;
    }

    public double getBalance() {
        return balance;
    }

    public String getName() {
        return  name;
    }

    public Account (String n, String na) {
        number = n;
        name = na; 
        balance = 0;
    }
}
