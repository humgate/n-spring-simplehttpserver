import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
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
     * DEPRECATED
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
        try (final var in = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            // read request string into Request object
            Request request = readRequest(in);
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
     * reads http request from input stream validates it and places into thr Request object
     * @param in - BufferedInputStream of client socket
     * @return Request object if parsing was ok or null if not (bad request)
     */
    private static Request readRequest (BufferedInputStream in) {
        try {
            in.mark(REQUEST_LENGTH_LIMIT);
            final var buffer = new byte[REQUEST_LENGTH_LIMIT];
            final var read = in.read(buffer);

            // look for request line end
            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                return null;
            }

            // read request line
            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                return null;
            }

            final Method method;
            try {
                method = Enum.valueOf(Method.class,requestLine[0]);
            } catch (IllegalArgumentException e) {
                // bad request;
                return null;
            }
            System.out.println(method);

            final var path = requestLine[1];
            if (!path.startsWith("/")) {
                // bad request;
                return null;
            }
            System.out.println(path);

            // ищем заголовки
            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                // bad request;
                return null;
            }

            // отматываем на начало буфера
            in.reset();
            // пропускаем requestLine
            in.skip(headersStart);

            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
            System.out.println(headers);

            // определяем тело, для GET тела нет
            byte[] bodyBytes = new byte[0];
            if (!method.equals(Method.GET)) {
                in.skip(headersDelimiter.length);
                // вычитываем Content-Length, чтобы прочитать body
                final var contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()) {
                    final var length = Integer.parseInt(contentLength.get());
                    bodyBytes = in.readNBytes(length);
                    final var body = new String(bodyBytes);
                    System.out.println(body);
                }
            }

            return new Request(method, path, headers, bodyBytes);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
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

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}

