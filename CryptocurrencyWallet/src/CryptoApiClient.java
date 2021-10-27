import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CryptoApiClient {

    private HttpClient currencyClient;
    private LocalDateTime lastSearch;
    private static final File currencyCache = new File ("currencyCache.txt");
    private static final Gson GSON = new Gson();
    private static final String API_KEY = "D32B7E5C-80A7-4B88-A554-1441978800D6";


    public CryptoApiClient(HttpClient cryptoClient) {
        this.currencyClient = cryptoClient;
    }

    public List<Currency> getAllCurrencies() throws IOException, CryptoClientException {
        currencyCache.createNewFile();
        HttpResponse<String> response = null;

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
                if(currency.isCrypto() > 0 && currencies.size() < 50) {
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

    public Currency getCurrencyByName(String name) throws IOException, CryptoClientException {
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

    public Currency getCurrencyByCode(String code) throws IOException, CryptoClientException {
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

    private List<Currency> loadCurrenciesFromFile() throws IOException {
        currencyCache.createNewFile();
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

    private void writeCurrenciesToFile(List<Currency> currenciesToWrite) throws IOException {
        currencyCache.createNewFile();
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
