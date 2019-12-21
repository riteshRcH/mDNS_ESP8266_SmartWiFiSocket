package in.co.unifytech.socket;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class ExternalSmartWiFiSocketActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        startActivity(new Intent(ExternalSmartWiFiSocketActivity.this, SmartWiFiSocketActivity.class));
        finish();
    }
}
