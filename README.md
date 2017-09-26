# To run the jar file #
Step 1: Rewrite an app, apk, androidJarDir and resultDir are necessary parameters, by default, it instruments all the API calls specified by PScout.
```
java -jar rewriter.jar
-apk /Users/ruian/Desktop/app-signature/third-party/SimplePermAnalysis/testdata/3748917127f5d98f017109c1f7ddc515893feb16f1a4892bd4fa4e05eb0ca8d6.offender.apk
-androidJarDir /Users/ruian/Library/Android/sdk/platforms
-resultDir /Users/ruian/Desktop/app-signature/data/module-results
[-androSimPath /Users/ruian/Desktop/app-signature/third-party/SimplePermAnalysis/testdata/androsim_result.txt]
[-diffMethodPath /Users/ruian/Desktop/app-signature/third-party/SimplePermAnalysis/testdata/diff_methods.txt]
[-configPath /Users/ruian/Desktop/app-signature/third-party/app-rewriter/data/webview.config]
[-trackAll]
```

Step 2: Sign an app
```
jarsigner -verbose -sigalg SHA1withDSA -digestalg SHA1 -keystore ../own-app/my-release-key.keystore $APK mykey -storepass 12341234
```

Step 3: Run an app
```
cp $MODIFIED_APK $APK.offender.rewrite.apk
```

# Dependencies #
This repo depends on app-search. 
- app-search is included as a submodule in third-party, because it is hosted on GitHub.
