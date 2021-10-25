//import com.google.gson.Gson;
//
//import java.io.IOException;
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.util.*;
//import java.util.stream.Collectors;
//
//public class Main {
//    private static final Gson GSON = new Gson();
//
//    public static <Request> void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
//        HttpResponse<String> response;
//        HttpClient currencyClient = HttpClient.newBuilder().build();
//        List<Currency> currencies = new ArrayList<>();
//        Currency[] currenciesArray;
//
//        String API_KEY = "D32B7E5C-80A7-4B88-A554-1441978800D6";
//
//        //https://rest.coinapi.io/v1/assets/BTC?apikey=D32B7E5C-80A7-4B88-A554-1441978800D6
//
//        URI uri = new URI("https://rest.coinapi.io/v1/assets/?apikey=D32B7E5C-80A7-4B88-A554-1441978800D6");
//        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
//        response = currencyClient.send(request, HttpResponse.BodyHandlers.ofString());
//        System.out.println(response);
//
//        currenciesArray = GSON.fromJson(response.body(), Currency[].class);
//
//        for(Currency currency : currenciesArray) {
//            if(currency.isCrypto() > 0 && currency.getPrice_usd() > 0) {
//                currencies.add(currency);
//            }
//        }
//
//        String userOutput = "";
//        for(Currency currency : currencies) {
//            userOutput += currency.getAsset_id() + " " + currency.getPrice_usd() +
//                    System.lineSeparator();
//        }
//        System.out.println(userOutput);
//
//
//    }
//}
