package rewriter;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
// import org.apache.commons.cli.Option;
// import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import gtisc.proto.rewriter.JobRunner.AppAnalysisConfig;
import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.ResolutionFailedException;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StringConstant;
import soot.options.Options;


public class SysCallInstrumentation {
	public static org.apache.commons.cli.Options options = null;
	private static AppAnalysisConfig config = null;
	
	// The system call APIs that is interesting to us.
	private static PSCout psCout;
	private static Androsim androsim;
	private static InterestingMethods interests;

	private static void buildOptions() {
		options = new org.apache.commons.cli.Options();
		
		options.addOption("apk", true, "apk file");
		options.addOption("androSimPath", true, "path to the androsim results");
		options.addOption("diffMethodPath", true, "Path to the diff method results");		
		options.addOption("androidJarDir", true, "android jars directory");
		options.addOption("configPath", true, "The path to the configuration file");
		options.addOption("resultDir", true, "The directory to store the results");		
		options.addOption("trackAll", false, "Track all methods (after excluding androidsim Results), r.t. permission related only!");
	}
	
	private static void parseOptions(String[] args) {
		Locale locale = new Locale("en", "US");
		Locale.setDefault(locale);
		
		CommandLineParser parser = new PosixParser();
		CommandLine commandLine;
		AppAnalysisConfig.Builder configBuilder = AppAnalysisConfig.newBuilder();
		
		try {
			commandLine = parser.parse(options, args);
			
			commandLine.getArgs();
			org.apache.commons.cli.Option[] clOptions = commandLine.getOptions();
			
			for (int i = 0; i < clOptions.length; i++) {
				org.apache.commons.cli.Option option = clOptions[i];
				String opt = option.getOpt();
				
				if (opt.equals("apk")){
					configBuilder.setApkPath(commandLine.getOptionValue("apk"));
				} else if (opt.equals("androSimPath")) {
					configBuilder.setAndrosimPath(commandLine.getOptionValue("androSimPath"));
				} else if (opt.equals("diffMethodPath")) {
					configBuilder.setDiffMethodPath(commandLine.getOptionValue("diffMethodPath"));
				} else if (opt.equals("androidJarDir")) {
					configBuilder.setAndroidJarDirPath(commandLine.getOptionValue("androidJarDir"));
					configBuilder.setForceAndroidJarPath(configBuilder.getAndroidJarDirPath() + "/android-21/android.jar");
				} else if (opt.equals("configPath")) {
					configBuilder.setConfigPath(commandLine.getOptionValue("configPath"));
				} else if (opt.equals("resultDir")) {
					configBuilder.setResultDir(commandLine.getOptionValue("resultDir"));					
				} else if (opt.equals("trackAll")) {
					configBuilder.setTrackAll(true);
				}
				config = configBuilder.build();
			}
		} catch (ParseException ex) {
			ex.printStackTrace();
			return;
		}
	}
	
	public static void main(String[] args) {
		//enable assertion
		//ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
		
		buildOptions();
		parseOptions(args);
		
		// initialize androsim
		androsim = new Androsim(config.getAndrosimPath(), config.getDiffMethodPath());
		
		// initialize interesting methods
		interests = new InterestingMethods(config.getConfigPath());

		// initialize PSCout
		String dataDir = System.getProperty("user.dir") + File.separator + "data";
		psCout = new PSCout(dataDir + File.separator + "jellybean_allmappings",
				dataDir + File.separator + "jellybean_intentpermissions");

		// prefer Android APK files// -src-prec apk
		Options.v().set_src_prec(Options.src_prec_apk);
		
		// output as APK, too//-f J
		Options.v().set_output_format(Options.output_format_dex);
		// Options.v().set_output_format(Options.output_format_jimple);
        
        // Borrowed from CallTracer.
        Options.v().set_allow_phantom_refs(true);
		Options.v().set_whole_program(true);
		Scene.v().addBasicClass("java.lang.StringBuilder", SootClass.SIGNATURES);
		Scene.v().addBasicClass("android.util.Log", SootClass.SIGNATURES);

        PackManager.v().getPack("jtp").add(new Transform("jtp.sysCallInstrumenter", new BodyTransformer() {

			@Override
			protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {				
				// Initialize the essential variables.
				final SootMethod currMethod = b.getMethod();
				final String className = b.getMethod().getDeclaringClass().getName();
				final String currSubSig = currMethod.getSubSignature();
				final String currSig = currMethod.getSignature();
				final PatchingChain<Unit> units = b.getUnits();
				
				if (!androsim.isDirtyMethod(currSig)) {
					return;
				}

				// StringBuilder callTracerSB;
				final Local sbRef = addTmpRef(b, "callTracerSB", "java.lang.StringBuilder");
				final Local contentStr = addTmpRef(b, "callTracerContent", "java.lang.String");
				
				// important to use snapshotIterator here
				for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
			        final Unit u = iter.next();
			        u.apply(new AbstractStmtSwitch() {
		                
		                public void caseInvokeStmt(InvokeStmt stmt) {
	                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
	                        addLogForSysCall(invokeExpr, null, b, units, u, sbRef, contentStr, className, currSig, currSubSig);
		                }
		                
		                public void caseAssignStmt(AssignStmt stmt) {
							Value rValue = stmt.getRightOp();
							Value lValue = stmt.getLeftOp();
							InvokeExpr invokeExpr = null;
							if (rValue instanceof InvokeExpr) {
								invokeExpr = (InvokeExpr) rValue;
							    addLogForSysCall(invokeExpr, lValue, b, units, u, sbRef, contentStr, className, currSig, currSubSig);
							}
		                }
						
			        });
				}
			}
		}));
        
