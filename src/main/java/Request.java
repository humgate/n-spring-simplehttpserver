import java.util.List;

public class Request {
    //method
    private Method method;
    //path without query string
    private String path;
    //headers
    private List<String> headers;
    //body
    private byte[] body;

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
}
