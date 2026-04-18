package com.vergepay.wallet.ui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vergepay.core.coins.CoinType;
import com.vergepay.core.coins.Value;
import com.vergepay.core.util.GenericUtils;
import com.vergepay.core.wallet.WalletAccount;
import com.vergepay.wallet.ExchangeRatesProvider.ExchangeRate;
import com.vergepay.wallet.R;
import com.vergepay.wallet.util.WalletUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * @author John L. Jegutanis
 */
public class CoinListItem extends LinearLayout implements Checkable {
    final View view;
    @BindView(R.id.item_icon) ImageView icon;
    @BindView(R.id.item_text) TextView title;
    @BindView(R.id.amount) Amount amount;

    private boolean isChecked = false;
    private CoinType type;

    public CoinListItem(Context context) {
        super(context);

        view = LayoutInflater.from(context).inflate(R.layout.coin_list_row, this, true);
        ButterKnife.bind(this, view);
    }

    public void setAccount(WalletAccount account) {
        this.type = account.getCoinType();
        title.setText(account.getDescriptionOrCoinName());
        icon.setImageResource(WalletUtils.getIconRes(account));
    }

    public void setCoin(CoinType type) {
        this.type = type;
        title.setText(type.getName());
        icon.setImageResource(WalletUtils.getIconRes(type));
    }

    public void setExchangeRate(ExchangeRate exchangeRate) {
        if (exchangeRate != null && type != null) {
            Value localAmount = exchangeRate.rate.convert(type.oneCoin());
            setFiatAmount(localAmount);
        } else {
            amount.setVisibility(View.GONE);
        }
    }

    public void setAmount(Value value) {
        amount.setAmount(GenericUtils.formatCoinValue(value.type, value, true));
        amount.setSymbol(value.type.getSymbol());
        amount.setVisibility(View.VISIBLE);
    }

    private void setFiatAmount(Value value) {
        amount.setAmount(GenericUtils.formatFiatValue(value));
        amount.setSymbol(value.type.getSymbol());
        amount.setVisibility(View.VISIBLE);
    }

    public void setAmountSingleLine(boolean isSingleLine) {
        amount.setSingleLine(isSingleLine);
    }

    @Override
    public void setChecked(boolean checked) {
        isChecked = checked;

        if (isChecked) {
            view.setBackgroundResource(R.drawable.coin_list_item_selected_bg);
        } else {
            view.setBackgroundResource(R.drawable.drawer_item_default_bg);
        }
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }
}
