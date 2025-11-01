package com.example.lumiapp;

import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class PMDashboardContainer extends AppCompatActivity {

    private LinearLayout[] navItems;
    private int[] icons = {
            R.drawable.ic_home,
            R.drawable.ic_property,
            R.drawable.ic_message,
            R.drawable.ic_profile
    };
    private final String[] labels = {"Home", "Property", "Message", "Profile"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_property_manager_dashboard_container);

        LinearLayout navContainer = findViewById(R.id.navContainer);
        navItems = new LinearLayout[4];

        for (int i = 0; i < 4; i++) {
            navItems[i] = (LinearLayout) navContainer.getChildAt(i);
            ImageView icon = navItems[i].findViewById(R.id.navIcon);
            TextView label = navItems[i].findViewById(R.id.navLabel);

            icon.setImageResource(icons[i]);
            label.setText(labels[i]);

            int index = i;
            navItems[i].setOnClickListener(v -> {
                setActiveItem(index);
                animateTab(navItems[index]);
            });
        }

        // Default
        replaceFragment(new PMDashboardFragment());
        setActiveItem(0);
    }

    private void setActiveItem(int index) {
        for (int i = 0; i < navItems.length; i++) {
            LinearLayout item = navItems[i];
            TextView label = item.findViewById(R.id.navLabel);
            ImageView icon = item.findViewById(R.id.navIcon);

            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) item.getLayoutParams();

            if (i == index) {
                // Active: expand to fit full text
                label.setVisibility(View.VISIBLE);
                lp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
                lp.weight = 0f;
                item.setLayoutParams(lp);
                item.setBackgroundResource(R.drawable.nav_active_bg);
                icon.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary));
            } else {
                // Inactive: take equal share of remaining space
                label.setVisibility(View.GONE);
                lp.width = 0;
                lp.weight = 1f;
                item.setLayoutParams(lp);
                item.setBackgroundResource(R.drawable.nav_inactive_bg);
                icon.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary));
            }
        }

        // swap fragment
        switch (index) {
            case 0: replaceFragment(new PMDashboardFragment()); break;
            case 1: replaceFragment(new PMPropertyFragment()); break;
            case 2: replaceFragment(new PMMessageFragment()); break;
            case 3: replaceFragment(new PMProfileFragment()); break;
        }

        // Ensure layout remeasures after param changes
        findViewById(R.id.navContainer).requestLayout();
    }


    // ✨ Smooth fade + scale animation for switching tabs
    private void animateTab(View view) {
        ScaleAnimation scale = new ScaleAnimation(
                0.9f, 1f, 0.9f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(180);

        AlphaAnimation fade = new AlphaAnimation(0.8f, 1f);
        fade.setDuration(180);

        view.startAnimation(scale);
        view.startAnimation(fade);
    }

    private void replaceFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
