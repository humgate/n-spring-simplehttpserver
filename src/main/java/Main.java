import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Server demo class. Registers several handlers and launches listening.
 * The "exact match" handlers are specific full-path handlers, they trigger only when client requests exactly
 * for path specified in handler path ("/" which means http://localhost:9999).
 *
 * One custom specific exact match handler registered for classic.html
 *
 * The "Common" handler it is only seeked for if no full-path handlers found
 * for request path, and they handle all request paths whose parent folder is specified in handler path.
 * In this example "common" handler will handle all requests for resources located in PUBLIC_FOLDER (and
 * another common handler registered for PUBLIC_FOLDER/messages folder
 *
 */
public class Main {
    static final short SERVER_PORT = 9999;
    static final String PUBLIC_FOLDER = "public";

    public static void main(String[] args) {
        final var server = new Server();

        //Custom exact match handler for root path: http://localhost:9999")
        server.addHandler(Method.GET, "/",
                (r,o) -> defaultHandlerForSpecificPath(r,o,"/index.html"));

        //Custom exact match handler for sub-path of root path: http://localhost:9999/messages"
        server.addHandler(Method.GET, "/messages",
                (r,o) -> defaultHandlerForSpecificPath(r,o,"/messages/message.html"));

        //Common "root" ("parent") handler for each resource located in PUBLIC_FOLDER folder
        server.addHandler(Method.GET, "\\", Main::defaultHandlerForSpecificFolder);

        //Common "root" ("parent") handler for each resource located in PUBLIC_FOLDER/messages folder
        server.addHandler(Method.GET, "\\messages", Main::defaultHandlerForSpecificFolder);

        //Custom exact match handler for PUBLIC_FOLDER/classic.html file"
        server.addHandler(Method.GET, "/classic.html", (r, o) -> {

            try {
                final var filePath = Path.of(".", PUBLIC_FOLDER, r.getPathWithoutQueryString());
                final var mimeType = Files.probeContentType(filePath);
                final var template = Files.readString(filePath);
                System.out.println(template);
                final var content = template.replace("{time}",LocalDateTime.now().
                        toString()).getBytes();
                o.write(Server.buildResponseStatusHeadersOnOK(
                        mimeType, content.length, !r.headerExists("Connection: keep-alive")).getBytes());
                o.write(content);
                o.flush();
            } catch (InvalidPathException | IOException e) {
                e.printStackTrace();
                //Not found. Respond with 404
                try {
                    o.write(Server.buildResponseStatusHeadersOnFail(
                            !r.headerExists("Connection: keep-alive")).getBytes());
                    o.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        server.listen(SERVER_PORT);
    }

    private static void defaultHandlerForSpecificPath(Request r, BufferedOutputStream o, String pageName) {
        try {
            final Path filePath = Path.of(".", PUBLIC_FOLDER, pageName);
            final String mimeType = Files.probeContentType(filePath);
            final var length = Files.size(filePath);
            o.write(Server.buildResponseStatusHeadersOnOK(
                    mimeType, length, !r.headerExists("Connection: keep-alive")).getBytes());
            Files.copy(filePath, o);
            o.flush();
        } catch (InvalidPathException | IOException e) {
            e.printStackTrace();
            //Not found. Respond with 404
            try {
                o.write(Server.buildResponseStatusHeadersOnFail(
                        !r.headerExists("Connection: keep-alive")).getBytes());
                o.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void defaultHandlerForSpecificFolder(Request r, BufferedOutputStream o) {
        try {
            final Path filePath = Path.of(".", PUBLIC_FOLDER, r.getPathWithoutQueryString());
            final String mimeType = Files.probeContentType(filePath);
            final var length = Files.size(filePath);
            o.write(Server.buildResponseStatusHeadersOnOK(
                    mimeType, length, !r.headerExists("Connection: keep-alive")).getBytes());
            Files.copy(filePath, o);
            o.flush();
        } catch (InvalidPathException | IOException e) {
            e.printStackTrace();
            //Not found. Respond with 404
            try {
                o.write(Server.buildResponseStatusHeadersOnFail(
                        !r.headerExists("Connection: keep-alive")).getBytes());
                o.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
