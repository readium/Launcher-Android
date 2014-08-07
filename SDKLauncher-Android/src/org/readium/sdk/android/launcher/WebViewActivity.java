/*
 * WebViewActivity.java
 * SDKLauncher-Android
 *
 * Created by Yonathan Teitelbaum (Mantano) on 2013-07-10.
 */
//  Copyright (c) 2014 Readium Foundation and/or its licensees. All rights reserved.
//  Redistribution and use in source and binary forms, with or without modification, 
//  are permitted provided that the following conditions are met:
//  1. Redistributions of source code must retain the above copyright notice, this 
//  list of conditions and the following disclaimer.
//  2. Redistributions in binary form must reproduce the above copyright notice, 
//  this list of conditions and the following disclaimer in the documentation and/or 
//  other materials provided with the distribution.
//  3. Neither the name of the organization nor the names of its contributors may be 
//  used to endorse or promote products derived from this software without specific 
//  prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
//  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
//  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
//  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
//  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
//  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
//  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
//  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
//  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
//  OF THE POSSIBILITY OF SUCH DAMAGE

package org.readium.sdk.android.launcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.readium.sdk.android.Container;
import org.readium.sdk.android.ManifestItem;
import org.readium.sdk.android.Package;
import org.readium.sdk.android.SpineItem;
import org.readium.sdk.android.launcher.model.BookmarkDatabase;
import org.readium.sdk.android.launcher.model.OpenPageRequest;
import org.readium.sdk.android.launcher.model.Page;
import org.readium.sdk.android.launcher.model.PaginationInfo;
import org.readium.sdk.android.launcher.model.ReadiumJSApi;
import org.readium.sdk.android.launcher.model.ViewerSettings;
import org.readium.sdk.android.launcher.util.EpubServer;
import org.readium.sdk.android.launcher.util.HTMLUtil;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

public class WebViewActivity extends FragmentActivity implements ViewerSettingsDialog.OnViewerSettingsChange {

	private static final String TAG = "WebViewActivity";
	private static final String ASSET_PREFIX = "file:///android_asset/readium-shared-js/";
	private static final String READER_SKELETON = "file:///android_asset/readium-shared-js/reader.html";
	private static final String READER_DENOTE="readium-shared-js/reader.html";
	
	private WebView mWebview;
	private Container mContainer;
	private Package mPackage;
	private OpenPageRequest mOpenPageRequestData;
	private TextView mPageInfo;
	private ViewerSettings mViewerSettings;
	private ReadiumJSApi mReadiumJSApi;
	private EpubServer mServer;
	
