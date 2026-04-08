package edu.hitsz.aircraftwar;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

import edu.hitsz.aircraftwar.audio.SoundManager;
import edu.hitsz.aircraftwar.data.AppPreferences;
import edu.hitsz.aircraftwar.game.Difficulty;

public class MainActivity extends AppCompatActivity {

    private TextView lastModeTextView;
    private SwitchMaterial soundSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lastModeTextView = findViewById(R.id.text_last_mode);
        soundSwitch = findViewById(R.id.switch_sound);
        Button easyButton = findViewById(R.id.button_easy);
        Button normalButton = findViewById(R.id.button_normal);
        Button hardButton = findViewById(R.id.button_hard);
        Button leaderboardButton = findViewById(R.id.button_leaderboard);

        boolean soundEnabled = AppPreferences.isSoundEnabled(this);
        soundSwitch.setChecked(soundEnabled);
        SoundManager.getInstance(this).setSoundEnabled(soundEnabled);
        soundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPreferences.setSoundEnabled(this, isChecked);
            SoundManager.getInstance(this).setSoundEnabled(isChecked);
        });

        easyButton.setOnClickListener(view -> launchGame(Difficulty.EASY));
        normalButton.setOnClickListener(view -> launchGame(Difficulty.NORMAL));
        hardButton.setOnClickListener(view -> launchGame(Difficulty.HARD));
        leaderboardButton.setOnClickListener(view -> {
            startActivity(new Intent(this, LeaderboardActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Difficulty lastDifficulty = AppPreferences.getLastDifficulty(this);
        lastModeTextView.setText(getString(
                R.string.last_mode_template,
                UiText.getDifficultyLabel(this, lastDifficulty)));
        boolean soundEnabled = AppPreferences.isSoundEnabled(this);
        soundSwitch.setChecked(soundEnabled);
        SoundManager.getInstance(this).setSoundEnabled(soundEnabled);
    }

    private void launchGame(Difficulty difficulty) {
        AppPreferences.setLastDifficulty(this, difficulty);
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name());
        startActivity(intent);
    }
}
