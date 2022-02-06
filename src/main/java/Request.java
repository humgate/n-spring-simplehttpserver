import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class Request {
    //method
    private final Method method;
    //path (full, with query string)
    private final String path;
    //headers
    private final List<String> headers;
    //body
    private final byte[] body;

    public Request(Method method, String path, List<String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
    }

    public Method getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    /**
     * Gets query string parameter value with given parameter name
     * @param name - parameter name
     * @return - parameter value if such parameter name exists in query string, otherwise null
     */
    public String getQueryParam(String name) {
        try {
            Optional<NameValuePair> nameValuePair =
                    // URLEncodedUtils.parse(path, StandardCharsets.UTF_8) does not work..so:
                    URLEncodedUtils.parse(new URI(path), StandardCharsets.UTF_8)
                            .stream()
                            .filter((n) -> n.getName().equals(name))
                            .findFirst();
            return nameValuePair.map(NameValuePair::getValue).orElse(null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @return collection of name-value pairs extracted from Request path, null if empty or
     * in case of an error
     */
    public List<NameValuePair> getQueryParams() {
        try {
            return URLEncodedUtils.parse(new URI(path), StandardCharsets.UTF_8);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tests if there is such header in request headers collection
     * @param header - header to test
     * @return true if exists otherwise - false
     */
    public boolean headerExists (String header) {
        return getHeaders().contains(header);
    }

    /**
     * Extracts header value form the header with given name
     * @param headers headers collection (without ending \n\r)
     * @param header header name to search for (without ending \n\r)
     * @return header value
     */
    public static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    /**
     * @return part of the path from beginning to the first symbol of query string (excluding ?)
     */
    public String getPathWithoutQueryString() {
        return (path.indexOf('?') != -1) ? path.substring(0,path.indexOf('?')) : path;
    }
}
