package com.ibm.tone_analyzer;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.ScrollView;

/**
 * Holds and retains the Analyzer Result Data UI from AnalyzerResultBuilder
 */
public class AnalyzerResultFragment extends Fragment {

    private ScrollView retainedLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Check if we've retained the Fragment or are creating it for the first time.
        if (retainedLayout == null) {
            return inflater.inflate(R.layout.analyzer_result_view, container, false);
        } else {
            return retainedLayout;
        }
    }

    /**
     * Called by MainActivity.onDestroy to save the state of the fragment UI.
     */
    public void saveData() {
        retainedLayout = (ScrollView)getView();
    }
}