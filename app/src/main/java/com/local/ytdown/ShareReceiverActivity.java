package com.local.ytdown;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class ShareReceiverActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent source = getIntent();
        Intent target = new Intent(this, MainActivity.class);
        target.setAction(Intent.ACTION_SEND);
        target.setType(source.getType());
        target.putExtra(Intent.EXTRA_TEXT, source.getStringExtra(Intent.EXTRA_TEXT));
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(target);
        finish();
    }
}