		File apkFile = new File(config.getApkPath());		
		String[] sootArgs = new String[]{
			"-android-jars",
			config.getAndroidJarDirPath(),
			"-process-dir",
			apkFile.getAbsolutePath(),
			"-d",
			config.getResultDir() + File.separator + apkFile.getName(),
			"-force-android-jar",
			config.getForceAndroidJarPath()
		};
		soot.Main.main(sootArgs);
	}

    private static Local addTmpRef(Body body, String name, String type)
    {
        Local tmpRef = Jimple.v().newLocal(name, RefType.v(type));
        body.getLocals().add(tmpRef);
        return tmpRef;
    }
    
    private static Local addTmpString(Body body)
    {
        Local tmpString = Jimple.v().newLocal("tmpString", RefType.v("java.lang.String")); 
        body.getLocals().add(tmpString);
        return tmpString;
    }
    
    private static void sbToStringAndLog(List<Unit> stmts, Local sbRef, Local contentStr, String tag) {
		// contentStr = sb.toString()
		SootMethod sbToStr = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.String toString()");
		stmts.add(Jimple.v().newAssignStmt(contentStr, Jimple.v().newVirtualInvokeExpr(sbRef, sbToStr.makeRef())));
		
		// Log.e("CallTracer", contentStr)
		SootMethod logE = Scene.v().getSootClass("android.util.Log").getMethod("int e(java.lang.String,java.lang.String)");
		stmts.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(logE.makeRef(), StringConstant.v(tag), contentStr)));
    }
    
    private static void addValueString(Value arg, List<Unit> stmts, Local sbRef, boolean firstOne) {
    	// Convert Value to String, and add them to the String Builder
		String typeStr = arg.getType().toString();
		SootMethod strSbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(java.lang.String)");
		// Log type string.
		String logTypeStr = "\"" + typeStr + "\": \"";
		if (!firstOne) logTypeStr = "," + logTypeStr;
		stmts.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, strSbAppend.makeRef(),
				StringConstant.v(logTypeStr))));
		SootMethod sbAppend = null;				
		boolean logArg = false;
		boolean addNullCheck = false;
		String strValue = null;
		if (typeStr.equals("byte")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(int)");
			logArg = true;
		} else if (typeStr.equals("short")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(int)");
			logArg = true;
		} else if (typeStr.equals("char")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(char)");
			logArg = true;
		} else if (typeStr.equals("boolean")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(boolean)");
			logArg = true;			
		} else if (typeStr.equals("double")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(double)");
			logArg = true;
		} else if (typeStr.equals("float")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(float)");
			logArg = true;			
		} else if (typeStr.equals("int")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(int)");
			logArg = true;			
		} else if (typeStr.equals("long")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(long)");
			logArg = true;			
		} else if (typeStr.equals("java.lang.String")) {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(java.lang.String)");
			logArg = true;
			addNullCheck = true;
		} else if (typeStr.equals("null_type")){
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(java.lang.String)");		
			strValue = "null";
		} else {
			sbAppend = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(java.lang.Object)");
			logArg = true;
			addNullCheck = true;
		}
		
		// Update the statements.
		if (addNullCheck) {
			/**
			 * add null check
			 * if p != null; Log.e(arg); else Log.e(null);
			 */
			Value pNull = Jimple.v().newEqExpr(arg, IntConstant.v(0));			
			Unit appendArg = Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppend.makeRef(), arg));
			Unit appendNull = Jimple.v().newAssignStmt(sbRef,
					Jimple.v().newVirtualInvokeExpr(sbRef, sbAppend.makeRef(), StringConstant.v("null")));
			Unit appendEnd = Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, strSbAppend.makeRef(), StringConstant.v("\"")));
			stmts.add(Jimple.v().newIfStmt(pNull, appendNull));
			stmts.add(appendArg);
			stmts.add(Jimple.v().newGotoStmt(appendEnd));
			stmts.add(appendNull);
			stmts.add(appendEnd);
		} else {
			if (logArg) {
				stmts.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppend.makeRef(), arg)));
			}
			if (strValue != null) {
				stmts.add(Jimple.v().newAssignStmt(sbRef,
						Jimple.v().newVirtualInvokeExpr(sbRef, sbAppend.makeRef(), StringConstant.v(strValue))));
			}
			stmts.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, strSbAppend.makeRef(), StringConstant.v("\""))));
		}
    }
    
    private static void addLogForSysCall(InvokeExpr invokeExpr, Value returnValue, Body body,
    		PatchingChain<Unit> units, Unit currentUnit,
    		Local sbRef, Local contentStr, String className, String methodSig, String methodSubsig) {
		/* This function does the following changes to the original function body.
		 * 1. Print class name, method name, permission
		 * 2a. If argument is primitive type, log Argument value, else Argument type
		 * 2b. If argument is non-primitive, call toString method, print toString()
		 * 3. If return value is not void, follow the similar procedure to insert expression after a unit.
		 */
    	// Insert the required logic before each API call invocation and after each API call invocation.
		SootMethod targetMethod = null;
		try{
			targetMethod = invokeExpr.getMethod();
		} catch (ResolutionFailedException e) {
			e.printStackTrace();
			return;
		}
		String targetClassName = targetMethod.getDeclaringClass().getName();
		String targetMethodName = targetClassName + "." + targetMethod.getName();
		String permission = psCout.getApiPermission(targetMethodName);
		boolean interesting = interests.isDirty(targetMethod);
		
		// Continue transform only in three cases: permission related, interests, or trackAll
		if (!config.getTrackAll() && permission == null && !interesting) {
			return;
		}

		// Initialize the tag, which can be used to filter results.
		String tag;
		if (permission != null) {
			// Log("CallTracer"), trace permissions
			tag = "CallTracer";
		} else if (interesting) {
			// Log("InterestTracer"), trace interests
			tag = "InterestTracer";
		} else if (config.getTrackAll()) {
			// Log("InfoTracer"), trace call stacks
			tag = "InfoTracer";
		} else {
			System.out.println("This shouldn't happen, in tag settings.");
			return;
		}
		
		System.out.println("Instrumenting:[method]" + methodSig + ", [permission]" + permission);
		List<Unit> before = new ArrayList<Unit>();
		List<Unit> after = new ArrayList<Unit>();
		
		// The log format:
		// CallTracer:{"Permission": "Some",
		// "Source": {"Class": "ClassName", "Signature": "methodSig", "SubSignature": "subsignature"},
		// "Target": {"Class": "ClassName", "Signature": "methodSig", "SubSignature": "subsignature",
		// 		"IsJavaLibrary": true, "IsStatic": true, "InstanceString": "if there is one"},
		// "Parameters": {"typea": "a", "typeb": "b", "typec": "c"},
		// "Return": {"returntype": "hello world"} }
		// ============================= Step 1: print class name, method name, permission =============================
		// callTracerSB = new java.lang.StringBuilder
		{
			SootMethod sbAppendO = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(java.lang.Object)");		
			NewExpr newSBExpr = Jimple.v().newNewExpr(RefType.v("java.lang.StringBuilder"));
			before.add(Jimple.v().newAssignStmt(sbRef, newSBExpr));
			
			//specialinvoke callTracerSB.init<>
			SootMethod sbConstructor = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("void <init>(java.lang.String)");
			// CallTracer:{"Permission": "Some"
			before.add(
					Jimple.v().newInvokeStmt(
						Jimple.v().newSpecialInvokeExpr(sbRef, sbConstructor.makeRef(), 
								StringConstant.v(tag + ":{\"Permission\":\"" + permission + "\", " +
										"\"Source\": {\"Class\": \"" + className + "\", \"Signature\": \"" + methodSig + "\"," +
											"\"SubSignature\": \"" + methodSubsig + "\"}," +
										"\"Target\": {\"Class\": \"" + targetClassName + "\", \"Signature\":\"" + targetMethod.getSignature() + "\"," +
											"\"SubSignature\": \"" + targetMethod.getSubSignature() + "\"," +
											"\"IsStatic\": " + targetMethod.isStatic() + "," +
											"\"IsJavaLibrary\": " + targetMethod.isJavaLibraryMethod() ))));
		}
		// ============================= Step 2: print argument values based on their types =============================
		// callTracerSB = new java.lang.StringBuilder
		{
			// this
			SootMethod sbAppendS = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(java.lang.String)");
			/*
			 * The identity statement seems not interesting, and it some times causes crashes, therefore, I disabled this logging.
			 * vaultyfree.apk -> hashCode() on null object
			 *  
			 * TODO (ruian): come up with a solution, instead of this temporary hack.
			 * 
			if ((invokeExpr instanceof InstanceInvokeExpr) && !(invokeExpr instanceof SpecialInvokeExpr)) {
				before.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppendS.makeRef(), StringConstant.v(", \"InstanceString\": \""))));
				Value baseValue = ((InstanceInvokeExpr) invokeExpr).getBase();
				addValueString(baseValue, before, sbRef, true);
				
				// SootMethod sbAppendO = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(java.lang.Object)");
				// before.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppendO.makeRef(), baseValue)));
				// before.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppendS.makeRef(), StringConstant.v("\""))));
			}
			*/
			before.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppendS.makeRef(), StringConstant.v("},"))));
			
			// append the parameters
			List<Value> args = invokeExpr.getArgs();
			before.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppendS.makeRef(), StringConstant.v("\"Parameters\": {"))));			
			for (int i=0; i < args.size(); i++) {
				if (i==0) {
					addValueString(args.get(i), before, sbRef, true);
				} else {
					addValueString(args.get(i), before, sbRef, false);
				}
			}
			before.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppendS.makeRef(), StringConstant.v("}"))));
			
			// Log.e("CallTracer", callTracerSB.toString())
			// sbToStringAndLog(before, sbRef, contentStr, tag);
		}
		// ============================= Step 3: print return values based on their types =============================
		{
			SootMethod sbAppendS = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("java.lang.StringBuilder append(java.lang.String)");							
			if (returnValue != null) {
				// callTracerSB = new java.lang.StringBuilder
				// NewExpr newSBExpr = Jimple.v().newNewExpr(RefType.v("java.lang.StringBuilder"));
				// after.add(Jimple.v().newAssignStmt(sbRef, newSBExpr));
				//specialinvoke callTracerSB.init<>
				// SootMethod sbConstructor = Scene.v().getSootClass("java.lang.StringBuilder").getMethod("void <init>(java.lang.String)");
				
				after.add(Jimple.v().newInvokeStmt(
						Jimple.v().newSpecialInvokeExpr(sbRef, sbAppendS.makeRef(), StringConstant.v(", \"Return\": {"))));
				
				// append the return value
				addValueString(returnValue, after, sbRef, true);
				// Log.e("CallTracer", callTracerSB.toString())
				after.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppendS.makeRef(), StringConstant.v("}"))));				
			}
			after.add(Jimple.v().newAssignStmt(sbRef, Jimple.v().newVirtualInvokeExpr(sbRef, sbAppendS.makeRef(), StringConstant.v("}"))));					
		}
		// Log.e($tag, callTracerSB.toString())
		sbToStringAndLog(after, sbRef, contentStr, tag);		
		units.insertBefore(before, currentUnit);		
		units.insertAfter(after, currentUnit);
		body.validate();
    }
}