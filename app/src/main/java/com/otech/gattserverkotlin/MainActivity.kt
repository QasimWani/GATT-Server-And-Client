package com.otech.gattserverkotlin

import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.otech.gattserverkotlin.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
    }

    fun serverClicked(view: View) {
        val i = Intent(this, ServerActivity::class.java)
        startActivity(i)
    }

    fun clientClicked(view: View) {
        val i = Intent(this, ClientActivity::class.java)
        startActivity(i)
    }

    fun onBothClicked(view: View) {
        val i = Intent(this, BothActivity::class.java)
        startActivity(i)
    }
}
