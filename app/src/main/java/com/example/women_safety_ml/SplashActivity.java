package com.example.women_safety_ml;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    // Duration before navigating to next screen (3 seconds)
    private static final long SPLASH_DURATION = 3000;

    private ImageView ivShieldIcon;
    private TextView tvAppName;
    private TextView tvTagline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide status bar for full screen splash (modern API)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        setContentView(R.layout.activity_splash);

        // Initialize views
        ivShieldIcon = findViewById(R.id.ivShieldIcon);
        tvAppName    = findViewById(R.id.tvAppName);
        tvTagline    = findViewById(R.id.tvTagline);

        // Start entrance animations
        startAnimations();

        // Navigate to MainActivity after splash duration
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                navigateToMain();
            }
        }, SPLASH_DURATION);
    }

    /**
     * Runs a staggered fade+scale animation on the icon, title, and tagline.
     */
    private void startAnimations() {

        // --- Shield icon: scale up from 0 + fade in ---
        ivShieldIcon.setAlpha(0f);
        ivShieldIcon.setScaleX(0.4f);
        ivShieldIcon.setScaleY(0.4f);

        ObjectAnimator iconAlpha   = ObjectAnimator.ofFloat(ivShieldIcon, "alpha",   0f, 1f);
        ObjectAnimator iconScaleX  = ObjectAnimator.ofFloat(ivShieldIcon, "scaleX",  0.4f, 1f);
        ObjectAnimator iconScaleY  = ObjectAnimator.ofFloat(ivShieldIcon, "scaleY",  0.4f, 1f);

        AnimatorSet iconSet = new AnimatorSet();
        iconSet.playTogether(iconAlpha, iconScaleX, iconScaleY);
        iconSet.setDuration(700);
        iconSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // --- App name: slide up + fade in ---
        tvAppName.setAlpha(0f);
        tvAppName.setTranslationY(40f);

        ObjectAnimator nameAlpha = ObjectAnimator.ofFloat(tvAppName, "alpha",        0f, 1f);
        ObjectAnimator nameSlide = ObjectAnimator.ofFloat(tvAppName, "translationY", 40f, 0f);

        AnimatorSet nameSet = new AnimatorSet();
        nameSet.playTogether(nameAlpha, nameSlide);
        nameSet.setDuration(600);
        nameSet.setStartDelay(400);
        nameSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // --- Tagline: slide up + fade in ---
        tvTagline.setAlpha(0f);
        tvTagline.setTranslationY(30f);

        ObjectAnimator tagAlpha = ObjectAnimator.ofFloat(tvTagline, "alpha",        0f, 1f);
        ObjectAnimator tagSlide = ObjectAnimator.ofFloat(tvTagline, "translationY", 30f, 0f);

        AnimatorSet tagSet = new AnimatorSet();
        tagSet.playTogether(tagAlpha, tagSlide);
        tagSet.setDuration(600);
        tagSet.setStartDelay(650);
        tagSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // --- Play all together ---
        AnimatorSet fullSet = new AnimatorSet();
        fullSet.playTogether(iconSet, nameSet, tagSet);
        fullSet.start();
    }

    /**
     * Navigate to MainActivity and finish this activity so the user
     * cannot press Back to return to the splash screen.
     */
    private void navigateToMain() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}