package com.ibm.tone_analyzer;

import android.app.DialogFragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.ibm.watson.developer_cloud.service.exception.UnauthorizedException;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.ToneAnalyzer;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ToneAnalysis;

import java.net.UnknownHostException;

import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPush;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushException;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPSimplePushNotification;





public class MainActivity extends AppCompatActivity {

    private MFPPush push;
    private MFPPushNotificationListener notificationListener;
    

    private ToneAnalyzer toneAnalyzerService;
    private AnalyzerResultFragment resultFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toneAnalyzerService = new ToneAnalyzer(ToneAnalyzer.VERSION_DATE_2016_05_19,
                getString(R.string.watson_tone_analyzer_username),
                getString(R.string.watson_tone_analyzer_password));

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText entryTextView = (EditText)findViewById(R.id.entryText);
                String entryText = entryTextView.getText().toString();

                if (!entryText.isEmpty()) {
                    // Send the user's input text to the AnalyzeTask.
                    AnalyzeTask analyzeTask = new AnalyzeTask();
                    analyzeTask.execute(entryText);
                } else {
                    Snackbar.make(v, "There's no text to analyze", Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        // Using a retained fragment to hold our result from Analyzer, if it doesn't exist create it.
        resultFragment = (AnalyzerResultFragment)getSupportFragmentManager().findFragmentByTag("result");

        if (resultFragment == null) {
            resultFragment = new AnalyzerResultFragment();
            resultFragment.setRetainInstance(true);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, resultFragment, "result").commit();

            /*
             * On first run through send a single space to ToneAnalyzer. This gives us the full return
             * data structure which we use to dynamically create the UI. This call also allows us to
             * check their credentials at start-up.
             */
            AnalyzeTask analyzeTask = new AnalyzeTask();
            analyzeTask.execute(" ");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Enable the Push Notifications client SDK to listen for push notifications using the predefined notification listener.
        if (push != null) {
            push.listen(notificationListener);
        }

        
        
    }

    @Override
    public void onPause() {
        super.onPause();
        if (push != null) {
            push.hold();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onDestroy() {
        // Have the fragment save its state for recreation on orientation changes.
        resultFragment.saveData();
        super.onDestroy();
    }

    /**
     * Displays an AlertDialogFragment with the given parameters.
     * @param errorTitle Error Title from values/strings.xml.
     * @param errorMessage Error Message either from values/strings.xml or response from server.
     * @param canContinue Whether the application can continue without needing to be rebuilt.
     */
    private void showDialog(int errorTitle, String errorMessage, boolean canContinue) {
        DialogFragment newFragment = AlertDialogFragment.newInstance(errorTitle, errorMessage, canContinue);
        newFragment.show(getFragmentManager(), "dialog");
    }

    /**
     * Enables communication to the Tone Analyzer Service and fetches the service's output.
     */
    private class AnalyzeTask extends AsyncTask<String, Void, ToneAnalysis> {

        @Override
        protected void onPreExecute() {
            EditText entryTextView = (EditText)findViewById(R.id.entryText);
            entryTextView.clearFocus();

            // Hide the keyboard so the user can see the full result.
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(entryTextView.getWindowToken(), 0);

            // Turn on the loading spinner for the brief network load.
            ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected ToneAnalysis doInBackground(String... params) {
            String entryText = params[0];
            ToneAnalysis analysisResult;

            try {
                analysisResult = toneAnalyzerService.getTone(entryText, null).execute();
            } catch (Exception ex) {
                // Here is where we see if the user's credentials are valid or not along with other errors.
                if (ex.getClass().equals(UnauthorizedException.class) ||
                        ex.getClass().equals(IllegalArgumentException.class)) {
                    showDialog(R.string.error_title_invalid_credentials,
                            getString(R.string.error_message_invalid_credentials), false);
                } else if (ex.getCause() != null &&
                        ex.getCause().getClass().equals(UnknownHostException.class)) {
                    showDialog(R.string.error_title_bluemix_connection,
                            getString(R.string.error_message_bluemix_connection), true);
                } else {
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                }
                return null;
            }

            return analysisResult;
        }

        @Override
        protected void onPostExecute(ToneAnalysis result) {
            // Turn off the loading spinner.
            ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
            progressBar.setVisibility(View.GONE);

            // If null do nothing, an alertDialog will be popping up from the catch statement earlier.
            if (result != null) {
                // If not null send the full result from ToneAnalyzer to our UI Builder class.
                AnalyzerResultBuilder resultBuilder = new AnalyzerResultBuilder(MainActivity.this, result);
                LinearLayout resultLayout = (LinearLayout) findViewById(R.id.analysisResultLayout);

                resultLayout.removeAllViews();
                resultLayout.addView(resultBuilder.buildAnalyzerResultView());
            }
        }
    }
}
