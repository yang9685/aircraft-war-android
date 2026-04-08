package edu.hitsz.aircraftwar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import edu.hitsz.aircraftwar.data.ScoreRecord;

public class ScoreRecordAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater inflater;
    private final List<ScoreRecord> records = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public ScoreRecordAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void replaceData(List<ScoreRecord> newRecords) {
        records.clear();
        records.addAll(newRecords);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return records.size();
    }

    @Override
    public ScoreRecord getItem(int position) {
        return records.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_score, parent, false);
            viewHolder = new ViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        ScoreRecord record = getItem(position);
        int rank = position + 1;
        viewHolder.rankText.setText(context.getString(R.string.score_item_rank, rank));
        viewHolder.playerNameText.setText(record.getPlayerName());
        viewHolder.scoreText.setText(context.getString(R.string.score_item_score, record.getScore()));
        viewHolder.modeText.setText(UiText.getDifficultyLabel(context, record.getDifficulty()));
        viewHolder.durationText.setText(context.getString(
                R.string.score_item_duration,
                UiText.formatDuration(record.getDurationSeconds())));
        viewHolder.createdAtText.setText(context.getString(
                R.string.score_item_time,
                dateFormat.format(new Date(record.getCreatedAt()))));

        bindRankStyle(viewHolder, rank);
        return convertView;
    }

    private void bindRankStyle(ViewHolder viewHolder, int rank) {
        int badgeColor;
        int strokeColor;
        if (rank == 1) {
            badgeColor = ContextCompat.getColor(context, R.color.secondary);
            strokeColor = ContextCompat.getColor(context, R.color.secondary);
        } else if (rank == 2) {
            badgeColor = 0xFFB7CAD8;
            strokeColor = 0xFFB7CAD8;
        } else if (rank == 3) {
            badgeColor = 0xFFE0A06B;
            strokeColor = 0xFFE0A06B;
        } else {
            badgeColor = ContextCompat.getColor(context, R.color.primary);
            strokeColor = ContextCompat.getColor(context, R.color.stroke);
        }
        viewHolder.rankText.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
        viewHolder.cardView.setStrokeColor(strokeColor);
    }

    private static final class ViewHolder {
        private final MaterialCardView cardView;
        private final TextView rankText;
        private final TextView playerNameText;
        private final TextView modeText;
        private final TextView scoreText;
        private final TextView durationText;
        private final TextView createdAtText;

        private ViewHolder(View root) {
            cardView = root.findViewById(R.id.card_score);
            rankText = root.findViewById(R.id.text_rank);
            playerNameText = root.findViewById(R.id.text_player_name);
            modeText = root.findViewById(R.id.text_mode);
            scoreText = root.findViewById(R.id.text_score);
            durationText = root.findViewById(R.id.text_duration);
            createdAtText = root.findViewById(R.id.text_created_at);
        }
    }
}
