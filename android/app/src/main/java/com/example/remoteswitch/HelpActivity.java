package com.example.remoteswitch;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class HelpActivity extends AppCompatActivity {
    private TextView helpTextView;
    private Button settingsButton;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // Enable the back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        helpTextView = findViewById(R.id.helpTextView);
        settingsButton = findViewById(R.id.settingsButton);

        // load help_text.txt from assets
        loadHelpText();

        // open app settings when settings button is clicked
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
    }

    private void loadHelpText() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("help_text.txt")));
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
            helpTextView.setText(text.toString());
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            // If failed, load default text
            helpTextView.setText("Failed to load help text.");
        }
    }
}
