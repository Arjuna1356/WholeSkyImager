package sg.edu.ntu.wholeskyimager;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

public class RunFragment extends Fragment
{
    private Camera camera;
    private FragmentManager fragmentManager;
    private FrameLayout frameLayout;
    private CaptureFragment captureFragment;
    private Button btn;
    private MainActivity mainActivity;

    public RunFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_run, container, false);

        mainActivity = (MainActivity) getActivity();

        camera = mainActivity.getCamera();
        fragmentManager = mainActivity.getSupportFragmentManager();
        captureFragment = mainActivity.getCaptureFragment();

        btn = (Button) view.findViewById(R.id.buttonRun);

        return view;
    }

    @Override
    public void onStart()
    {
        super.onStart();

        frameLayout = mainActivity.getFrameLayout();

        btn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                camera.openCamera(frameLayout);

                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.runCaptureFrame, captureFragment, "captureFragment");
                fragmentTransaction.commit();
            }
        });
    }
}
