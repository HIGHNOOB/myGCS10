package com.example.mygcs2;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.WindowManager;

public class MyAlertDialog extends AlertDialog.Builder {
    MainActivity mMainActivity;
    protected MyAlertDialog(@NonNull Context context) {
        super(context);
        mMainActivity = (MainActivity) context;
    }

    @Override
    public AlertDialog show() {
        super.show();
        AlertDialog dialog = this.create();
        mMainActivity.hideSystemUI();
        return dialog;
    }

}
