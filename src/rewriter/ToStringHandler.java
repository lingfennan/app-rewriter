package rewriter;

import java.util.ArrayList;
import java.util.HashMap;

import soot.SootMethod;

public class ToStringHandler {
	/* All the functions
	 * What if some functions doesn't have the toString function
	 * Choice 1: output all the primitive values?
	 * Choice 2: output all the fields?
	 * Choice 3: output the parameters taken by the constructor function?
	 * Choice 4: default, ignore
	 */
	private int choice;
	private static HashMap<String, ArrayList<String>> toString2Calls = null;  // maps toString to calls invoked in 
	private static HashMap<String, ArrayList<String>> toStringClasses = null;  

	
	public ToStringHandler() {
		this(1);
	}
	
	public ToStringHandler(int choice) {
		this.choice = choice;
	}
	
	public SootMethod modifyToString() {
		// This is not urgent now! Implement this when actually needed!
		return null;
	}
	
	public void collectToStringClasses () {
		// toStringClasses
	}
	
	public void collectToStringCalls () {
		// toString2Calls
	}
}
