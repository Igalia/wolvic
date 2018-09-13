package org.mozilla.vrbrowser.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import com.mozilla.speechlibrary.ISpeechRecognitionListener;
import com.mozilla.speechlibrary.MozillaSpeechService;
import com.mozilla.speechlibrary.STTResult;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.WidgetManagerDelegate;
import org.mozilla.vrbrowser.WidgetPlacement;

import static org.mozilla.gecko.GeckoAppShell.getApplicationContext;

public class VoiceSearchWidget extends UIWidget implements WidgetManagerDelegate.PermissionListener, Application.ActivityLifecycleCallbacks {

    private static final String LOGTAG = "VRB";
    private static final int VOICESEARCH_AUDIO_REQUEST_CODE = 7455;
    private static final int ANIMATION_DURATION = 1000;

    private static int MAX_CLIPPING = 10000;
    private static int MAX_DB = 130;
    private static int MIN_DB = 50;

    public interface VoiceSearchDelegate {
        void OnVoiceSearchResult(String transcription, float confidance);
        void OnVoiceSearchCanceled();
        void OnVoiceSearchError();
    }

    private MozillaSpeechService mMozillaSpeechService;
    private VoiceSearchDelegate mDelegate;
    private ImageView mVoiceSearchInput;
    private ImageView mVoiceSearchSearching;
    private Drawable mVoiceInputBackgroundDrawable;
    private ClipDrawable mVoiceInputClipDrawable;
    private int mVoiceInputGravity;
    private TextView mVoiceSearchText1;
    private TextView mVoiceSearchText2;
    private TextView mVoiceSearchText3;
    private RotateAnimation mSearchingAnimation;
    private CloseButtonWidget mCloseButton;
    private boolean mIsSpeechRecognitionRunning = false;
    private boolean mWasSpeechRecognitionRunning = false;

