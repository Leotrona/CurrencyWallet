import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.*;

public class RequestHandler {

    private static final File currencyCache = new File ("currencyCache.txt");
    private static final File users = new File ("users.txt");
    private static final File wallets = new File ("wallets.txt");

    private static Map<SocketAddress, Boolean> threadsLoggedIn = new HashMap<>();
    private static Map<String, Wallet> usernameWallet = new HashMap<>();
    private static Map<SocketAddress, String> currentUserOnThread = new HashMap<>();
    private static List<String> loggedInUsers = new ArrayList<>();
    private static final Gson GSON = new Gson();


    private static final CryptoApiClient cryptoApiClient =
            new CryptoApiClient(HttpClient.newBuilder().build());


    public RequestHandler() throws IOException {

        threadsLoggedIn = new HashMap<>();
        usernameWallet = new HashMap<>();
        currentUserOnThread = new HashMap<>();
        users.createNewFile();
        wallets.createNewFile();
        currencyCache.createNewFile();
        if(wallets.length() > 0) {
            loadWallets();
        }
    }

    public static String handleRequest(String message, SocketChannel socketChannel) throws CryptoClientException {
        if(message == null) {
            throw new IllegalArgumentException();
        }
        try {
            if(message.equals("disconnect")) {
                    socketChannel.close();
                    return "Successfully disconnected.";

            }

            String command = message.split(" ")[0];
            String arguments = message.substring(message.indexOf(" ") + 1);

            switch(command) {
                case "register":
                    return register(arguments, socketChannel);
                case "login":
                    return login(arguments, socketChannel);
                case "deposit-money":
                    return depositMoney(arguments, socketChannel);
                case "list-offerings":
                    return listOfferings(socketChannel);
                case "buy":
                    return buy(arguments, socketChannel);
                case "sell":
                    return sell(arguments, socketChannel);
                case "get-wallet-summary":
                    return getWalletSummary(socketChannel);
                case "get-wallet-overall-summary":
                    return getWalletOverallSummary(socketChannel);
                case "logout":
                    return logout(socketChannel);
                default :
                    return "Unknown command!";
            }
        } catch (IOException e) {
            throw new CryptoClientException("Problem with the currency client." , e);
        }
    }

    private static String register(String arguments, SocketChannel socketChannel) throws IOException {
        if(!threadsLoggedIn.isEmpty() && threadsLoggedIn.get(socketChannel.getRemoteAddress()) == null) {
            threadsLoggedIn.put(socketChannel.getRemoteAddress(), false);
        }else if(!threadsLoggedIn.isEmpty() && threadsLoggedIn.get(socketChannel.getRemoteAddress()) == true) {
            return "There is already other user logged in you need to logout in order to register new account!";
        }

        String username = arguments.split(" ")[0];
        String password = arguments.split(" ")[1];

        if(checkFileForUsername(username)) {
            return "Username already taken!";
        }

        writeUserInfoToFile(username,password);
        threadsLoggedIn.put(socketChannel.getRemoteAddress(), true);
        currentUserOnThread.put(socketChannel.getRemoteAddress(), username);
        usernameWallet.put(username, new Wallet());
        writeWalletsToFile();
        loggedInUsers.add(username);
        return "Successfully registered " + username + "!";
    }

    private static String login(String arguments, SocketChannel socketChannel) throws IOException {
        if(!threadsLoggedIn.isEmpty() && threadsLoggedIn.get(socketChannel.getRemoteAddress()) == null) {
            threadsLoggedIn.put(socketChannel.getRemoteAddress(), false);
        }else if(!threadsLoggedIn.isEmpty() && threadsLoggedIn.get(socketChannel.getRemoteAddress()) == true) {
            return "There is already other user logged in you need to logout in order to register new account!";
        }

        String username = arguments.split(" ")[0];
        System.out.println(username);
        String password = arguments.split(" ")[1];

        if (!checkFileForUsername(username)) {
            return "No such username!";
        } else if (loggedInUsers.contains(username)) {
            return "You are already logged in !";
        }

        try (BufferedReader bufferedReader = Files.newBufferedReader(users.toPath())) {
            String line;

            while((line = bufferedReader.readLine()) != null) {
                if(line.contains(username) && !line.contains(encryptPassword(password))) {
                    return "Incorrect password!";
                }
            }
            threadsLoggedIn.put(socketChannel.getRemoteAddress(), true);
            currentUserOnThread.put(socketChannel.getRemoteAddress(), username);
            loggedInUsers.add(username);
            return "Successful login " + username + "!";

        } catch (IOException e) {
            throw new IllegalStateException("Error while reading from file!", e);
        }

    }

