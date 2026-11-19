package com.eddyizm.tempus.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.ui.view.AuraAppearance;
import com.eddyizm.tempus.ui.view.AuraSettingsCards;

public class ClickablePreferenceCategory extends PreferenceCategory {
    public interface OnClickListener {
        void onClick(ClickablePreferenceCategory category);
    }

    private OnClickListener clickListener;

    public ClickablePreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSelectable(true);
    }

    public void setOnClickListener(OnClickListener listener) {
        this.clickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View surface = holder.findViewById(R.id.aura_settings_category_surface);
        if (surface != null) {
            surface.setBackground(AuraSettingsCards.cardBackground(getContext(), false));
            ImageView icon = (ImageView) holder.findViewById(android.R.id.icon);
            icon.setImageTintList(ColorStateList.valueOf(AuraAppearance.color(getContext(), AuraAppearance.TILES)));
            holder.setDividerAllowedAbove(false);
            holder.setDividerAllowedBelow(false);
        }
        if (!isSelectable()) {
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            holder.itemView.setAlpha(0.5f);
            return;
        }
        holder.itemView.setAlpha(1f);
        holder.itemView.setClickable(true);
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(this);
            }
        });
    }
}