	private boolean mIsMoAvailable;
	private boolean mIsMoPlaying;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_web_view);
		
		mWebview = (WebView) findViewById(R.id.webview);
		mPageInfo = (TextView) findViewById(R.id.page_info);
		initWebView();

        Intent intent = getIntent();
        if (intent.getFlags() == Intent.FLAG_ACTIVITY_NEW_TASK) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mContainer = ContainerHolder.getInstance().get(extras.getLong(Constants.CONTAINER_ID));
                if (mContainer == null) {
                	finish();
                	return;
                }
                mPackage = mContainer.getDefaultPackage();
                try {
					mOpenPageRequestData = OpenPageRequest.fromJSON(extras.getString(Constants.OPEN_PAGE_REQUEST_DATA));
				} catch (JSONException e) {
					Log.e(TAG, "Constants.OPEN_PAGE_REQUEST_DATA must be a valid JSON object: "+e.getMessage(), e);
				}
            }
        }
        new AsyncTask<Void, Integer, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
        		//mServer = new EpubServer(EpubServer.HTTP_HOST, EpubServer.HTTP_PORT, mPackage, false);
				mServer = new EpubServer(WebViewActivity.this, EpubServer.HTTP_HOST, EpubServer.HTTP_PORT, mPackage, false);
    			mServer.startServer();
    			
    			publishProgress(0);
    			return null;
        	}
			
			protected void onProgressUpdate(Integer... values) {
				// Load the page skeleton
		        mWebview.loadUrl(getUrl(READER_SKELETON,Constants.HOST));
		        mViewerSettings = new ViewerSettings(false, 100, 20);
		        mReadiumJSApi = new ReadiumJSApi(new ReadiumJSApi.JSLoader() {
					
					@Override
					public void loadJS(String javascript) {
						mWebview.loadUrl(javascript);
					}
				});
			}
        }.execute();
        
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mServer.stop();
        mWebview.loadUrl(READER_SKELETON);
		((ViewGroup) mWebview.getParent()).removeView(mWebview);
		mWebview.removeAllViews();
		mWebview.clearCache(true);
		mWebview.clearHistory();
		mWebview.destroy();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			mWebview.onPause();
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			mWebview.onResume();
		}
	}
	

	@SuppressLint({ "SetJavaScriptEnabled", "NewApi" })
	private void initWebView() {
		mWebview.getSettings().setJavaScriptEnabled(true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			mWebview.getSettings().setAllowUniversalAccessFromFileURLs(true);
		}
		mWebview.setWebViewClient(new EpubWebViewClient());
		mWebview.setWebChromeClient(new EpubWebChromeClient());

		mWebview.addJavascriptInterface(new EpubInterface(), "LauncherUI");
	}

	public boolean onMenuItemSelected(int featureId, MenuItem item) {
	    int itemId = item.getItemId();
	    switch (itemId) {
	    case R.id.add_bookmark:
			Log.d(TAG, "Add a bookmark");
			mReadiumJSApi.bookmarkCurrentPage();
			return true;
	    case R.id.settings:
			Log.d(TAG, "Show settings");
			showSettings();
			return true;
	    case R.id.mo_previous:
	    	mReadiumJSApi.previousMediaOverlay();
	    	return true;
		case R.id.mo_play:
			mReadiumJSApi.toggleMediaOverlay();
			return true;
		case R.id.mo_pause:
			mReadiumJSApi.toggleMediaOverlay();
			return true;
		case R.id.mo_next:
			mReadiumJSApi.nextMediaOverlay();
			return true;
	    }
	    return false;
	}

	public void onClick(View v) {
		if (v.getId() == R.id.left) {
			mReadiumJSApi.openPageLeft();
		} else if (v.getId() == R.id.right) {
			mReadiumJSApi.openPageRight();
		}
	}
	
	private void showSettings() {
		FragmentManager fm = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fm.beginTransaction();
		DialogFragment dialog = new ViewerSettingsDialog(this, mViewerSettings);
        dialog.show(fm, "dialog");
		fragmentTransaction.commit();
	}

	@Override
	public void onViewerSettingsChange(ViewerSettings viewerSettings) {
		updateSettings(viewerSettings);
	}

	private void updateSettings(ViewerSettings viewerSettings) {
		mViewerSettings = viewerSettings;
		mReadiumJSApi.updateSettings(viewerSettings);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.web_view, menu);
		
		MenuItem mo_previous = menu.findItem(R.id.mo_previous);
		MenuItem mo_next = menu.findItem(R.id.mo_next);
		MenuItem mo_play = menu.findItem(R.id.mo_play);
		MenuItem mo_pause = menu.findItem(R.id.mo_pause);
		
		//show menu only when its reasonable
		
		mo_previous.setVisible(mIsMoAvailable);
		mo_next.setVisible(mIsMoAvailable);
		
		if(mIsMoAvailable){
			mo_play.setVisible(!mIsMoPlaying);
			mo_pause.setVisible(mIsMoPlaying);
		}
		
		return true;
	}

    public final class EpubWebViewClient extends WebViewClient {

        private static final String HTTP = "http";
		private static final String UTF_8 = "utf-8";
        private boolean skeletonPageLoaded = false;

		@Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
        	Log.d(TAG, "onPageStarted: "+url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
        	Log.d(TAG, "onPageFinished: "+url);
        	if (!skeletonPageLoaded && url.contains(READER_DENOTE)) {
        		skeletonPageLoaded = true;
        		Log.d(TAG, "openPageRequestData: "+mOpenPageRequestData);
        		mReadiumJSApi.openBook(mPackage, mViewerSettings, mOpenPageRequestData);
        	}
        }

//        @Override
//        public void onLoadResource(WebView view, String url) {
//			Log.d(TAG, "onLoadResource: " + url);
//        	String cleanedUrl = cleanResourceUrl(url);
//        	byte[] data = mPackage.getContent(cleanedUrl);
//            if (data != null && data.length > 0) {
//            	ManifestItem item = mPackage.getManifestItem(cleanedUrl);
//            	String mimetype = (item != null) ? item.getMediaType() : null;
//            	mWebview.loadData(new String(data), mimetype, UTF_8);
//            }
//        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Log.d(TAG, "shouldOverrideUrlLoading: " + url);
    		return false;
        }

