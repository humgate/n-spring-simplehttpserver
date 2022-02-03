import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    static final int THREAD_POOL_THREADS = 64;
    //handlers collection
    static Map<Method,Map<String, Handler>> handlerMap = new HashMap<>();

    /**
     * Handler adder
     * @param method - http method
     * @param path - http request path. May be absolute path including resource name, or may be path to folder
     *             resources folder
     * @param handler - handler object
     */
    public void addHandler(Method method, String path, Handler handler) {
        Map<String, Handler> innerMap = new HashMap<>();
        innerMap.put(path, handler);
        handlerMap.put(method, innerMap);
    }

    /**
     * Parses Request string to Request object. Read only request line for simplicity.
     * String must be in form GET /path HTTP/1.1
     * @param strRequest - input string
     * @return - Request objects if parsed ok and null if not
     */
    public static Request parseRequest (String strRequest) {
        final var parts = strRequest.split(" ");

        if (parts.length != 3) {
            // incorrect request
            return null;
        }

        Method method;
         try {
             method = Enum.valueOf(Method.class,parts[0]);
         } catch (IllegalArgumentException e) {
             // incorrect request;
             return null;
         }

         String path = parts[1];
         if (path ==null) {
             // incorrect request;
             return null;
         }

        return new Request(method,path,null,new byte[0]);
    }

    /**
     * Initiate server listening operation for given port.
     * Listening is cycle of waiting for new client connections and launching received request handling
     * in new thread (in threadPool) upon connection
     * @param port - server port
     */
    public void listen(short port) {
        final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_THREADS);
        try (final var serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                   /* Remove Socket closure from here to the threadPool thread, otherwise threadPool thread
                    * will get already closed socket */
                    final var socket = serverSocket.accept();
                    threadPool.execute(() -> processClientRequests(socket));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    /**
     * Processes client request and closes the socket
     * @param socket - accepted client socket
     */
    private static void processClientRequests(Socket socket) {
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();

            //parse String to Request
            Request request = parseRequest(requestLine);
            if (request == null) {
                // just close socket
                socket.close();
                return;
            }

            //find registered handler exactly matching handlers path
            Handler handler = handlerMap.get(request.getMethod()).get(request.getPath());
            System.out.println("request path: " + request.getPath());

            //if exact match not found, try to find handler by parent path to request path
            if (handler == null) {
                System.out.println("handler not found for exact request path");
                Path parent = Path.of(request.getPath()).getParent();
                System.out.println("parent path: " + parent);

                //if this one fails either - no registered handlers found, respond with 404
                handler = handlerMap.get(request.getMethod()).get(parent.toString());
                if (handler == null) {
                    System.out.println("handler not found for request parent path");
                    out.write(buildResponseStatusHeadersOnFail().getBytes());
                    out.flush();
                    socket.close();
                    return;
                }
            }

            //handler found, have it to process the request
            handler.handle(request,out);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Builds response status line and headers for OK status
     * @param mimeType - mimetype of response body
     * @param length - respince body length
     * @return - response status line and headers
     */
    public static String buildResponseStatusHeadersOnOK(String mimeType, long length) {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }

    /**
     * Builds response status line and headers for "Not found" status
     * @return - response status line and headers
     */
    public static String buildResponseStatusHeadersOnFail() {
        return  "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }
}

