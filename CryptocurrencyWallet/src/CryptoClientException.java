public class CryptoClientException extends Exception{
    public CryptoClientException(String message) {
        super(message);
    }

    public CryptoClientException(String message, Exception e) {
        super(message, e);
    }
}
