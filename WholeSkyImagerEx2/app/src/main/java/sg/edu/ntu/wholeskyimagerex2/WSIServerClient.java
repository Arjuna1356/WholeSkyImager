package sg.edu.ntu.wholeskyimagerex2;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.loopj.android.http.SyncHttpClient;
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

public class WSIServerClient
{
    MainActivity mainActivity;
    private String clientUrl;
    private static SyncHttpClient client = new SyncHttpClient();
    private static int httpStatusCode = 1;
    private JSONArray httpResponseArray;


    //WSIServerClient constructor
    public WSIServerClient(MainActivity mainActivity, String url, String token)
    {
        this.mainActivity = mainActivity;
        clientUrl = url;
        // this enables to bypass the permission issue
        client.setSSLSocketFactory(MySSLSocketFactory.getFixedSocketFactory());

        // authentication header
        client.addHeader("Authorization", "Token f26543bea24e3545a8ef9708dffd7ce5d35127e2");
//        client.addHeader("Authorization", "Token "+ token);
        Log.d(TAG, "SyncHttpClient succesfully created.");
    }

    /**
     * Connection verifier
     *
     * @return connection status
     */
    public boolean isConnected()
    {
        ConnectivityManager connMgr = (ConnectivityManager) mainActivity.getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }

    /**
     * POST Method
     *
     * @param timeStamp
     * @param wahrsisModelNr
     * @return Status Code
     */
    public void httpPOST(String timeStamp, int wahrsisModelNr)
    {
        File imageFile = null;
        File imageFileLow = null;
        File imageFileMed = null;
        File imageFileHigh = null;

        JsonHttpResponseHandler httpHandler = new JsonHttpResponseHandler()
        {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response)
            {
                Log.d(TAG, "Http Status Code: " + statusCode);

                if (response != null)
                {
                    Log.d(TAG, "SyncHttpClient onSuccess onSuccess, got JSON Object: " + response.toString());
                }

                Log.d(TAG, "POST execution finished. Response code: " + statusCode);

                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mainActivity.sendCompleteSuccess();
                    }
                });
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray responseArray)
            {
                Log.d(TAG, "Http Status Code: " + statusCode);

                if (responseArray != null)
                {
                    Log.d(TAG, "SyncHttpClient onSuccess. Received JSON Array. Content: " + responseArray.toString());

                    httpResponseArray = responseArray;
                }

                Log.d(TAG, "POST execution finished. Response code: " + statusCode);

                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mainActivity.sendCompleteSuccess();
                    }
                });
            }

            @Override
            public void onFailure(final int statusCode, Header[] headers, Throwable e, JSONObject response)
            {
                Log.d(TAG, "SyncHttpClient Failure. Status Code: " + statusCode);

                if (response != null)
                {
                    Log.d(TAG, "Response JSON: " + response.toString());
                }

                Log.d(TAG, "POST execution failure. Response code: " + statusCode);

                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mainActivity.sendCompleteFailure(statusCode);
                    }
                });
            }

            @Override
            public void onFailure(final int statusCode, Header[] headers, String response, Throwable e)
            {
                Log.d(TAG, "SyncHttpClient Failure. Status Code: " + statusCode);

                Log.d(TAG, "Response: " + response);

                Log.d(TAG, "POST execution failure. Response code: " + statusCode);

                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mainActivity.sendCompleteFailure(statusCode);
                    }
                });
            }
        };

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mainActivity);
        String photoMode = sharedPrefs.getString("hdrPref", "0");

        //file management
        String filePath = Environment.getExternalStorageDirectory().getPath() + "/WSI/";

        //prepare files to upload (normal image, low, med, high ev photo)
        if(photoMode.equals("0"))
        {
            imageFile = new File(filePath + timeStamp + "_wahrsis" + wahrsisModelNr + ".jpg");
        }
        else if(photoMode.equals("1"))
        {
            imageFile = new File(filePath + timeStamp + "_wahrsis" + wahrsisModelNr + "_DRO" + ".jpg");
        }
        else if(photoMode.equals("2"))
        {
            imageFile = new File(filePath + timeStamp + "_wahrsis" + wahrsisModelNr + "_HDR" + ".jpg");
            imageFileLow = new File(filePath + timeStamp + "_wahrsis" + wahrsisModelNr + "_LOW" + ".jpg");
            imageFileMed = new File(filePath + timeStamp + "_wahrsis" + wahrsisModelNr + "_MED" + ".jpg");
            imageFileHigh = new File(filePath + timeStamp + "_wahrsis" + wahrsisModelNr + "_HIGH" + ".jpg");
        }

        //create object that contains the images
        RequestParams params = new RequestParams();
        try
        {
            params.put("image", imageFile);

        } catch (FileNotFoundException e)
        {
            Log.d(TAG, "Could not find file " + imageFile);
        }

        client.post(clientUrl, params, httpHandler);

        if(photoMode.equals("2"))
        {
            try
            {
                params.remove("image");

                params.put("imageLow", imageFileLow);
                params.put("imageMed", imageFileMed);
                params.put("imageHigh", imageFileHigh);
            }
            catch (FileNotFoundException e)
            {
                Log.d(TAG, "Could not find HDR base exposures.");
            }

            client.post(clientUrl, params, httpHandler);
        }
    }
}