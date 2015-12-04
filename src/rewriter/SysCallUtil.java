package rewriter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

public class SysCallUtil {

    public static List<JsonObject> readJsonFromFile(String filename) {
    	List<JsonObject> result = new ArrayList<JsonObject>();
        try {
        	// Every line in the file is an json object.
        	String currentLine = null;
        	BufferedReader br = new BufferedReader(new FileReader(filename));
        	while ((currentLine = br.readLine()) != null) {
        		JsonReader jsonReader = Json.createReader(new StringReader(currentLine));
        		result.add(jsonReader.readObject());
        	}
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
