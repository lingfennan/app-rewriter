package rewriter;

import java.io.File;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
// import org.apache.commons.cli.Option;
// import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import edu.gatech.gtisc.simplepermanalysis.SimplePermAnalysis;

import edu.gatech.gtisc.simplepermanalysis.SimplePermAnalysis;
import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.StringConstant;
import soot.options.Options;


public class SysCallInstrumentation {
	public static org.apache.commons.cli.Options options = null;
	public static String apkPath = null;
	public static String androidJarDirPath = null;
	public static String outDir = null;	
	// The system call APIs that is interesting to us.
	public static Set<String> sysCalls = null;
	// The classes that we want to instrument.
	public static Set<String> extraClasses = null;

	private static void buildOptions() {
		options = new org.apache.commons.cli.Options();
		
		options.addOption("apk", true, "apk file");
		options.addOption("androidJarDir", true, "android jars directory");
		options.addOption("outDir", true, "out dir");
		options.addOption("suppressOutput", false, "whether to output or not");
	}
	
	private static void parseOptions(String[] args) {
		Locale locale = new Locale("en", "US");
		Locale.setDefault(locale);
		
		CommandLineParser parser = new PosixParser();
		CommandLine commandLine;
		
		try {
			commandLine = parser.parse(options, args);
			
			commandLine.getArgs();
			org.apache.commons.cli.Option[] clOptions = commandLine.getOptions();
			
			for (int i = 0; i < clOptions.length; i++) {
				org.apache.commons.cli.Option option = clOptions[i];
				String opt = option.getOpt();
				
				if (opt.equals("apk")){
					apkPath = commandLine.getOptionValue("apk");
				} else if (opt.equals("androidJarDir")) {
					androidJarDirPath = commandLine.getOptionValue("androidJarDir");
				} else if (opt.equals("outDir")) {
					outDir = commandLine.getOptionValue("outDir");
				}
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
		
		/* Analyze permission of the added module.
		 * 1. Analyze the permissions for the repackaged application.
		 * 2. Get the added module. So we have permissions related to the added part. 
		 */
		// SimplePermAnalysis.main(args);
		// SimplePermAnalysis.class2Permission
		// SimplePermAnalysis.entryPointClasses
		// Load the permissions from class relation graph.
		
		//prefer Android APK files// -src-prec apk
		Options.v().set_src_prec(Options.src_prec_apk);
		
		//output as APK, too//-f J
		Options.v().set_output_format(Options.output_format_dex);
		
        // resolve the PrintStream and System soot-classes
		Scene.v().addBasicClass("java.io.PrintStream",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System",SootClass.SIGNATURES);

        PackManager.v().getPack("jtp").add(new Transform("jtp.myInstrumenter", new BodyTransformer() {

			@Override
			protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {
				final PatchingChain<Unit> units = b.getUnits();
				
				//important to use snapshotIterator here
				for(Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
					final Unit u = iter.next();
					u.apply(new AbstractStmtSwitch() {
						
						public void caseInvokeStmt(InvokeStmt stmt) {
							InvokeExpr invokeExpr = stmt.getInvokeExpr();
							if(invokeExpr.getMethod().getName().equals("onDraw")) {

								Local tmpRef = addTmpRef(b);
								Local tmpString = addTmpString(b);
								
								  // insert "tmpRef = java.lang.System.out;" 
						        units.insertBefore(Jimple.v().newAssignStmt( 
						                      tmpRef, Jimple.v().newStaticFieldRef( 
						                      Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), u);

						        // insert "tmpLong = 'HELLO';" 
						        String toPrint = "HELLO FROM:" + b.getMethod().getDeclaringClass().getName();
						        		b.getClass().getName();
						        units.insertBefore(Jimple.v().newAssignStmt(tmpString, 
						                      StringConstant.v(toPrint)), u);
						        
						        // insert "tmpRef.println(tmpString);" 
						        SootMethod toCall = Scene.v().getSootClass("java.io.PrintStream").getMethod("void println(java.lang.String)");                    
						        units.insertBefore(Jimple.v().newInvokeStmt(
						                      Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), tmpString)), u);
						        
						        //check that we did not mess up the Jimple
						        b.validate();
							}
						}
						
					});
				}
			}

		}));
        
		String[] tokens = apkPath.split("/");
		String apkName = tokens[tokens.length - 1];
		String[] sootArgs = new String[]{
			"-android-jars",
			androidJarDirPath,
			"-process-dir",
			apkPath, 
			"-d",
			outDir + File.separator + apkName,
			"-force-android-jar",
			androidJarDirPath + "/android-22/android.jar"
		};        
		
		soot.Main.main(sootArgs);
	}

    private static Local addTmpRef(Body body)
    {
        Local tmpRef = Jimple.v().newLocal("tmpRef", RefType.v("java.io.PrintStream"));
        body.getLocals().add(tmpRef);
        return tmpRef;
    }
    
    private static Local addTmpString(Body body)
    {
        Local tmpString = Jimple.v().newLocal("tmpString", RefType.v("java.lang.String")); 
        body.getLocals().add(tmpString);
        return tmpString;
    }
}