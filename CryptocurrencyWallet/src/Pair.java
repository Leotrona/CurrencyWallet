import java.time.LocalDateTime;

public class Pair {
    double amount;
    double price;

    public Pair(double firstValue, double secondValue){
        this.amount = firstValue;
        this.price = secondValue;
    }

    public double getAmount() {
        return this.amount;
    }
    public double getPrice() {
        return this.price;
    }
}