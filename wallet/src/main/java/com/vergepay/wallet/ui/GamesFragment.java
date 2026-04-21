package com.vergepay.wallet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vergepay.wallet.R;

public class GamesFragment extends Fragment {
    public static GamesFragment newInstance() {
        return new GamesFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_games, container, false);
        WindowInsetsHelper.applyPaddingInsets(view, false, false);

        View tetrisCard = view.findViewById(R.id.game_tetris_card);
        View tetrisButton = view.findViewById(R.id.game_tetris_button);
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getContext() == null) {
                    return;
                }
                Intent intent = new Intent(getContext(), TetrisActivity.class);
                if (getResources().getBoolean(R.bool.wallet_meta_variant)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                startActivity(intent);

                if (getResources().getBoolean(R.bool.wallet_meta_variant)
                        && getActivity() != null
                        && getActivity().getWindow() != null) {
                    final androidx.fragment.app.FragmentActivity activity = getActivity();
                    activity.getWindow().getDecorView().post(new Runnable() {
                        @Override
                        public void run() {
                            activity.moveTaskToBack(false);
                        }
                    });
                }
            }
        };
        tetrisCard.setOnClickListener(listener);
        tetrisButton.setOnClickListener(listener);
        return view;
    }
}
