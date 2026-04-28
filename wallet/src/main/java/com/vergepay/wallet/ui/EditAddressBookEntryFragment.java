package com.vergepay.wallet.ui;

/*
 * Copyright 2011-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.vergepay.core.coins.CoinID;
import com.vergepay.core.coins.CoinType;
import com.vergepay.core.exceptions.AddressMalformedException;
import com.vergepay.core.wallet.AbstractAddress;
import com.vergepay.wallet.AddressBookProvider;
import com.vergepay.wallet.R;
import com.vergepay.wallet.util.UnstoppableDomainsResolver;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public final class EditAddressBookEntryFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = EditAddressBookEntryFragment.class.getName();

    private static final String KEY_COIN_ID = "coin_id";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_SUGGESTED_ADDRESS_LABEL = "suggested_address_label";

    public static void edit(final FragmentManager fm, @Nonnull final AbstractAddress address) {
        edit(fm, address.getType(), address, null);
    }

    public static void edit(final FragmentManager fm, @Nonnull CoinType type,
                            @Nonnull final AbstractAddress address) {
        edit(fm, type, address, null);
    }

    public static void edit(final FragmentManager fm, @Nonnull CoinType type,
                            @Nonnull final AbstractAddress address,
                            @Nullable final String suggestedAddressLabel) {
        edit(fm, type, address.toString(), suggestedAddressLabel);
    }

    public static void edit(final FragmentManager fm, @Nonnull CoinType type,
                            @Nullable final String address,
                            @Nullable final String suggestedAddressLabel) {
        final DialogFragment newFragment =
                EditAddressBookEntryFragment.instance(type, address, suggestedAddressLabel);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private static EditAddressBookEntryFragment instance(@Nonnull CoinType type,
                                                         @Nullable final String address,
                                                         @Nullable final String suggestedAddressLabel) {
        final EditAddressBookEntryFragment fragment = new EditAddressBookEntryFragment();

        final Bundle args = new Bundle();
        args.putString(KEY_COIN_ID, type.getId());
        args.putString(KEY_ADDRESS, address);
        args.putString(KEY_SUGGESTED_ADDRESS_LABEL, suggestedAddressLabel);
        fragment.setArguments(args);

        return fragment;
    }

    private Context context;
    private ContentResolver contentResolver;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.context = context;
        this.contentResolver = context.getContentResolver();
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final CoinType type = CoinID.typeFromId(args.getString(KEY_COIN_ID));
        final String initialAddress = args.getString(KEY_ADDRESS);
        final String suggestedAddressLabel = args.getString(KEY_SUGGESTED_ADDRESS_LABEL);

        final LayoutInflater inflater = LayoutInflater.from(context);
        final String label = resolveLabel(type, initialAddress);
        final boolean isExistingEntry = label != null;

        final DialogBuilder dialog = new DialogBuilder(context);
        final View view = inflater.inflate(R.layout.edit_address_book_entry_dialog, null);
        final EditText viewAddress = view.findViewById(R.id.edit_address_book_entry_address);
        viewAddress.setText(initialAddress);
        final EditText viewLabel = view.findViewById(R.id.edit_address_book_entry_label);
        viewLabel.setText(label != null ? label : suggestedAddressLabel);

        dialog.setView(view);
        dialog.setTitle(isExistingEntry ? R.string.send_edit_contact : R.string.send_add_contact);
        if (isExistingEntry) {
            dialog.setNeutralButton(R.string.button_delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialogInterface, final int which) {
                    deleteEntry(type, initialAddress);
                    dismiss();
                }
            });
        }
        dialog.setPositiveButton(R.string.button_save, null);
        dialog.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialogInterface, final int which) {
                dismiss();
            }
        });

        final AlertDialog alertDialog = dialog.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                saveEntry(type, initialAddress, viewAddress, viewLabel);
                            }
                        });
            }
        });

        return alertDialog;
    }

    @Nullable
    private String resolveLabel(@Nonnull final CoinType type, @Nullable final String address) {
        if (address == null || address.trim().isEmpty()) return null;
        return AddressBookProvider.resolveLabel(context, type, address.trim());
    }

    @Nullable
    private AbstractAddress parseAddress(@Nonnull final CoinType type, @Nonnull final String address)
            throws AddressMalformedException {
        try {
            return type.newAddress(address);
        } catch (AddressMalformedException e) {
            return type.newAddress(com.vergepay.core.util.GenericUtils.fixAddress(address));
        }
    }

    private void saveEntry(@Nonnull final CoinType type, @Nullable final String originalAddress,
                           @Nonnull final EditText addressInput, @Nonnull final EditText labelInput) {
        final String newAddress = addressInput.getText().toString().trim();
        final String newLabel = labelInput.getText().toString().trim();

        addressInput.setError(null);
        labelInput.setError(null);

        if (newLabel.isEmpty()) {
            labelInput.setError(getString(R.string.contact_name_required));
            return;
        }
        if (newAddress.isEmpty()) {
            addressInput.setError(getString(R.string.contact_address_required));
            return;
        }

        final String normalizedAddress;
        if (UnstoppableDomainsResolver.looksLikeDomain(newAddress)) {
            normalizedAddress = newAddress.toLowerCase(Locale.US);
        } else {
            final AbstractAddress parsedAddress;
            try {
                parsedAddress = parseAddress(type, newAddress);
            } catch (AddressMalformedException e) {
                addressInput.setError(getString(R.string.address_error));
                return;
            }
            normalizedAddress = parsedAddress.toString();
        }

        final Uri newUri = buildEntryUri(type, normalizedAddress);
        final ContentValues values = new ContentValues();
        values.put(AddressBookProvider.KEY_LABEL, newLabel);

        if (originalAddress != null && !originalAddress.trim().isEmpty()
                && !originalAddress.trim().equals(normalizedAddress)) {
            deleteEntry(type, originalAddress);
        }

        if (entryExists(newUri)) {
            contentResolver.update(newUri, values, null, null);
        } else {
            contentResolver.insert(newUri, values);
        }

        dismiss();
    }

    private boolean entryExists(@Nonnull final Uri uri) {
        final android.database.Cursor cursor = contentResolver.query(uri, null, null, null, null);
        if (cursor == null) return false;

        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private void deleteEntry(@Nonnull final CoinType type, @Nullable final String address) {
        if (address == null || address.trim().isEmpty()) return;

        contentResolver.delete(buildEntryUri(type, address.trim()), null, null);
    }

    @Nonnull
    private Uri buildEntryUri(@Nonnull final CoinType type, @Nonnull final String address) {
        return AddressBookProvider.contentUri(context.getPackageName(), type)
                .buildUpon()
                .appendPath(address)
                .build();
    }
}