    private static String depositMoney(String amountAsString, SocketChannel socketChannel) throws IOException {
        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }

        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        double amount = Double.valueOf(amountAsString);
        String username = currentUserOnThread.get(socketChannel.getRemoteAddress());

        Wallet newWallet;
        if(usernameWallet.get(username) !=  null) {
            newWallet = usernameWallet.get(username);
        }else {
            newWallet = new Wallet();
        }
        newWallet.depositMoney(amount);
        usernameWallet.put(username, newWallet);
        writeWalletsToFile();
        return "Successfully deposited money!";
    }

    private static String listOfferings(SocketChannel socketChannel) throws IOException, CryptoClientException {

        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }
        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        List<Currency> currencies = cryptoApiClient.getAllCurrencies();

        String currencyInfo = "Here is the current crypto currencies prices: " + System.lineSeparator();
        for (Currency currency : currencies) {
            currencyInfo += "CurrencyId: " + currency.getAsset_id() + "Price in usd: " + currency.getPrice_usd() +
                    System.lineSeparator();
        }

        return currencyInfo;
    }

    private static String buy(String arguments, SocketChannel socketChannel) throws IOException, CryptoClientException {
        if(arguments == null) {
            throw new IllegalArgumentException();
        }

        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }
        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        String offeringCode = arguments.split(" ")[0].substring(11);
        double amount = Double.parseDouble(arguments.split(" ")[1].substring(8));
        String usernameLoggedInThread = currentUserOnThread.get(socketChannel.getRemoteAddress());

        if(amount > usernameWallet.get(usernameLoggedInThread).getDollarsInWallet()) {
            return "You don't have enough money in your wallet";
        }

        Currency wantedCurrency = cryptoApiClient.getCurrencyByCode(offeringCode);
        if(wantedCurrency == null) {
            return "There is no such currency";
        }
        String wantedCurrencyName = wantedCurrency.getName();
        double affordableAmount =amount / wantedCurrency.getPrice_usd();

        usernameWallet.get(usernameLoggedInThread).buyCurrency(wantedCurrencyName, affordableAmount, wantedCurrency.getPrice_usd());
        writeWalletsToFile();

        return "Successfully bought " + wantedCurrencyName;
    }

    private static String sell(String arguments, SocketChannel socketChannel) throws IOException, CryptoClientException {
        if(arguments == null) {
            throw new IllegalArgumentException();
        }

        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }
        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        String offeringCode = arguments.split(" ")[0].substring(11);
        Currency wantedCurrency = cryptoApiClient.getCurrencyByCode(offeringCode);
        String currencyName = wantedCurrency.getName();
        double currencyPrice = wantedCurrency.getPrice_usd();
        String usernameLoggedInThread = currentUserOnThread.get(socketChannel.getRemoteAddress());

        if(usernameWallet.get(usernameLoggedInThread).sellCurrency(currencyName, currencyPrice) == false) {
            return "You don't own this currency";
        } else {
            writeWalletsToFile();
            return "Successfully sold " + wantedCurrency.getName();
        }
    }

    private static String getWalletSummary(SocketChannel socketChannel) throws IOException {
        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }
        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        String username = currentUserOnThread.get(socketChannel.getRemoteAddress());
        return usernameWallet.get(username).printWallet();
    }

    private static String getWalletOverallSummary(SocketChannel socketChannel) throws IOException, CryptoClientException {
        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }
        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }
        String username = currentUserOnThread.get(socketChannel.getRemoteAddress());
        Wallet usersWallet = usernameWallet.get(username);
        Map<String, Pair> currenciesInformation = usersWallet.getCurrencyInfo();

        String walletOverallSummary = "Currency   +/-" + System.lineSeparator();
        for(Map.Entry<String, Pair> entry : currenciesInformation.entrySet()) {
            String currencyName = entry.getKey();
            System.out.println(currencyName);
            if(!currencyName.equals("US Dollars")) {
                Currency currency = cryptoApiClient.getCurrencyByName(currencyName);
                double ownedCurrencyDollarValue = entry.getValue().getAmount() * entry.getValue().getPrice();
                double currentCurrencyDollarValue = entry.getValue().getAmount() * currency.getPrice_usd();
                walletOverallSummary += currencyName + " " + (currentCurrencyDollarValue - ownedCurrencyDollarValue);
                walletOverallSummary += System.lineSeparator();
            } else {
                walletOverallSummary += currencyName + " " + 0 + System.lineSeparator();
            }
        }
        walletOverallSummary += "This is all of the information for the wallet!";
        return walletOverallSummary;
    }

    private static String logout(SocketChannel socketChannel) throws IOException {
        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }

        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        threadsLoggedIn.put(socketChannel.getRemoteAddress(), false);
        String username = currentUserOnThread.get(socketChannel.getRemoteAddress());
        currentUserOnThread.put(socketChannel.getRemoteAddress(), null);
        loggedInUsers.remove(username);
        return "Successfully logged out!";
    }

    private static boolean checkFileForUsername(String username) {
        try (BufferedReader bufferedReader = Files.newBufferedReader(users.toPath())) {
            String line;

            while((line = bufferedReader.readLine()) != null) {
                if(line.contains(username)) {
                    return true;
                }
            }

        } catch(IOException e) {
            throw new IllegalStateException("Error while reading from file!", e);
        }

        return false;
    }

    private static void writeUserInfoToFile(String username, String password) {
        password = encryptPassword(password);
        String toBeInput = username + " " + password;
        try (var bufferedWriter = new FileWriter(users,true)) {
            bufferedWriter.write(toBeInput);
            bufferedWriter.flush();
            bufferedWriter.write(System.lineSeparator());
            bufferedWriter.flush();
        } catch (IOException e) {
            throw new IllegalStateException("A problem occurred while writing to the file!", e);
        }
    }

    private static void writeWalletsToFile() throws IOException {
        try (var bufferedWriter = new FileWriter(wallets, false)) {
            for(Map.Entry<String, Wallet> entry : usernameWallet.entrySet()) {
                bufferedWriter.write(entry.getKey());
                bufferedWriter.flush();
                bufferedWriter.write(" ");
                bufferedWriter.flush();
                bufferedWriter.write(GSON.toJson(entry.getValue()));
                bufferedWriter.flush();
                bufferedWriter.write(System.lineSeparator());
            }
        } catch (IOException e) {
            throw new IllegalStateException("A problem occurred while saving the wallets!", e);
        }
    }

    private void loadWallets() throws IOException {
        try(BufferedReader reader = Files.newBufferedReader(wallets.toPath())) {
            String line;
            while ((line = reader.readLine()) != null) {
                String username = line.split(" ")[0];
                String wallet = line.substring(line.indexOf(" ") + 1);
                usernameWallet.put(username, GSON.fromJson(wallet, Wallet.class));
            }
        } catch (IOException e) {
            throw new IOException("Couldn't load wallets!");
        }
    }

    private static String encryptPassword(String password) {
        char[] passAsChar = password.toCharArray();
        for(int i = 0; i < passAsChar.length; i++) {
            passAsChar[i] += 5;
        }
        String newPassword = String.valueOf(passAsChar);
        StringBuilder sb = new StringBuilder(newPassword);
        sb.reverse();
        newPassword = sb.toString();
        return newPassword;
    }

}
