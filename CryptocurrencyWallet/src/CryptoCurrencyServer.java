import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;


public class CryptoCurrencyServer {

    private static final int SERVER_PORT = 7777;
    private static final int BUFFER_SIZE = 4096;
    private static final String SERVER_HOST = "localhost";
    private static final String API_KEY = "D32B7E5C-80A7-4B88-A554-1441978800D6";

    private final int port;
    private final ByteBuffer messageBuffer;
    private static final HttpClient currencyClient = HttpClient.newBuilder().build();
    private static final File currencyCache = new File ("currencyCache.txt");
    private static final File users = new File ("users.txt");
    private static final File wallets = new File ("wallets.txt");

    private static final Gson GSON = new Gson();

    private static LocalDateTime lastSearch;
    private boolean isStarted = true;
    private Map<SocketAddress, Boolean> threadsLoggedIn;
    private Map<String, Wallet> usernameWallet;
    private Map<SocketAddress, String> currentUserOnThread;


    public CryptoCurrencyServer (int port) throws IOException {
        this.port = port;
        this.messageBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        threadsLoggedIn = new HashMap<>();
        usernameWallet = new HashMap<>();
        currencyCache.createNewFile();
        currentUserOnThread = new HashMap<>();
        users.createNewFile();
        wallets.createNewFile();
        if(wallets.length() > 0) {
            loadWallets();
        }
    }


    public static void main(String[] args) throws IOException {
        new CryptoCurrencyServer(SERVER_PORT).start();
    }

    public void start() throws IOException {

        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress(SERVER_HOST, port));
            serverSocketChannel.configureBlocking(false);

            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while(isStarted) {
                int readyChannels = selector.select();
                if(readyChannels == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while(keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if(key.isReadable()) {
                        SocketChannel socketChannel = (SocketChannel)key.channel();

                        messageBuffer.clear();
                        int r = socketChannel.read(messageBuffer);
                        if(r <= 0) {
                            System.out.println("Nothing to read from the channel.");
                            socketChannel.close();
                            continue;
                        }
                        handleKeyIsReadable(key, messageBuffer);
                    } else if(key.isAcceptable()) {
                        handleKeyIsAcceptable(selector,key);
                    }

                    keyIterator.remove();
                }
            }

        } catch(IOException | CryptoClientException e) {
            System.err.println("There is an error with the server socket: " + e.getMessage());
            System.err.println(e);
        }

