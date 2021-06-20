package com.example.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Type;
import java.util.HashMap;

public class PermissionsActivity extends AppCompatActivity {

    RecyclerView permissionsRecView;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        permissionsRecView = findViewById(R.id.permissionsRecView);

        Intent previousIntent = getIntent();
        HashMap<String, String> permissionsNotGranted = (HashMap<String, String>) previousIntent
                .getSerializableExtra("permissionsNotGranted");

        PermissionsRecViewAdapter permissionsRecViewAdapter =
                new PermissionsRecViewAdapter(this,
                PermissionsActivity.this,
                permissionsNotGranted);
        LinearLayoutManager permissionsRecViewLayoutManager = new
                LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        permissionsRecView.setLayoutManager(permissionsRecViewLayoutManager);
        permissionsRecView.setAdapter(permissionsRecViewAdapter);


    }
}