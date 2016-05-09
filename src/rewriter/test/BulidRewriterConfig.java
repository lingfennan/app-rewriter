package rewriter.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import gtisc.app.search.AppSearchUtil;
import gtisc.proto.rewriter.Rewriter.PointOfInterest;
import gtisc.proto.rewriter.Rewriter.RewriterConfig;

public class BulidRewriterConfig {
	PointOfInterest buildInterest(String content, PointOfInterest.POINT_TYPE type) {
		PointOfInterest.Builder poib = PointOfInterest.newBuilder();
		poib.setContent(content);
		poib.setPointType(type);
		return poib.build();
	}
	
	@Test
	public void buildAndSaveConfig() throws IOException {
		RewriterConfig.Builder config = RewriterConfig.newBuilder();
		config.addInterests(buildInterest("android.webkit.WebView.loadUrl", PointOfInterest.POINT_TYPE.CLASS_METHOD_NAME));
		config.addInterests(buildInterest("android.webkit.WebView.loadData", PointOfInterest.POINT_TYPE.CLASS_METHOD_NAME));
		config.addInterests(buildInterest("android.webkit.WebView.loadDataWithBaseURL", PointOfInterest.POINT_TYPE.CLASS_METHOD_NAME));
		File testConfig = new File("data" + File.separator + "webview.config");
		AppSearchUtil.saveMessage(config.build(), testConfig, false);
		assertEquals(true, testConfig.exists());
	}
}
