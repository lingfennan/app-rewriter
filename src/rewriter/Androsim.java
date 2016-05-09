package rewriter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.google.common.base.Joiner;

public class Androsim {
	/* This should be the same as the parameters used in androguard to find similar methods, otherwise it doesn't make sense.
	 * In androsim.py, we specified a parameter to threshold the similarity of similar methods. We are currently using 0.6.
	 */
	public double simThreshold = 0.6;
	private double simScore = -1;	
	private String summary = null;
	private Set<String> newMethods = null;
	private Set<String> newSmaliMethods = null;
	private Set<String> similarMethods = null;
	private Set<String> similarSmaliMethods = null;
	public Androsim(String androsimPath, String diffMethodPath) {
		newMethods = new HashSet<String>();
		newSmaliMethods = new HashSet<String>();
		similarMethods = new HashSet<String>();
		similarSmaliMethods = new HashSet<String>();		
		if (androsimPath == null || diffMethodPath == null || androsimPath == "" || diffMethodPath == "") {
			System.out.println("No androsimPath or diffMethodPath found!");
			return;
		}
		JsonObject jsonSimResult = readJsonFromFile(androsimPath);
		simScore = jsonSimResult.getJsonNumber("score").doubleValue();
		summary = jsonSimResult.getString("summary");		
		JsonObject jsonMethodDiff = readJsonFromFile(diffMethodPath);
		try {
			JsonArray newM = jsonMethodDiff.getJsonArray("new_methods");
			JsonArray simM = jsonMethodDiff.getJsonArray("similar_methods");
			for (int i=0; i<newM.size(); i++) {
				newSmaliMethods.add(newM.getString(i));
				newMethods.add( smali2Soot(newM.getString(i)) );
			}
			for (int i=0; i<simM.size(); i++) {
				similarSmaliMethods.add(simM.getString(i));
				similarMethods.add( smali2Soot(simM.getString(i)) );
			}
		} catch (Exception e) {
			System.out.println("There is error loading " + diffMethodPath);
		}
	}
	
	private String convertClassName(String className) {
		// Lcom/qiangsky/a/e; -> com.qiangsky.a.e
		switch (className.trim()) {
		case "V":
			className = "void";
			break;
		case "Z":
			className = "boolean";
			break;
		case "B":
			className = "byte";
			break;
		case "S":
			className = "short";
			break;
		case "C":
			className = "char";
			break;
		case "I":
			className = "int";
			break;
		case "J":
			className = "long";
			break;
		case "F":
			className = "float";
			break;
		case "D":
			className = "double";
			break;
		default:
			// Get rid of 'L' and ';', and replace '/' with '.'
			className = className.substring(1, className.length()-1).replace('/', '.');;
		}
		return className;
	}
	
	private String convertMethodName(String methodName) {
		// Get return type, function name, para type
		/*  V	void - can only be used for return types
			Z	boolean
			B	byte
			S	short
			C	char
			I	int
			J	long (64 bits)
			F	float
			D	double (64 bits)
		 */
		String functionName = "", returnType = "";
		ArrayList<String> paraType = new ArrayList<String>();
		String[] parts = Pattern.compile("[()]").split(methodName);
		assert(parts.length == 3);
		
		functionName = parts[0].trim();
		if (parts[1].length() > 0) {
			for (String para : parts[1].split(" ")) {
				paraType.add(convertClassName(para));
			}
		}
		// V 54 -> void or Ljava/lang/String; 35 -> java.lang.String
		returnType = parts[2].split(" ")[0];
		returnType = convertClassName(returnType);
		return returnType + " " + functionName + "(" + Joiner.on(",").join(paraType) + ")";
	}
	
	private String smali2Soot(String smaliMethod) {
		/** Smali method: Lcom/qiangsky/a/e; <init> (Landroid/content/Context;)V 80
		 *  Soot method: <com.example.your.hideicon.zombie7: void <init>()>
		 */
		// Get rid of L
		int helpIndex = smaliMethod.indexOf(' ');
		String className = smaliMethod.substring(0, helpIndex);
		String methodName = smaliMethod.substring(helpIndex+1);			
		className = convertClassName(className);
		methodName = convertMethodName(methodName);
		return "<" + className + ": " + methodName + ">";
	}
	
	public double getSimScore() {
		return simScore;
	}
	
	public boolean isSimilar() {
		return simScore >= simThreshold;
	}
	
	public String getSummary() {
		return summary;
	}
	
	public Set<String> getSimilarMethods() {
		return similarMethods;
	}
	
	public Set<String> getSimilarSmaliMethods() {
		return similarSmaliMethods;
	}
	
	public Set<String> getNewMethods() {
		return newMethods;
	}

	public Set<String> getNewSmaliMethods() {
		return newSmaliMethods;
	}

	public boolean isDirtyMethod(String method) {
		if (isSimilar()) {
			if (similarMethods.contains(method)) {
				return true;
			} else if (newMethods.contains(method)) {
				return true;
			} else {
				return false;
			}
		} else {
			// if applicaitons are not similar, all methods are considered dirty
			return true;
		}
	}
	
	public Map<String, Set<String>> getDirtyEntryPoints(Map<String, Set<String>> entryWithCallbacks) {
		Map<String, Set<String>> dirtyEntries = new HashMap<String, Set<String>>();
		for (String className: entryWithCallbacks.keySet()) {
			Set<String> dirtyMethods = new HashSet<String>();
			for (String methodName: entryWithCallbacks.get(className)) {
				String methodSig = "<" + className + ": " + methodName + ">";
				if (newMethods.contains(methodSig) || similarMethods.contains(methodSig)) {
					dirtyMethods.add(methodName);
				}
			}
			if (dirtyMethods.size() > 0) {
				dirtyEntries.put(className, dirtyMethods);
			}
		}
		return dirtyEntries;
	}
	
	public Set<String> similarMethods(Set<String> exclude) {
		Set<String> tempSimilarMethods = new HashSet<String>(similarMethods);
		tempSimilarMethods.removeAll(exclude);
		return tempSimilarMethods;
	}
	
	public Set<String> newMethods(Set<String> exclude) {
		Set<String> tempNewMethods = new HashSet<String>(newMethods);
		tempNewMethods.removeAll(exclude);
		return tempNewMethods;
	}
    JsonObject readJsonFromFile(String filename) {
    	JsonObject result = null;
        try {
        	// Every line in the file is an json object.
        	StringBuilder sb = new StringBuilder();
        	String currentLine = null;
        	BufferedReader br = new BufferedReader(new FileReader(filename));
        	while ((currentLine = br.readLine()) != null) {
        		sb.append(currentLine);
        	}        		
    		JsonReader jsonReader = Json.createReader(new StringReader(sb.toString()));
    		result = jsonReader.readObject();
    		br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
    public String toString() {
    	return "sim score: " + simScore + "\nsummary: " + summary + "\nnew methods: " + newMethods
    			+ "\nsimilar methods: " + similarMethods;
    }
}
