import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    static final String PUBLIC_FOLDER = "public";
    static final int THREAD_POOL_THREADS = 64;
    static final List<String> validPaths = initServerPaths();
    static Map<Method,Map<String, Handler>> handlerMap = new HashMap<>();

    public void addHandler(Method method, String path, Handler handler) {
        Map<String, Handler> innerMap = new HashMap<>();
        innerMap.put(path, handler);
        handlerMap.put(method, innerMap);
    }

    public static Request parseRequest (String strRequest) {
        // read only request line for simplicity
        // must be in form GET /path HTTP/1.1
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

    public void listen(short port) {
        final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_THREADS);
        try (final var serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    /* Закрытие сокета выносим отсюда в тред-обработчик из тредпула. Иначе запущенный тред
                    в тредпуле даже не успеет начать обработку, а socket уже будет закрыт в этом треде
                    * Remove Socket closure from here to the threadPool thread, otherwise threadPool thread
                    * will get closed socket
                    */
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
     * Reads server resource paths from file system folder (constant)
     *
     * @return pathsList
     */
    private static List<String> initServerPaths() {
        List<String> validPaths = Arrays.asList((Objects.requireNonNull(new File(Server.PUBLIC_FOLDER).list())));
        validPaths.replaceAll(s -> "/" + s);
        return validPaths;
    }

    /**
     * Processes client request and closes the socket
     *
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

            Handler handler = handlerMap.get(request.getMethod()).get(request.getPath());

            if (handler == null) {
                //no handlers registered
                out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                socket.close();
                return;
            }

            handler.handle(request,out);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

