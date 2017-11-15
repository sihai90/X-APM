package github.tornaco.xposedmoduletest.ui.activity;

import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.andrognito.patternlockview.PatternLockView;
import com.andrognito.patternlockview.utils.PatternLockUtils;

import org.newstand.logger.Logger;

import java.util.List;

import github.tornaco.xposedmoduletest.R;
import github.tornaco.xposedmoduletest.camera.CameraManager;
import github.tornaco.xposedmoduletest.compat.fingerprint.FingerprintManagerCompat;
import github.tornaco.xposedmoduletest.provider.KeyguardStorage;
import github.tornaco.xposedmoduletest.provider.XSettings;
import github.tornaco.xposedmoduletest.util.PatternLockViewListenerAdapter;
import github.tornaco.xposedmoduletest.util.ViewAnimatorUtil;

/**
 * Created by guohao4 on 2017/11/15.
 * Email: Tornaco@163.com
 */

@SuppressLint("Registered")
public class NeedLockActivity extends BaseActivity {

    private LockView mLockView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isLockNeeded()) {
            mLockView = new LockView();
            mLockView.attach(NeedLockActivity.this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLockView != null) {
            mLockView.detach(false);
        }
    }

    protected boolean isLockNeeded() {
        return true;
    }

    private class LockView {

        private Activity activity;

        private CancellationSignal mCancellationSignal;

        private ScreenBroadcastReceiver mScreenBroadcastReceiver;

        private AsyncTask mCheckTask;

        private boolean mTakePhoto;

        private View mRootView;

        @SuppressLint("InflateParams")
        public void attach(Activity activity) {
            this.activity = activity;

            detach(false);

            readSettings();

            mRootView = LayoutInflater.from(activity)
                    .inflate(R.layout.verify_displayer, null, false);

            setupLabel();
            setupCamera();
            setupFP();
            setupLockView();

            if (isKeyguard()) {
                this.mScreenBroadcastReceiver = new ScreenBroadcastReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(Intent.ACTION_USER_PRESENT);
                registerReceiver(this.mScreenBroadcastReceiver, intentFilter);
                return;
            }

            WindowManager wm = getWindowManager();

            ViewGroup.LayoutParams params
                    = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            wm.addView(mRootView, params);

//            mRootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
//                @Override
//                public void onViewAttachedToWindow(View v) {
//                    ViewAnimatorUtil.circularShow(mRootView, new Runnable() {
//                        @Override
//                        public void run() {
//                        }
//                    });
//                }
//
//                @Override
//                public void onViewDetachedFromWindow(View v) {
//                    mRootView.removeOnAttachStateChangeListener(this);
//                }
//            });
        }

        public void detach(boolean withAnim) {
            try {
                CameraManager.get().closeCamera();

                if (mScreenBroadcastReceiver != null) {
                    unregisterReceiver(mScreenBroadcastReceiver);
                }

                cancelCheckTask();
            } catch (Throwable e) {
                Logger.e("Error onDestroy: " + e);
            }

            if (mRootView != null && mRootView.isAttachedToWindow()) {

                if (withAnim) {
                    ViewAnimatorUtil.circularHide(mRootView, new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            WindowManager wm = getWindowManager();
                            wm.removeView(mRootView);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                } else {
                    WindowManager wm = getWindowManager();
                    wm.removeView(mRootView);
                }
            }
        }


        private void readSettings() {
            this.mTakePhoto = XSettings.get().takenPhotoEnabled(activity);
        }

        private void setupLabel() {
            TextView textView = mRootView.findViewById(R.id.label);
            textView.setText(getString(R.string.input_password,
                    getString(R.string.app_name)));
        }

        private void setupCamera() {
            // Setup camera preview.
            View softwareCameraPreview = mRootView.findViewById(R.id.surface);
            if (softwareCameraPreview != null)
                softwareCameraPreview.setVisibility(mTakePhoto ? View.VISIBLE : View.GONE);
        }

        private void setupFP() {
            cancelFP();
            if (XSettings.get().fpEnabled(activity)) {
                mCancellationSignal = setupFingerPrint(
                        new FingerprintManagerCompat.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(
                                    FingerprintManagerCompat.AuthenticationResult result) {
                                super.onAuthenticationSucceeded(result);
                                Logger.d("onAuthenticationSucceeded:" + result);
                                onPass();
                                vibrate();
                            }

                            @Override
                            public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                                super.onAuthenticationHelp(helpMsgId, helpString);
                                Logger.i("onAuthenticationHelp:" + helpString);
                                // mLabelView.setText(helpString);
                            }

                            @Override
                            public void onAuthenticationFailed() {
                                super.onAuthenticationFailed();
                                Logger.d("onAuthenticationFailed");
                                // vibrate();
                            }

                            @Override
                            public void onAuthenticationError(int errMsgId, CharSequence errString) {
                                super.onAuthenticationError(errMsgId, errString);
                                Logger.d("onAuthenticationError:" + errString);
                                // mLabelView.setText(errString);
                                // vibrate();
                            }
                        });
            }
        }

        private void vibrate() {
            Vibrator vibrator = (Vibrator) activity.getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(200);
            }
        }

        private boolean isKeyguard() {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (keyguardManager != null && keyguardManager.inKeyguardRestrictedInputMode()) {
                Logger.d("in keyguard true");
                return true;
            }
            Logger.d("in keyguard false");
            return false;
        }


        private void onPass() {
            cancelFP();
            detach(true);
        }

        private void cancelFP() {
            if (mCancellationSignal != null && !mCancellationSignal.isCanceled()) {
                mCancellationSignal.cancel();
                mCancellationSignal = null;
            }
        }

        private CancellationSignal setupFingerPrint(FingerprintManagerCompat.AuthenticationCallback callback) {
            if (ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.USE_FINGERPRINT)
                    != PackageManager.PERMISSION_GRANTED) {
                Logger.w("FP Permission is missing...");
                return null;
            }
            if (!FingerprintManagerCompat.from(activity.getApplicationContext()).isHardwareDetected()) {
                Logger.w("FP HW is missing...");
                return null;
            }
            CancellationSignal cancellationSignal = new CancellationSignal();
            FingerprintManagerCompat.from(activity.getApplicationContext())
                    .authenticate(null, 0, cancellationSignal, callback, null);
            Logger.i("FP authenticate");
            return cancellationSignal;
        }

        private void setupLockView() {
            setupPatternLockView();
        }

        private void setupPatternLockView() {
            final PatternLockView patternLockView = mRootView.findViewById(R.id.pattern_lock_view);
            patternLockView.addPatternLockListener(new PatternLockViewListenerAdapter() {
                @Override
                public void onComplete(List<PatternLockView.Dot> pattern) {
                    cancelCheckTask();
                    // Check pattern.
                    mCheckTask = KeyguardStorage.checkPatternAsync(getApplicationContext(),
                            PatternLockUtils.patternToString(patternLockView, pattern),
                            new KeyguardStorage.PatternCheckListener() {
                                @Override
                                public void onMatch() {
                                    patternLockView.setViewMode(PatternLockView.PatternViewMode.CORRECT);
                                    onPass();
                                }

                                @Override
                                public void onMisMatch() {
                                    patternLockView.setViewMode(PatternLockView.PatternViewMode.WRONG);
                                    patternLockView.clearPattern();
                                }
                            });
                }

                @Override
                public void onCleared() {

                }
            });
            patternLockView.setEnableHapticFeedback(true);
        }

        private void cancelCheckTask() {
            if (mCheckTask != null) {
                mCheckTask.cancel(true);
            }
        }


        private final class ScreenBroadcastReceiver extends BroadcastReceiver {
            private ScreenBroadcastReceiver() {
            }

            public void onReceive(Context context, Intent intent) {
                String strAction = null;
                if (intent != null) {
                    strAction = intent.getAction();
                }
                if (Intent.ACTION_USER_PRESENT.equals(strAction)) {
                    setupFP();
                }
            }
        }
    }


}