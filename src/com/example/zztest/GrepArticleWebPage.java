package com.example.zztest;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.example.zztest.downloader.ArticleFile;
import com.example.zztest.downloader.CacheToFile;
import com.example.zztest.downloader.LocalFileCache;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class GrepArticleWebPage {

    private static final String TAG = "GrepArticleWebPage";

    private Handler mHandler;

    private int mRetry = 5;

    private Document mDocument;

    private String mAtricle;

    private String mLrcUrl;

    private String mTranslation;

    private String mMp3webUrl;

    private String mUrl;

    private String mTranslationlink;

    private ArticleFile mArticleInfo;

    int mProgress = 0;

    int mOldProgress = 0;

    private int mRetryDownloadfile = 5;

    public GrepArticleWebPage(Handler handler, ArticleFile item) {
        mHandler = handler;
        mArticleInfo = item;

        mUrl = mArticleInfo.urlstring;

    }

    public String getAtricle() {
        return mAtricle;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getLrcUrl() {
        return mLrcUrl;
    }

    public String getTranstion() {
        return mTranslation;
    }

    public String getMp3webUrl() {
        return mMp3webUrl;
    }

    public void syncGetArticleInfo() {
        mDocument = getWebpageDoc(mUrl);
        if (mDocument != null) {
            parserDocument(mDocument);
        } else {
            if (mRetry > 0) {
                mRetry--;
                syncGetArticleInfo();
            }
        }
    }

    public void getArticleInfo() {
        if (mUrl != null) {
            // run in background thread.
            new Thread(new Runnable() {

                @Override
                public void run() {

                    mDocument = getWebpageDoc(mUrl);

                    parserDocument(mDocument);
                }

            }).start();
        }
    }

    protected void parserDocument(Document doc) {
        if (doc == null) {
            if (mRetry > 0) {
                mRetry--;
                Log.d(TAG, "retry to get the data.");
                getArticleInfo();
            } else {
                Message msg = mHandler.obtainMessage();
                msg.what = Constant.FAILED_UPDATE;
                mHandler.sendMessage(msg);
            }
            return;
        }

        Elements elements = doc.getElementsByTag("body");
        Element mBody = elements.first();
        Element content = mBody.getElementById("content");

        Element menubar = mBody.getElementById("menubar");

        if (menubar != null) {
            Elements links = menubar.select("a[href]");
            for (int i = 0; i < links.size(); i++) {
                Element ele = links.get(i);

                String linkHref = ele.attr("abs:href");
                if (ele.id().equalsIgnoreCase("mp3")) {
                    mMp3webUrl = linkHref;
                } else if (ele.id().equalsIgnoreCase("lrc")) {
                    mLrcUrl = linkHref;
                } else if (ele.id().equalsIgnoreCase("EnPage")) {
                    mTranslationlink = linkHref;
                }
            }
        }

        if (mTranslationlink != null) {
            mRetry = 5;
            getTranslationContent(mTranslationlink);
        }

        Message msg = mHandler.obtainMessage();
        msg.what = Constant.UPDATE_TEXT;
        msg.obj = GrepArticleWebPage.this;
        mHandler.sendMessage(msg);

        if (mLrcUrl != null) {
            downloadlrcFile(mLrcUrl);
        }

        if (mMp3webUrl != null) {
            downloadAudioFile(mMp3webUrl);
        }

        if (content != null) {

            Elements imageElements = content.getElementsByClass("contentImage");

            if (imageElements != null) {
                // <font COLOR="#990030"> </font>
                getImages(imageElements);
            }

            mAtricle = content.html();

            if (mAtricle.indexOf("imagecaption") != -1) {
                int begin = mAtricle.indexOf("imagecaption");
                begin = mAtricle.indexOf(">", begin) + ">".length();

                String fontArticle = mAtricle.substring(0, begin);
                fontArticle = fontArticle + "<font COLOR=\"#999999\">";
                int end = mAtricle.indexOf("<", begin);
                fontArticle = fontArticle + mAtricle.substring(begin, end);
                fontArticle = fontArticle + "</font>";
                fontArticle = fontArticle + mAtricle.substring(end);
                mAtricle = fontArticle;
            }

            String filename = CACHE_PATH + "/" + mArticleInfo.key + ".txt";

            CacheToFile.writeFile(filename, mAtricle.getBytes());

            mArticleInfo.localFileName = filename;
        }

        HashMap<String, ArticleFile> map = LocalFileCache.getInstance().getLocalFileMap();
        if (map == null) {
            map = new HashMap<String, ArticleFile>();
        }
        map.put(mArticleInfo.key, mArticleInfo);
        LocalFileCache.getInstance().setmLocalFileMap(map);
        LocalFileCache.getInstance().writeFile();

        mProgress = 100;
        notifyTheProgress();
    }

    // <DIV class=contentImage><IMG
    // src="/images/201408/33071B56-8F79-4199-AEF6-3BAAD68C69AD_w268_r1_s.jpg"><BR><SPAN
    // class=imagecaption>The Africa Development Bank Community Agriculture Infrastructure
    // Improvement Project reduced transport costs and helped farmers earn higher incomes form
    // agricultural commodities. (Photo: AfDB)</SPAN></DIV>
    private Elements getImages(Elements imageElements) {
        Elements elements = new Elements();
        for (Element imageElement : imageElements) {
            Elements media = imageElement.select("[src]");

            for (Element src : media) {
                if (src.tagName().equals("img")) {
                    String imageurl = src.attr("abs:src");
                    Log.d(TAG, "src = " + imageurl);
                    String filename = CACHE_PATH + getFileName(imageurl);
                    mRetryDownloadfile = 5;
                    downloadFile(imageurl, filename);
                    src.attr("src", filename);
                }
            }

            elements.add(imageElement);

        }
        return elements;
    }

    private void getTranslationContent(final String link) {

        Log.d(TAG, "link = " + link);

        new Thread(new Runnable() {

            @Override
            public void run() {

                if (mTranslationlink.startsWith("http://")) {

                    Log.d(TAG, "mTranslationlink = " + mTranslationlink);

                    Document doc = getWebpageDoc(mTranslationlink);

                    getTranslationContent(doc);
                }
            }

        }).start();

    }

    private void getTranslationContent(Document doc) {
        if (doc == null) {
            if (mRetry > 0) {
                mRetry--;
                getTranslationContent(mTranslationlink);
            }
            return;
        }
        Element ele = doc.getElementById("content");
        mTranslation = ele.html();

        if (mTranslation != null) {

            String filename = CACHE_PATH + "/" + mArticleInfo.key + "_1.txt";
            CacheToFile.writeFile(filename, mTranslation.getBytes());
            mArticleInfo.translation = filename;
        }

        HashMap<String, ArticleFile> map = LocalFileCache.getInstance().getLocalFileMap();
        if (map == null) {
            map = new HashMap<String, ArticleFile>();
        }
        map.put(mArticleInfo.key, mArticleInfo);
        LocalFileCache.getInstance().setmLocalFileMap(map);
        LocalFileCache.getInstance().writeFile();
    }

    private Document getWebpageDoc(String url) {
        Document doc = null;
        try {
            doc = Jsoup.connect(url).timeout(5000).get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return doc;
    }


    public static String CACHE_PATH = Environment.getExternalStorageDirectory().toString() + "/51voa/cache";

    private void downloadlrcFile(String urlstring) {
        String filename = CACHE_PATH + getFileName(urlstring);
        mRetryDownloadfile = 5;
        downloadFile(urlstring, filename);

        mArticleInfo.lrc = filename;
        mArticleInfo.lrcUrl = urlstring;
    }

    private void downloadAudioFile(String urlstring) {
        String filename = CACHE_PATH + getFileName(urlstring);
        mRetryDownloadfile = 5;
        downloadFile(urlstring, filename);

        mArticleInfo.audio = filename;
        mArticleInfo.audioUrl = urlstring;

    }

    private String getFileName(String urlstring) {
        return urlstring.substring(urlstring.lastIndexOf('/'));
    }


    private static final int MAX_BUFFER_SIZE = 1024;

    private void downloadFile(String urlString, String localFilename) {
        mProgress = 0;
        RandomAccessFile file = null;

        InputStream stream = null;

        try {
            URL url = new URL(urlString);
            // Open connection to URL.
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Specify what portion of file to download.
            connection.setRequestProperty("Range", "bytes=" + 0 + "-");

            // Connect to server.
            connection.connect();

            // Make sure response code is in the 200 range.
            if (connection.getResponseCode() / 100 != 2) {
                error(urlString, localFilename);
                return;
            }
            // Check for valid content length.
            int size = connection.getContentLength();
            if (size < 1) {
                error(urlString, localFilename);
                return;
            }

            int downloaded = 0;

            file = new RandomAccessFile(localFilename, "rw");

            long length = file.length();

            downloaded = (int) length;

            file.seek(length);

            if (downloaded >= size) {
                mProgress = 100;
                notifyTheProgress();
                return;
            }

            stream = connection.getInputStream();
            byte buffer[];
            int read = -1;

            notifyTheProgress();

            do {
                if (size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    buffer = new byte[size - downloaded];
                }
                read = stream.read(buffer);
                Log.d(TAG, "read is " + read + buffer.toString());

                if (read == -1 || read == 0) {
                    Log.d(TAG, "read length is -1");
                    mProgress = 100;
                    break;
                }

                file.write(buffer, 0, read);
                downloaded = downloaded + read;

                mProgress = downloaded * 100 / size;

                notifyTheProgress();

            } while (read != -1);

        } catch (IOException e) {
            Log.e(TAG, e.toString());
            error(urlString, localFilename);
            return;
        } finally {
            // Close file.
            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {
                }
            }

            // Close connection to server.
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                }
            }
        }
    }


    private void notifyTheProgress() {

        if (mProgress == 100) {
            Log.d(TAG, "mArticleInfo = " + mArticleInfo.toString() + ", notifyTheProgress = 100%");
            Message msg = mHandler.obtainMessage(Constant.DOWNLOAD_COMPLETED);
            msg.obj = mArticleInfo;
            mHandler.sendMessage(msg);

            mArticleInfo.progress = "[已下载]";
        } else {

            if (mProgress == 0 || mProgress - mOldProgress > 3) {
                mOldProgress = mProgress;
                Message msg = mHandler.obtainMessage(Constant.DOWNLOAD_PROGRESS);
                msg.obj = mArticleInfo;
                mHandler.sendMessage(msg);
                mArticleInfo.progress = "[下载中" + mProgress + "%]";

                Log.d(TAG, "mArticleInfo = " + mArticleInfo.toString() + ", notifyTheProgress = " + mProgress);
            }
        }
    }

    private void error(String urlString, String localFilename) {
        if (mRetryDownloadfile != 0) {
            mRetryDownloadfile--;
            downloadFile(urlString, localFilename);
        }
    }

}