    public VoiceSearchWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public VoiceSearchWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public VoiceSearchWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.voice_search_dialog, this);

        mWidgetManager.addPermissionListener(this);

        mMozillaSpeechService = MozillaSpeechService.getInstance();

        mVoiceSearchText1 = findViewById(R.id.voiceSearchText1);
        mVoiceSearchText2 = findViewById(R.id.voiceSearchText2);
        mVoiceSearchText3 = findViewById(R.id.voiceSearchText3);

        mVoiceInputGravity = 0;
        mVoiceInputBackgroundDrawable = getResources().getDrawable(R.drawable.ic_voice_search_volume_input_black, getContext().getTheme());
        mVoiceInputClipDrawable = new ClipDrawable(getContext().getDrawable(R.drawable.ic_voice_search_volume_input_clip), Gravity.START, ClipDrawable.HORIZONTAL);
        Drawable[] layers = new Drawable[] {mVoiceInputBackgroundDrawable, mVoiceInputClipDrawable };
        mVoiceSearchInput = findViewById(R.id.voiceSearchInput);
        mVoiceSearchInput.setImageDrawable(new LayerDrawable(layers));
        mVoiceInputClipDrawable.setLevel(mVoiceInputGravity);

        mSearchingAnimation = new RotateAnimation(0, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);

        mSearchingAnimation.setInterpolator(new LinearInterpolator());
        mSearchingAnimation.setDuration(ANIMATION_DURATION);
        mSearchingAnimation.setRepeatCount(Animation.INFINITE);
        mVoiceSearchSearching = findViewById(R.id.voiceSearchSearching);

        mCloseButton = createChild(CloseButtonWidget.class, true);
        mCloseButton.setDelegate(new CloseButtonWidget.CloseButtonDelegate() {
            @Override
            public void OnClick() {
                hide();
            }
        });

        ((Application)getApplicationContext()).registerActivityLifecycleCallbacks(this);
    }

    public void setDelegate(VoiceSearchDelegate delegate) {
        mDelegate = delegate;
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removePermissionListener(this);
        mMozillaSpeechService.removeListener(mVoiceSearchListener);
        ((Application)getApplicationContext()).unregisterActivityLifecycleCallbacks(this);

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.voice_search_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.voice_search_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.restart_dialog_world_y);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.restart_dialog_world_z);
    }

    private ISpeechRecognitionListener mVoiceSearchListener = new ISpeechRecognitionListener() {

        public void onSpeechStatusChanged(final MozillaSpeechService.SpeechState aState, final Object aPayload){
            ((Activity)getContext()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (aState) {
                        case DECODING:
                            // Handle when the speech object changes to decoding state
                            Log.d(LOGTAG, "===> DECODING");
                            setDecodingState();
                            break;
                        case MIC_ACTIVITY:
                            // Captures the activity from the microphone
                            Log.d(LOGTAG, "===> MIC_ACTIVITY");
                            double db = (double)aPayload * -1; // the higher the value, quieter the user/environment is
                            db = db == Double.POSITIVE_INFINITY ? MAX_DB : db;
                            int level = (int)(MAX_CLIPPING - (((db - MIN_DB) / (MAX_DB - MIN_DB)) * MAX_CLIPPING));
                            Log.d(LOGTAG, "===> db:      " + db);
                            Log.d(LOGTAG, "===> level    " + level);
                            mVoiceInputClipDrawable.setLevel(level);
                            break;
                        case STT_RESULT:
                            // When the api finished processing and returned a hypothesis
                            Log.d(LOGTAG, "===> STT_RESULT");
                            String transcription = ((STTResult)aPayload).mTranscription;
                            float confidence = ((STTResult)aPayload).mConfidence;
                            if (mDelegate != null)
                                mDelegate.OnVoiceSearchResult(transcription, confidence);
                            hide();
                            break;
                        case START_LISTEN:
                            // Handle when the api successfully opened the microphone and started listening
                            Log.d(LOGTAG, "===> START_LISTEN");
                            break;
                        case NO_VOICE:
                            // Handle when the api didn't detect any voice
                            Log.d(LOGTAG, "===> NO_VOICE");
                            setResultState();
                            break;
                        case CANCELED:
                            // Handle when a cancelation was fully executed
                            Log.d(LOGTAG, "===> CANCELED");
                            setResultState();
                            if (mDelegate != null)
                                mDelegate.OnVoiceSearchCanceled();
                            break;
                        case ERROR:
                            Log.d(LOGTAG, "===> ERROR: " + aPayload.toString());
                            setResultState();
                            // Handle when any error occurred
                            if (mDelegate != null)
                                mDelegate.OnVoiceSearchError();
                            break;
                        default:
                            break;
                    }
                }
            });
        }
    };

    public void startVoiceSearch() {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity)getContext(), new String[]{Manifest.permission.RECORD_AUDIO},
                    VOICESEARCH_AUDIO_REQUEST_CODE);
        } else {
            mMozillaSpeechService.start(getApplicationContext());
            mIsSpeechRecognitionRunning = true;
        }
    }

    public void stopVoiceSearch() {
        try {
            mMozillaSpeechService.cancel();
            mIsSpeechRecognitionRunning = false;

        } catch (Exception e) {
            Log.d(LOGTAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean granted = false;
        if (requestCode == VOICESEARCH_AUDIO_REQUEST_CODE) {
            for (int result: grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                startVoiceSearch();

            } else {
                setPermissionNotGranted();
            }
        }
    }

    @Override
    public void show() {
        super.show();

        setStartListeningState();

        mCloseButton.show();

        mMozillaSpeechService.addListener(mVoiceSearchListener);
        startVoiceSearch();
    }

    @Override
    public void hide() {
        super.hide();

        mCloseButton.hide();

        mMozillaSpeechService.removeListener(mVoiceSearchListener);
        stopVoiceSearch();
    }

    private void setStartListeningState() {
        mVoiceSearchText1.setText(R.string.voice_search_start_1);
        mVoiceSearchText1.setVisibility(View.VISIBLE);
        mVoiceSearchText2.setText(R.string.voice_search_start_2);
        mVoiceSearchText2.setVisibility(View.VISIBLE);
        mVoiceSearchText3.setVisibility(View.VISIBLE);
        mVoiceSearchInput.setVisibility(View.VISIBLE);
        mVoiceSearchSearching.clearAnimation();
        mVoiceSearchSearching.setVisibility(View.INVISIBLE);
    }

    private void setDecodingState() {
        mVoiceSearchText1.setText(R.string.voice_search_decoding);
        mVoiceSearchText1.setVisibility(View.VISIBLE);
        mVoiceSearchText2.setVisibility(View.INVISIBLE);
        mVoiceSearchText3.setVisibility(View.INVISIBLE);
        mVoiceSearchInput.setVisibility(View.INVISIBLE);
        mVoiceSearchSearching.startAnimation(mSearchingAnimation);
        mVoiceSearchSearching.setVisibility(View.VISIBLE);
    }

    private void setResultState() {
        mVoiceSearchText1.setText(R.string.voice_search_error);
        mVoiceSearchText1.setVisibility(View.VISIBLE);
        mVoiceSearchText2.setText(R.string.voice_search_try_again);
        mVoiceSearchText2.setVisibility(View.VISIBLE);
        mVoiceSearchText3.setVisibility(View.VISIBLE);
        mVoiceSearchInput.setVisibility(View.VISIBLE);
        mVoiceSearchSearching.clearAnimation();
        mVoiceSearchSearching.setVisibility(View.INVISIBLE);

        stopVoiceSearch();
        startVoiceSearch();
    }

    private void setPermissionNotGranted() {
        mVoiceSearchText1.setText(R.string.voice_search_permission_1);
        mVoiceSearchText1.setVisibility(View.VISIBLE);
        mVoiceSearchText2.setVisibility(View.INVISIBLE);
        mVoiceSearchText3.setVisibility(View.INVISIBLE);
        mVoiceSearchInput.setVisibility(View.INVISIBLE);
        mVoiceSearchSearching.clearAnimation();
        mVoiceSearchSearching.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (mWasSpeechRecognitionRunning) {
            mMozillaSpeechService.addListener(mVoiceSearchListener);
            startVoiceSearch();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        mWasSpeechRecognitionRunning = mIsSpeechRecognitionRunning;
        if (mIsSpeechRecognitionRunning) {
            mMozillaSpeechService.removeListener(mVoiceSearchListener);
            stopVoiceSearch();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

}
