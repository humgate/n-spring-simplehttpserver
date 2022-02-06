import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Server demo class. Registers several handlers and launches listening.
 * The first handler is specific full-path handler, it triggers only when client requests exactly
 * for path specified in handler path ("/" which means http://localhost:9999).
 * The second handler is specific full-path handler, it triggers only when client requests exactly
 * for path specified in handler path (relatively to PUBLIC_FOLDER).
 * The third handler is "common" which means it is only seeked for if no full-path handlers found
 * for request path, and it handles all request paths whose parent folder is specified in handler path.
 * In this example "common" handler will handle all requests for resources located in PUBLIC_FOLDER
 * except classic.html, which is handled by specific full-path handler
 *
 */
public class Main {
    static final short SERVER_PORT = 9999;
    static final String PUBLIC_FOLDER = "public";

    public static void main(String[] args) {
        final var server = new Server();

        //Custom exact match handler for root path like http://localhost:9999"
        server.addHandler(Method.GET, "/", (r, o) -> {
            try {
                final Path filePath = Path.of(".", PUBLIC_FOLDER, "/index.html");
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
        });

        //Custom exact match handler for PUBLIC_FOLDER/classic.html"
        server.addHandler(Method.GET, "/classic.html", (r, o) -> {
            try {
                final var filePath = Path.of(".", PUBLIC_FOLDER, r.getPath());
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

        //Common "root" ("parent") handler for each resource located in PUBLIC_FOLDER
        server.addHandler(Method.GET, "\\", (r, o) -> {
            try {
                final Path filePath = Path.of(".", PUBLIC_FOLDER, r.getPath());
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
        });

        server.listen(SERVER_PORT);
    }
}
