import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Arrays;

public class RequestReader {
    /**
     * Reads http request from input stream, validates it and returns it as the Request object
     * @param in - BufferedInputStream of client socket
     * @return Request object if reading/parsing was ok, null if not (bad request)
     */
    public static Request readRequest (BufferedInputStream in, short requestLengthLimit) {
        try {
            in.mark(requestLengthLimit);
            final var buffer = new byte[requestLengthLimit];
            final var read = in.read(buffer);

            final String url = new String(Arrays.copyOf(buffer,read));

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
                final var contentLength = Request.extractHeader(headers, "Content-Length");
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
