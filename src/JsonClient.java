import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
//import org.json.JSONObject;

public class JsonClient {
    public static void main(String[] args) {
        try {
            JsonClient.call("http://192.168.1.10:8080/api/v1/status");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String call(String url) throws Exception {
        //String url = "http://192.168.1.10:8080/api/v1/status";
        URL obj = new URL(url);
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
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        String json = response.toString();

        if ( responseCode <= 299 ) {
            System.out.println(json);
            return json;
        } else {
            System.err.println("Error: " + json);
            return "";
        }

        //Read JSON response and print
        //JSONObject myResponse = new JSONObject(response.toString());
        //System.out.println("result after Reading JSON Response");
        //System.out.println("statusCode- "+myResponse.getString("statusCode"));

    }
}