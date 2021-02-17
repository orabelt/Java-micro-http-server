import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class HttpServer  {
    private int port;
    // private Handler defaultHandler = null;
    // Two level map: first level is HTTP Method (GET, POST, OPTION, etc.), second level is the request paths.
    private Map<String, Map<String, Handler>> handlers = new HashMap<String, Map<String, Handler>>();

    ExecutorService executor = Executors.newCachedThreadPool(); //newFixedThreadPool(10);
    // todo:
    //executor.shutdown();
    //while (!executor.isTerminated()) {}
    //System.out.println("Finished all threads");

    public HttpServer(int port)  {
        this.port = port;
    }

    /**
     * @param path if this is the special string "/*", this is the default handler if
     *   no other handler matches.
     */
    public void addHandler(String method, String path, Handler handler)  {
        Map<String, Handler> methodHandlers = handlers.get(method);
        if (methodHandlers == null)  {
            methodHandlers = new HashMap<String, Handler>();
            handlers.put(method, methodHandlers);
        }
        methodHandlers.put(path, handler);
    }

    public void start() throws IOException  {
        ServerSocket socket = new ServerSocket(port);
        System.out.println("Listening on port " + port);
        Socket client;

        while ((client = socket.accept()) != null)  {
            System.out.println("Received connection from " + client.getRemoteSocketAddress().toString());
            SocketHandler handler = new SocketHandler(client, handlers);
            //Thread t = new Thread(handler);
            //t.start();
            executor.execute(handler);
        }
    }

    public static void main(String[] args) throws IOException  {
        HttpServer server = new HttpServer(5588);
        server.addHandler("GET", "/hello", new Handler()  {
            public void handle(Request request, Response response) throws IOException  {
                String html = "It works, " + request.getParameter("name") + "";
                response.setResponseCode(200, "OK");
                response.addHeader("Content-Type", "text/html");
                response.addBody(html);
            }
        });

        //server.addHandler("GET", "/*", new FileHandler());  // Default handler
        server.addHandler("GET", "/*", new JsonHandler());  // Default handler

        server.start();
    }
}

/**
 * Handlers must be thread safe.
 */
interface Handler  {
    public void handle(Request request, Response response) throws IOException;
}

class JsonHandler implements Handler {
    public void handle(Request request, Response response) throws IOException {
        try {
            String url = request.getPath().substring(1);
            if ( url.equalsIgnoreCase("favicon.ico") ) {
                response.setResponseCode(404, "Not Found");
                return;
            }

            url = url.trim().length() > 0 ?  url.replace('_',':') : "127.0.0.1:8080"; // 127.0.0.1_8080
            URL obj = new URL("http://" + url + "/api/v1/status");
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setConnectTimeout(1000);
            con.setReadTimeout(1000);
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(responseCode <= 299 ? con.getInputStream() : con.getErrorStream()));
            String inputLine;
            StringBuffer resp = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                resp.append(inputLine);
            }
            in.close();
            String json = resp.toString();

            if ( responseCode <= 299 ) {
                System.out.println("" + Thread.currentThread().toString() + " " + json);
            } else {
                System.err.println("" + Thread.currentThread().toString() + " Error: " + json);
            }

            response.setResponseCode(responseCode, con.getResponseMessage());
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Content-Type", "text/html");
            response.addBody(json);
        } catch (Exception e) {
            response.setResponseCode(404, "Not Found");
        }
    }
}

class FileHandler implements Handler {
    public void handle(Request request, Response response) throws IOException {
        try {
            FileInputStream file = new FileInputStream(request.getPath().substring(1));
            response.setResponseCode(200, "OK");
            response.addHeader("Content-Type", "text/html");
            StringBuffer buf = new StringBuffer();
            // TODO this is slow
            int c;
            while ((c = file.read()) != -1) {
                buf.append((char) c);
            }
            response.addBody(buf.toString());
        } catch (FileNotFoundException e) {
            response.setResponseCode(404, "Not Found");
        }
    }
}


class Request  {
    private String method;
    private String path;
    private String fullUrl;
    private Map<String, String> headers = new HashMap<String, String>();
    private Map<String, String> queryParameters = new HashMap<String, String>();
    private BufferedReader in;

    public Request(BufferedReader in)  {
        this.in = in;
    }

    public String getMethod()  {
        return method;
    }

    public String getPath()  {
        return path;
    }

    public String getFullUrl()  {
        return fullUrl;
    }

    // TODO support mutli-value headers
    public String getHeader(String headerName)  {
        return headers.get(headerName);
    }

    public String getParameter(String paramName)  {
        return queryParameters.get(paramName);
    }

