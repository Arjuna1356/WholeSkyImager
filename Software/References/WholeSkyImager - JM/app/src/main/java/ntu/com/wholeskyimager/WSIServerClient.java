package ntu.com.wholeskyimager;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.MySSLSocketFactory;
import com.loopj.android.http.RequestParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;

import cz.msebera.android.httpclient.Header;

import static android.content.ContentValues.TAG;

/**
 * Created by Julian on 30.11.2016.
 */

public class WSIServerClient {
    Context mContext;
    private String clientUrl;
    private static AsyncHttpClient client = new AsyncHttpClient();
    private static int httpStatusCode = 1;
    private JSONArray httpResponseArray;


    //WSIServerClient constructor
    public WSIServerClient(Context mContext, String url, String token) {
        this.mContext = mContext;
        clientUrl = url;
        // this enables to bypass the permission issue
        client.setSSLSocketFactory(MySSLSocketFactory.getFixedSocketFactory());

        // authentication header
        client.addHeader("Authorization", "Token f26543bea24e3545a8ef9708dffd7ce5d35127e2");
//        client.addHeader("Authorization", "Token "+ token);
        Log.d(TAG, "AsyncHttpClient succesfully created.");
    }

    /** Connection verifier
     *
     * @return connection status
     */
    public boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager) mContext.getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }

    /** POST Method
     *
     * @param timeStamp
     * @param wahrsisModelNr
     * @return Status Code
     */
    public int httpPOST(String timeStamp, int wahrsisModelNr) {
        //file management
        String filePath = Environment.getExternalStorageDirectory().getPath() + "/WSI/";

        //prepare files to upload (normal image, low, med, high ev photo)
        File imageFileLow = new File(filePath+timeStamp+"-wahrsis" + wahrsisModelNr + "-low" + ".jpg");
        File imageFileMed = new File(filePath+timeStamp+"-wahrsis" + wahrsisModelNr + "-med" + ".jpg");
        File imageFileHigh = new File(filePath+timeStamp+"-wahrsis" + wahrsisModelNr + "-high" + ".jpg");

        //create object that contains the images
        RequestParams params = new RequestParams();
        try {
            params.put("imageLow", imageFileLow);
            params.put("imageMed", imageFileMed);
            params.put("imageHigh", imageFileHigh);

        } catch(FileNotFoundException e) {
            Log.d(TAG, "Could not find file " + imageFileLow + " and others (med, high).");
        }

        client.post(clientUrl, params, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                Log.d(TAG, "AsyncHttpClient onSuccess onSuccess, got JSON Object: " + response.toString());
                Log.d(TAG, "Http Status Code: " + statusCode);
                httpStatusCode = statusCode;
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray responseArray) {
                Log.d(TAG, "AsyncHttpClient onSuccess. Received JSON Array. Content: " + responseArray.toString());
                Log.d(TAG, "Http Status Code: " + statusCode);
                httpStatusCode = statusCode;
                httpResponseArray = responseArray;
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable e, JSONObject response) {
                Log.d(TAG, "AsyncHttpClient Failure. Status Code: " + statusCode);
                Log.d(TAG, "Response JSON: " + response.toString());
                httpStatusCode = statusCode;
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String response, Throwable e) {
                Log.d(TAG, "AsyncHttpClient Failure. Status Code: " + statusCode);
                Log.d(TAG, "Response: " + response);
                httpStatusCode = statusCode;
            }
        });

        return httpStatusCode;
    }


    public int httpGET() {
        client.get(clientUrl, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                Log.d(TAG, "onSuccess, got JSON Object");
                Log.d(TAG, "Http Status Code: " + statusCode);
                httpStatusCode = statusCode;
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray responseArray) {
                Log.d(TAG, "AsyncHttpClient onSuccess. Received JSON Array. Content: " + responseArray.toString());
                Log.d(TAG, "Http Status Code: " + statusCode);
                httpStatusCode = statusCode;
                httpResponseArray = responseArray;
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable e, JSONObject response) {
                Log.d(TAG, "AsyncHttpClient Failure. Status Code: " + statusCode);
                Log.d(TAG, "Response JSON: " + response.toString());
                httpStatusCode = statusCode;
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String response, Throwable e) {
                Log.d(TAG, "AsyncHttpClient Failure. Status Code: " + statusCode);
                httpStatusCode = statusCode;
            }
        });
        return httpStatusCode;
    }
}