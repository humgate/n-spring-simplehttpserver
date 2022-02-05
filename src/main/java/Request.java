import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class Request {
    //method
    private final Method method;
    //path without query string
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

    public String getQueryParam(String name) {
        Optional<NameValuePair> nameValuePair =
                URLEncodedUtils.parse(path, StandardCharsets.UTF_8)
                .stream()
                .filter((n)-> n.getName().equals(name))
                .findFirst();

        return nameValuePair.map(NameValuePair::getValue).orElse(null);
    }
}