//        @Override
//        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
//			Log.d(TAG, "shouldInterceptRequest: " + url);
//			Uri uri = Uri.parse(url);
//            if (uri.getScheme().equals("file")) {
//                String cleanedUrl = cleanResourceUrl(url);
//                Log.d(TAG, url+" => "+cleanedUrl);
//                InputStream data = mPackage.getInputStream(cleanedUrl);
//                ManifestItem item = mPackage.getManifestItem(cleanedUrl);
//                if (item != null && item.isHtml()) {
//                    byte[] binary;
//                    try {
//                        binary = new byte[data.available()];
//                        data.read(binary);
//                        data.close();
//                        data = new ByteArrayInputStream(HTMLUtil.htmlByReplacingMediaURLsInHTML(new String(binary),
//                                cleanedUrl, "PackageUUID").getBytes());
//                    } catch (IOException e) {
//                        Log.e(TAG, ""+e.getMessage(), e);
//                    }
//                }
//                String mimetype = (item != null) ? item.getMediaType() : null;
//                return new WebResourceResponse(mimetype, UTF_8, data);
//            } else if(uri.getScheme().equals("http")){
//            	return super.shouldInterceptRequest(view, url);
//            }
//
//            try {
//                URLConnection c = new URL(url).openConnection();
//                return new WebResourceResponse(null, UTF_8, c.getInputStream());
//            } catch (MalformedURLException e) {
//                Log.e(TAG, ""+e.getMessage(), e);
//            } catch (IOException e) {
//                Log.e(TAG, ""+e.getMessage(), e);
//            }
//            return new WebResourceResponse(null, UTF_8, new ByteArrayInputStream("".getBytes()));
//        }
    }
    
    private String cleanResourceUrl(String url) {
        String cleanUrl = url.replace(ASSET_PREFIX, "");
        cleanUrl = (cleanUrl.startsWith(mPackage.getBasePath())) ? cleanUrl.replaceFirst(mPackage.getBasePath(), "") : cleanUrl;
        int indexOfSharp = cleanUrl.indexOf('#');
        if (indexOfSharp >= 0) {
            cleanUrl = cleanUrl.substring(0, indexOfSharp);
        }
        return cleanUrl;
    }

	public class EpubWebChromeClient extends WebChromeClient implements
			MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
		@Override
		public void onShowCustomView(View view, CustomViewCallback callback) {
			Log.d(TAG, "here in on ShowCustomView: " + view);
			super.onShowCustomView(view, callback);
			if (view instanceof FrameLayout) {
				FrameLayout frame = (FrameLayout) view;
				Log.d(TAG, "frame.getFocusedChild(): " + frame.getFocusedChild());
				if (frame.getFocusedChild() instanceof VideoView) {
					VideoView video = (VideoView) frame.getFocusedChild();
					// frame.removeView(video);
					// a.setContentView(video);
					video.setOnCompletionListener(this);
					video.setOnErrorListener(this);
					video.start();
				}
			}
		}

		public void onCompletion(MediaPlayer mp) {
			Log.d(TAG, "Video completed");

			// a.setContentView(R.layout.main);
			// WebView wb = (WebView) a.findViewById(R.id.webview);
			// a.initWebView();
		}

		@Override
		public boolean onError(MediaPlayer mp, int what, int extra) {
			Log.d(TAG, "MediaPlayer onError: " + what + ", " + extra);
			return false;
		}
	}
    
	public class EpubInterface {
		
		@JavascriptInterface
		public void onPaginationChanged(String currentPagesInfo) {
			Log.d(TAG, "onPaginationChanged: "+currentPagesInfo);
			try {
				PaginationInfo paginationInfo = PaginationInfo.fromJson(currentPagesInfo);
				List<Page> openPages = paginationInfo.getOpenPages();
				if (!openPages.isEmpty()) {
					final Page page = openPages.get(0);
					runOnUiThread(new Runnable() {
						public void run() {
							mPageInfo.setText(getString(R.string.page_x_of_y,
									page.getSpineItemPageIndex() + 1,
									page.getSpineItemPageCount()));
							SpineItem spineItem = mPackage.getSpineItem(page.getIdref());
							boolean isFixedLayout = spineItem.isFixedLayout();
				            mWebview.getSettings().setBuiltInZoomControls(isFixedLayout);
				            mWebview.getSettings().setDisplayZoomControls(false);
						}
					});
				}
			} catch (JSONException e) {
				Log.e(TAG, ""+e.getMessage(), e);
			}
		}
		
		@JavascriptInterface
		public void onSettingsApplied() {
			Log.d(TAG, "onSettingsApplied");
		}
		
		@JavascriptInterface
		public void onReaderInitialized() {
			Log.d(TAG, "onReaderInitialized");
		}
		
		@JavascriptInterface
		public void onContentLoaded() {
			Log.d(TAG, "onContentLoaded");
		}
		
		@JavascriptInterface
		public void onPageLoaded() {
			Log.d(TAG, "onPageLoaded");
		}
		
		@JavascriptInterface
		public void onIsMediaOverlayAvailable(String available){
			Log.d(TAG, "onIsMediaOverlayAvailable:" + available);
			mIsMoAvailable = available.equals("true");
            
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    invalidateOptionsMenu();
                }
            });
		}
		
		@JavascriptInterface
		public void onMediaOverlayStatusChanged(String status) {
			Log.d(TAG, "onMediaOverlayStatusChanged:" + status);
			//this should be real json parsing if there will be more data that needs to be extracted
			
			if(status.indexOf("isPlaying") > -1){
				mIsMoPlaying = status.indexOf("\"isPlaying\":true") > -1;
			}
            
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    invalidateOptionsMenu();
                }
            });
		}
