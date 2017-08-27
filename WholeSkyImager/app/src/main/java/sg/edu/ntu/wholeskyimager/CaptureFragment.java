package sg.edu.ntu.wholeskyimager;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class CaptureFragment extends Fragment
{
    private Camera camera;

    public CaptureFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_capture, container, false);

        MainActivity mainActivity = (MainActivity) getActivity();

        camera = mainActivity.getCamera();

        Button btn = (Button) view.findViewById(R.id.buttonCapture);

        btn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                camera.takePicture();
            }
        });

        return view;
    }
}
