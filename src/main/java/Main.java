import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class Main {
    static final short SERVER_PORT = 9999;
    static final String PUBLIC_FOLDER = "public";

    public static void main(String[] args) {
        final var server = new Server();

        //Common "root" ("parent") handler for each resource located in PUBLIC_FOLDER
        server.addHandler(Method.GET, "\\", (r, o) -> {
            try {
                final Path filePath = Path.of(".", PUBLIC_FOLDER, r.getPath());
                final String mimeType = Files.probeContentType(filePath);
                final var length = Files.size(filePath);
                o.write(Server.buildResponseStatusHeadersOnOK(mimeType, length).getBytes());
                Files.copy(filePath, o);
                o.flush();
            } catch (InvalidPathException | IOException e) {
              e.printStackTrace();
                //Not found. Respond with 404
                try {
                    o.write(Server.buildResponseStatusHeadersOnFail().getBytes());
                    o.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        //Custom exact match handler for PUBLIC_FOLDER/
        server.addHandler(Method.GET, "/classic.html", (r, o) -> {
                final var template = Files.readString(r.getPath());
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());

            try {
                final Path filePath = Path.of(".", PUBLIC_FOLDER, r.getPath());
                final String mimeType = Files.probeContentType(filePath);
                final var length = Files.size(filePath);
                o.write(Server.buildResponseStatusHeadersOnOK(mimeType, length).getBytes());
                Files.copy(filePath, o);
                o.flush();
            } catch (InvalidPathException | IOException e) {
                e.printStackTrace();
                //Not found. Respond with 404
                try {
                    o.write(Server.buildResponseStatusHeadersOnFail().getBytes());
                    o.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });


        server.listen(SERVER_PORT);
    }
}
