package lang.flybytes.tests.examples.business;

public class Bank {
    public Account[] counts;
    public int index = 0;

    public Bank() {
        counts=new Account[50];
    }
    
    public void register(Account c) {
        counts[index] = c;
        index = index + 1;
    }
    
    public Account find(String n) {
        int i = 0;
        boolean find = false;
        
        while ((! find) && (i < index)) {
            if (counts[i].getNumber().equals(n)) {
                find = true;
            }
            else {
                i = i + 1;
            }
        }
        
        if (find == true) {
            return counts[i];
        }
        else return null;
    }
    
    public double balance(String num){
        Account c;
        c = this.find(num);
        if (c != null) {
        	return c.getBalance();
        } else {
            return 0;
        }
    }
    
    public void debit(String num, double val) {
        Account c;
        c = this.find(num);
        
        if (c != null) {
            c.debit(val);
        } else {
            System.out.println("ERROR");
        }
    }
    
    public void credit(String num, double val) {
        Account c;
        c = this.find(num);
        if (c != null) {
            c.credit(val);
        } else {
            System.out.println("ERROR");
        }
    }
}