//		
//		@JavascriptInterface
//		public void onMediaOverlayTTSSpeak() {
//			Log.d(TAG, "onMediaOverlayTTSSpeak");
//		}
//		
//		@JavascriptInterface
//		public void onMediaOverlayTTSStop() {
//			Log.d(TAG, "onMediaOverlayTTSStop");
//		}
		
		@JavascriptInterface
		public void getBookmarkData(final String bookmarkData) {
			AlertDialog.Builder builder = new AlertDialog.Builder(WebViewActivity.this).
					setTitle(R.string.add_bookmark);
	        
	        final EditText editText = new EditText(WebViewActivity.this);
	        editText.setId(android.R.id.edit);
	        editText.setHint(R.string.title);
	        builder.setView(editText);
	        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which == DialogInterface.BUTTON_POSITIVE) {
						String title = editText.getText().toString();
						try {
							JSONObject bookmarkJson = new JSONObject(bookmarkData);
							BookmarkDatabase.getInstance().addBookmark(mContainer.getName(), title,
									bookmarkJson.getString("idref"), bookmarkJson.getString("contentCFI"));
						} catch (JSONException e) {
							Log.e(TAG, ""+e.getMessage(), e);
						}
					}
				}
			});
	        builder.setNegativeButton(android.R.string.cancel, null);
	        builder.show();
		}
	}
	
	private String getUrl(String path,String ipAddr){
		if (path.startsWith(Constants.FILE_PROC)){
			return Constants.HTTP_PROC+ipAddr+":"+Constants.PORT+
					path.replace(Constants.FILE_PROC,"");
		}else if (path.startsWith("/")){
			return Constants.HTTP_PROC+ipAddr+":"+Constants.PORT+path;
		}
		return "";
	}
}
