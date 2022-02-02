package com.peaceray.codeword.presentation.view.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.peaceray.codeword.databinding.ActivityHelloWorldBinding
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.HelloWorldContract
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HelloWorldActivity: AppCompatActivity(), HelloWorldContract.View {

    @Inject lateinit var presenter: HelloWorldContract.Presenter

    private lateinit var binding: ActivityHelloWorldBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelloWorldBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // attach the Presenter using Android's LifecycleObserver pattern
        attach(presenter)

        binding.stackButton.setOnClickListener { presenter.onStackButtonClicked() }
        binding.gameButton.setOnClickListener { presenter.onGameButtonClicked() }
    }

    override fun setText(text: String) {
        binding.textView.setText(text)
    }

    override fun stackHelloWorld() {
        val intent = Intent(this, HelloWorldActivity::class.java)
        startActivity(intent)
    }

    override fun stackCodeGame() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}