        System.out.println("Server stopped.");

    }

    public void stop() {
        isStarted = false;
    }

    private void handleKeyIsReadable(SelectionKey key, ByteBuffer buffer) throws IOException, CryptoClientException {
        SocketChannel socketChannel = (SocketChannel)key.channel();

        buffer.flip();
        String message = new String(buffer.array(),0,buffer.limit()).trim();

        System.out.println("Message [ " + message + " ] received from client " + socketChannel.getRemoteAddress());

        String command = message.split(" ")[0];
        String arguments = message.substring(message.indexOf(" ") + 1);

        String response = null;

        switch(command) {
            case "register":
                response = register(arguments, socketChannel);
                break;
            case "login":
                response = login(arguments, socketChannel);
                break;
            case "deposit-money":
                response = depositMoney(arguments, socketChannel);
                break;
            case "list-offerings":
                response = listOfferings(socketChannel);
                break;
            case "buy":
                response = buy(arguments, socketChannel);
                break;
            case "sell":
                response = sell(arguments, socketChannel);
                break;
            case "get-wallet-summary":
                response = getWalletSummary(socketChannel);
                break;
            case "get-wallet-overall-summary":
                response = getWalletOverallSummary(socketChannel);
                break;
            case "logout":
                response = logout(socketChannel);
                break;
            case "disconnect" :
                disconnect(key);
                break;
            default :
                response = "Unknown command!";
        }

        if (response != null) {
            System.out.println("Sending response to client: " + response);
            response += System.lineSeparator();
            buffer.clear();
            buffer.put(response.getBytes());
            buffer.flip();
            socketChannel.write(buffer);
        }
    }

    private void handleKeyIsAcceptable(Selector selector, SelectionKey key) throws IOException {
        ServerSocketChannel socketChannel = (ServerSocketChannel)key.channel();
        SocketChannel accept = socketChannel.accept();
        accept.configureBlocking(false);
        accept.register(selector, SelectionKey.OP_READ);
        System.out.println("Connection from client received: " + accept.getRemoteAddress());
    }

    private String register(String arguments, SocketChannel socketChannel) throws IOException {
        if(!threadsLoggedIn.isEmpty() && threadsLoggedIn.get(socketChannel.getRemoteAddress()) == true) {
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
        return "Successfully registered " + username + "!";
    }

    private String login(String arguments, SocketChannel socketChannel) throws IOException {
        if(!threadsLoggedIn.isEmpty() && threadsLoggedIn.get(socketChannel.getRemoteAddress()) == true) {
            return "There is already other user logged in you need to logout first!";
        }

        String username = arguments.split(" ")[0];
        String password = arguments.split(" ")[1];

        if (!checkFileForUsername(username)) {
            return "No such username!";
        }

        try (BufferedReader bufferedReader = Files.newBufferedReader(users.toPath())) {
            String line;

            while((line = bufferedReader.readLine()) != null) {
                if(line.contains(username) && !line.contains(password)) {
                    return "Incorrect password!";
                }
            }
            threadsLoggedIn.put(socketChannel.getRemoteAddress(), true);
            currentUserOnThread.put(socketChannel.getRemoteAddress(), username);
            return "Successful login " + username + "!";

        } catch (IOException e) {
            throw new IllegalStateException("Error while reading from file!", e);
        }

    }

    private String depositMoney(String amountAsString, SocketChannel socketChannel) throws IOException {
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

    private String listOfferings(SocketChannel socketChannel) throws IOException, CryptoClientException {
        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }
        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        List<Currency> currencies;
        if(lastSearch.plusMinutes(30).isAfter(LocalDateTime.now())) {
            currencies = loadCurrenciesFromFile();
        } else {
            currencies = getAllCurrencies();
        }

        String currencyInfo = "Here is the current crypto currencies prices: " + System.lineSeparator();
        for (Currency currency : currencies) {
            currencyInfo += "CurrencyId: " + currency.getAsset_id() + "Price in usd: " + currency.getPrice_usd() +
                    System.lineSeparator();
        }

        return currencyInfo;
    }

    private String buy(String arguments, SocketChannel socketChannel) throws IOException, CryptoClientException {
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

        Currency wantedCurrency = getCurrencyByCode(offeringCode);
        if(wantedCurrency == null) {
            return "There is no such currency";
        }
        String wantedCurrencyName = wantedCurrency.getName();
        double affordableAmount =amount / wantedCurrency.getPrice_usd();

        usernameWallet.get(usernameLoggedInThread).buyCurrency(wantedCurrencyName, affordableAmount, wantedCurrency.getPrice_usd());
        writeWalletsToFile();

        return "Successfully bought " + wantedCurrencyName;
    }

    private String sell(String arguments, SocketChannel socketChannel) throws IOException, CryptoClientException {
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
        Currency wantedCurrency = getCurrencyByCode(offeringCode);
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

    private String getWalletSummary(SocketChannel socketChannel) throws IOException {
        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }
        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        String username = currentUserOnThread.get(socketChannel.getRemoteAddress());
        return usernameWallet.get(username).printWallet();
    }

    private String getWalletOverallSummary(SocketChannel socketChannel) throws IOException, CryptoClientException {
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
                Currency currency = getCurrencyByName(currencyName);
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


    private String logout(SocketChannel socketChannel) throws IOException {
        if(threadsLoggedIn.isEmpty()) {
            return "There is nobody logged in yet!";
        }

        if(!threadsLoggedIn.get(socketChannel.getRemoteAddress())) {
            return "You are not logged in!";
        }

        threadsLoggedIn.put(socketChannel.getRemoteAddress(), false);
        currentUserOnThread.put(socketChannel.getRemoteAddress(), null);
        return "Successfully logged out!";
    }

    private void disconnect(SelectionKey key) throws IOException {
        key.channel().close();
        key.cancel();
    }

    private boolean checkFileForUsername(String username) {
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

    private void writeUserInfoToFile(String username, String password) {
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

    private void writeWalletsToFile() throws IOException {
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


    private List<Currency> getAllCurrencies() throws CryptoClientException, IOException {
        HttpResponse<String> response;
        List<Currency> currencies = new ArrayList<>();

        //If we have searched in the span of the last 30 minutes we get the last info about the currencies we have
        if(lastSearch != null && lastSearch.plusMinutes(30).isAfter(LocalDateTime.now())) {
            currencies = loadCurrenciesFromFile();
            return currencies;
        }

        //If not we make a request, we change the time of the last search to now and we write the currencies values
        try {
            URI uri = new URI("https://rest.coinapi.io/v1/assets/?apikey=D32B7E5C-80A7-4B88-A554-1441978800D6");
            HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
            response = currencyClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new CryptoClientException("Could not retrieve currencies!", e);
        }

        if(response.statusCode() == HttpURLConnection.HTTP_OK) {
            Currency[] currenciesArray;
            currenciesArray = GSON.fromJson(response.body(), Currency[].class);

            for(Currency currency : currenciesArray) {
                if(currency.isCrypto() > 0) {
                    currencies.add(currency);
                }
            }
            writeCurrenciesToFile(currencies);
            lastSearch = LocalDateTime.now();


            return currencies;
        } else if(response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            throw new CryptoClientException("Cryptos not found");
        }

        throw new CryptoClientException("Unexpected response code from crypto service");
    }

    private Currency getCurrencyByCode(String code) throws IOException, CryptoClientException {
        List<Currency> allCurrencies;
        if(lastSearch != null && lastSearch.plusMinutes(30).isAfter(LocalDateTime.now())) {
            allCurrencies = loadCurrenciesFromFile();
        } else {
            allCurrencies = getAllCurrencies();
        }

        for(Currency currency : allCurrencies) {
            if(currency.getAsset_id().equals(code)) {
                return currency;
            }
        }
        return null;
    }

    private Currency getCurrencyByName(String name) throws IOException, CryptoClientException {
        List<Currency> allCurrencies;
        if(lastSearch != null && lastSearch.plusMinutes(30).isAfter(LocalDateTime.now())) {
            allCurrencies = loadCurrenciesFromFile();
        } else {
            allCurrencies = getAllCurrencies();
        }

        for(Currency currency : allCurrencies) {
            if(currency.getName().equals(name)) {
                return currency;
            }
        }
        return null;
    }

    //We just load the currencies from the file
    private List<Currency> loadCurrenciesFromFile() throws IOException {
        List<Currency> allCurrencies = new ArrayList<>();
        try(BufferedReader reader = Files.newBufferedReader(currencyCache.toPath())) {
            String line;
            while((line = reader.readLine()) != null) {
                allCurrencies.add(GSON.fromJson(line, Currency.class));
            }
        } catch (IOException e) {
            throw new IOException("Couldn't load currencies!");
        }

        return allCurrencies;
    }

    //We just write the currencies to the cache
    private void writeCurrenciesToFile(List<Currency> currenciesToWrite){
        try (var bufferedWriter = new FileWriter(currencyCache,false)) {
            for(Currency currency : currenciesToWrite) {

                bufferedWriter.write(GSON.toJson(currency));
                bufferedWriter.flush();
                bufferedWriter.write(System.lineSeparator());
                bufferedWriter.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException("A problem occurred while writing to the file!", e);
        }
    }


}
