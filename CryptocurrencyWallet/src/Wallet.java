import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class Wallet {

    Map<String, Pair> currencyInfo;


    public Wallet(){
        this.currencyInfo = new HashMap<>();
    }

    public Wallet(Map<String, Pair> currencyInfo) {
        this.currencyInfo = currencyInfo;
    }

    public void depositMoney(double amount) {
        if(currencyInfo.isEmpty()) {
            currencyInfo.put("US Dollars", new Pair(amount, 1));
        } else {
            double previousMoney = currencyInfo.get("US Dollars").getAmount();
            double newAmount = previousMoney + amount;
            currencyInfo.put("US Dollars", new Pair(newAmount, 1));
        }
    }

    public double getDollarsInWallet() {
        if(currencyInfo.isEmpty()) {
            return 0;
        }
        return currencyInfo.get("US Dollars").getAmount();
    }

    public void buyCurrency(String currencyName, double amount, Double price) {
        if(currencyInfo.get(currencyName) == null) {
            currencyInfo.put("US Dollars", new Pair(currencyInfo.get("US Dollars").getAmount() - amount * price, 1));
            currencyInfo.put(currencyName,new Pair(amount, price));
        }else {
            double previousAmount = currencyInfo.get(currencyName).getAmount();
            double newAmount = previousAmount + amount;
            currencyInfo.put("US Dollars", new Pair(currencyInfo.get("US Dollars").getAmount() - amount * price, 1));
            currencyInfo.put(currencyName,new Pair(newAmount, price));
        }
    }

    public boolean sellCurrency(String currencyName, double price) {
        if(!currencyInfo.containsKey(currencyName) || currencyInfo.get(currencyName) == null) {
            return false;
        }
        double dollarsToAdd = currencyInfo.get(currencyName).getAmount() * price;
        currencyInfo.remove(currencyName);
        depositMoney(dollarsToAdd);
        return true;
    }

    public Map<String, Pair> getCurrencyInfo() {
        return this.currencyInfo;
    }

    public String printWallet() {
        String toReturn = "Currency    AmountOwned    Price" + System.lineSeparator();
        for(Map.Entry<String, Pair> entry : currencyInfo.entrySet()) {
            toReturn += entry.getKey() + " " + entry.getValue().getAmount() + " " + entry.getValue().getPrice()
                        + System.lineSeparator();
        }
        toReturn += "This is all the information about the wallet!";
        return toReturn;
    }

}