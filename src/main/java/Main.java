import java.io.BufferedOutputStream;
import java.io.IOException;

public class Main {
    static final short SERVER_PORT = 9999;

    public static void main(String[] args) {
        final var server = new Server();
        server.addHandler(Method.GET,"/spring.svg", (r, os) -> {
            try {
                os.write(r.getBody());
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
        server.listen(SERVER_PORT);
    }
}