    private void parseQueryParameters(String queryString)  {
        for (String parameter : queryString.split("&"))  {
            int separator = parameter.indexOf('=');
            if (separator > -1)  {
                queryParameters.put(parameter.substring(0, separator),
                        parameter.substring(separator + 1));
            } else  {
                queryParameters.put(parameter, null);
            }
        }
    }

    public boolean parse() throws IOException  {
        String initialLine = in.readLine();
        log(initialLine);
        StringTokenizer tok = new StringTokenizer(initialLine);
        String[] components = new String[3];
        for (int i = 0; i < components.length; i++)  {
            // TODO support HTTP/1.0?
            if (tok.hasMoreTokens())  {
                components[i] = tok.nextToken();
            } else  {
                return false;
            }
        }

        method = components[0];
        fullUrl = components[1];

        // Consume headers
        while (true)  {
            String headerLine = in.readLine();
            log(headerLine);
            if (headerLine.length() == 0)  {
                break;
            }

            int separator = headerLine.indexOf(":");
            if (separator == -1)  {
                return false;
            }
            headers.put(headerLine.substring(0, separator),
                    headerLine.substring(separator + 1));
        }

        // TODO should look for host header, Connection: Keep-Alive header,
        // Content-Transfer-Encoding: chunked

        if (components[1].indexOf("?") == -1)  {
            path = components[1];
        } else  {
            path = components[1].substring(0, components[1].indexOf("?"));
            parseQueryParameters(components[1].substring(
                    components[1].indexOf("?") + 1));
        }

        if ("/".equals(path))  {
            path = "/index.html";
        }

        return true;
    }

    private void log(String msg)  {
        System.out.println(msg);
    }

    public String toString()  {
        return method  + " " + path + " " + headers.toString();
    }
}


/**
 * Encapsulate an HTTP Response.  Mostly just wrap an output stream and
 * provide some state.
 */
class Response  {
    private OutputStream out;
    private int statusCode;
    private String statusMessage;
    private Map<String, String> headers = new HashMap<String, String>();
    private String body;

    public Response(OutputStream out)  {
        this.out = out;
    }

    public void setResponseCode(int statusCode, String statusMessage)  {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    public void addHeader(String headerName, String headerValue)  {
        this.headers.put(headerName, headerValue);
    }

    public void addBody(String body)  {
        headers.put("Content-Length", Integer.toString(body.length()));
        this.body = body;
    }

    public void send() throws IOException  {
        headers.put("Connection", "Close");
        out.write(("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n").getBytes());
        for (String headerName : headers.keySet())  {
            out.write((headerName + ": " + headers.get(headerName) + "\r\n").getBytes());
        }
        out.write("\r\n".getBytes());
        if (body != null)  {
            out.write(body.getBytes());
        }
    }
}

class SocketHandler implements Runnable  {
    private Socket socket;
    //private Handler defaultHandler;
    private Map<String, Map<String, Handler>> handlers;

    public SocketHandler(Socket socket, Map<String, Map<String, Handler>> handlers)  {
        this.socket = socket;
        this.handlers = handlers;
    }

    /**
     * Simple responses like errors.  Normal reponses come from handlers.
     */
    private void respond(int statusCode, String msg, OutputStream out) throws IOException  {
        String responseLine = "HTTP/1.1 " + statusCode + " " + msg + "\r\n\r\n";
        log(responseLine);
        out.write(responseLine.getBytes());
    }

    public void run()  {
        BufferedReader in = null;
        OutputStream out = null;

        try  {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = socket.getOutputStream();

            Request request = new Request(in);
            if (!request.parse())  {
                respond(500, "Unable to parse request", out);
                return;
            }

            // TODO most specific handler
            boolean foundHandler = false;
            Response response = new Response(out);
            Map<String, Handler> methodHandlers = handlers.get(request.getMethod());
            if (methodHandlers == null)  {
                respond(405, "Method not supported", out);
                return;
            }

            for (String handlerPath : methodHandlers.keySet())  {
                if (handlerPath.equals(request.getPath()))  {
                    methodHandlers.get(request.getPath()).handle(request, response);
                    response.send();
                    foundHandler = true;
                    break;
                }
            }

            if (!foundHandler)  {
                if (methodHandlers.get("/*") != null)  {
                    methodHandlers.get("/*").handle(request, response);
                    response.send();
                } else  {
                    respond(404, "Not Found", out);
                }
            }
        } catch (IOException e)  {
            try  {
                e.printStackTrace();
                if (out != null)  {
                    respond(500, e.toString(), out);
                }
            } catch (IOException e2)  {
                e2.printStackTrace();
                // We tried
            }
        } finally  {
            try  {
                if (out != null)  {
                    out.close();
                }
                if (in != null)  {
                    in.close();
                }
                socket.close();
            } catch (IOException e)  {
                e.printStackTrace();
            }
        }
    }

    private void log(String msg)  {
        System.out.println(msg);
    }
}