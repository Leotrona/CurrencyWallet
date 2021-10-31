import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;


public class CryptoCurrencyServer {

    private static final int SERVER_PORT = 7777;
    private static final int BUFFER_SIZE = 4096;
    private static final String SERVER_HOST = "localhost";

    private RequestHandler requestHandler;

    private final int port;
    private final ByteBuffer messageBuffer;
    private boolean isStarted = true;


    public CryptoCurrencyServer (int port) throws IOException {
        requestHandler = new RequestHandler();
        this.port = port;
        this.messageBuffer = ByteBuffer.allocate(BUFFER_SIZE);

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

        String response = null;
        response = requestHandler.handleRequest(message, socketChannel);


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


}
