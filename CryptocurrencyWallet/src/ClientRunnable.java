import java.io.BufferedReader;
import java.io.IOException;

public class ClientRunnable implements Runnable{
    private final BufferedReader bufferedReader;

    private String username;

    public ClientRunnable(BufferedReader bufferedReader) {
        this.bufferedReader = bufferedReader;
    }

    @Override
    public void run() {
        String line;
        while(true) {
            try{
                if((line = bufferedReader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.err.println("Connection is closed, stop waiting for server messages due to: " +
                        e.getMessage());
                System.err.println(e);
                break;
            }
        }
    }

    public String getUsername() {
        return this.username;
    }
}
