import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    //http request max length accepted by the server
    static final short REQUEST_LENGTH_LIMIT=4096;
    //threadPool threads amount to process client requests
    static final int THREAD_POOL_THREADS = 64;
    //handlers collection
    public static Map<Method,Map<String, Handler>> handlerMap = new HashMap<>();

    /**
     * Handler adder
     * @param method - http method
     * @param path - http request path. May be absolute path including resource name, or may be path to folder
     *             resources folder
     * @param handler - handler object
     */
    public void addHandler(Method method, String path, Handler handler) {
        //outer (method) map exists so inner map exits as well
        if (handlerMap.containsKey(method)) {
            //add inner (path) map element
            handlerMap.get(method).put(path, handler);
        } else {
            //create new inner map
            Map<String, Handler> innerMap = new HashMap<>();
            //add inner (path) map element
            innerMap.put(path, handler);
            //add outer (method) map element
            handlerMap.put(method, innerMap);
        }
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
                    threadPool.execute(() -> {
                        while (!socket.isClosed()) {
                            processClientRequest(socket);
                        }
                    });
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
    private static void processClientRequest(Socket socket) {
        try (final var in = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            // read request string into Request object
            Request request = RequestReader.readRequest(in, REQUEST_LENGTH_LIMIT);
            if (request == null) {
                // just close socket
                System.out.println("closing socket");
                socket.close();
                return;
            }

            //find registered handler exactly matching handlers path
            Handler handler = handlerMap
                    .get(request.getMethod())
                    .get(request.getPathWithoutQueryString());
            System.out.println("request full path: " + request.getPath());
            System.out.println("request path: " + request.getPathWithoutQueryString());

            //if exact match not found, and path is not root /, try to find handler by parent path to request path
            if (handler == null) {
                System.out.println("handler not found for exact request path");
                Path parent = Path.of(request.getPathWithoutQueryString()).getParent();
                System.out.println("parent path: " + parent);

                //if this one fails either - no registered handlers found, respond with 404
                handler = handlerMap.get(request.getMethod()).get(parent.toString());
                if (handler == null) {
                    System.out.println("handler not found for request parent path");
                    out.write(buildResponseStatusHeadersOnFail(true).getBytes());
                    out.flush();
                    socket.close();
                    return;
                }
            }

            //handler found, have it to process the request
            System.out.println("handler found, handling...\n");
            handler.handle(request,out);

            //do not close the connection if client requested so with "Connection: keep-alive" header
            if  (!request.headerExists("Connection: keep-alive")) {
                socket.close();
            }
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
    public static String buildResponseStatusHeadersOnOK(String mimeType, long length, boolean connClose) {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                (connClose ? "Connection: close\r\n\r\n":"\r\n");
    }

    /**
     * Builds response status line and headers for "Not found" status
     * @return - response status line and headers
     */
    public static String buildResponseStatusHeadersOnFail(boolean connClose) {
        return  "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                (connClose ? "Connection: close\r\n\r\n":"\r\n");
    }

    /**
     * Builds response status line and headers for "Bad Request" status
     * @return - response status line and headers
     */
    public static String buildResponseStatusHeadersOnBad() {
        return  "HTTP/1.1 40O Bad Request\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }
}

