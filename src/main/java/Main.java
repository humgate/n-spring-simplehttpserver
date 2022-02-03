import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class Main {
    static final short SERVER_PORT = 9999;
    static final String PUBLIC_FOLDER = "public";

    public static void main(String[] args) {
        final var server = new Server();
        server.addHandler(Method.GET, "/spring.svg", (r, o) -> {
            final Path filePath;
            final String mimeType;
            try {
                filePath = Path.of(".", PUBLIC_FOLDER, r.getPath());
                mimeType = Files.probeContentType(filePath);
                final var length = Files.size(filePath);
                o.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                Files.copy(filePath, o);
                o.flush();
            } catch (InvalidPathException | IOException e) {
                //Not found. Respond with 404
                try {
                    o.write((
                            "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    o.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        server.listen(SERVER_PORT);
    }
}
