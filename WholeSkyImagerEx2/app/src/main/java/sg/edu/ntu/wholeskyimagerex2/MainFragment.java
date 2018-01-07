package sg.edu.ntu.wholeskyimagerex2;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainFragment extends Fragment
{
    public MainFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        MainActivity mainActivity = (MainActivity)getActivity();

        // Initialize the main activity's variables to the needed text
        mainActivity.setRunButton((Button) view.findViewById(R.id.buttonRun));
        mainActivity.setStopButton((Button) view.findViewById(R.id.buttonStop));
        mainActivity.setStatusInfo((TextView) view.findViewById(R.id.tvStatusInfo));
        mainActivity.setConnectionStatus((TextView) view.findViewById(R.id.tvConnectionStatus));
        mainActivity.setEventLog((TextView) view.findViewById(R.id.tvEventLog));
        mainActivity.setCameraFrame((FrameLayout) view.findViewById(R.id.camera_preview));

        mainActivity.fragmentInitialize();

        return view;
    }
}
