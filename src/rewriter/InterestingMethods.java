package rewriter;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.google.protobuf.TextFormat;

import gtisc.proto.rewriter.Rewriter.PointOfInterest;
import gtisc.proto.rewriter.Rewriter.RewriterConfig;
import soot.SootClass;
import soot.SootMethod;


public class InterestingMethods {
	private Set<String> methodSigs;	
	private Set<String> classNames;
	// $className.$methodName
	private Set<String> methodNames;
	public InterestingMethods (String configPath) {
		methodSigs = new HashSet<String>();
		classNames = new HashSet<String>();
		methodNames = new HashSet<String>();
		
		if (configPath == null) {
			System.out.println("No config path found!");
			return;
		}

		try {
			RewriterConfig.Builder rwc = RewriterConfig.newBuilder();
			FileReader fileReader;
			fileReader = new FileReader(configPath);
			TextFormat.merge(fileReader, rwc);
			for (PointOfInterest pi : rwc.build().getInterestsList()) {
				if (pi.getPointType() == PointOfInterest.POINT_TYPE.METHOD_SIGNATURE)
					methodSigs.add(pi.getContent());
				else if (pi.getPointType() == PointOfInterest.POINT_TYPE.CLASS_NAME)
					classNames.add(pi.getContent());
				else if (pi.getPointType() == PointOfInterest.POINT_TYPE.CLASS_METHOD_NAME)
					methodNames.add(pi.getContent());
				else System.out.println("Unknown POINT_TYPE");
			}
			fileReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isDirty(String checkStr) {
		if (methodSigs.contains(checkStr) || classNames.contains(checkStr) || methodNames.contains(checkStr)) 
			return true;
		else return false;
	}

	public boolean isDirty(SootMethod sootMethod) {
		if (methodSigs.contains(sootMethod.getSignature())) return true;
		String className = sootMethod.getDeclaringClass().getName();		
		if (classNames.contains(className)) return true;
		String methodName = sootMethod.getName();		
		if (methodNames.contains(className + "." + methodName)) return true;
		return false;
	}
	
	public boolean isDirty(SootClass sootClass) {
		if (classNames.contains(sootClass.getName())) return true;
		return false;
	}
}
