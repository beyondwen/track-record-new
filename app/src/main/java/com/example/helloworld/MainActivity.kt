package com.example.helloworld

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnClick: MaterialButton = findViewById(R.id.btnClick)
        btnClick.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
    }
}
