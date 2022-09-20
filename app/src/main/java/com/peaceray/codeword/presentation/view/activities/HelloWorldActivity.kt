package com.peaceray.codeword.presentation.view.activities

import android.content.Intent
import android.os.Bundle
import com.peaceray.codeword.databinding.ActivityHelloWorldBinding
import com.peaceray.codeword.domain.manager.genie.GenieSettingsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HelloWorldActivity: CodeWordActivity() {

    @Inject lateinit var genieSettingsManager: GenieSettingsManager
    private lateinit var binding: ActivityHelloWorldBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelloWorldBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textView.text = "Hello World"

        binding.gameButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        binding.genieCheckBox.setOnCheckedChangeListener { _, b ->
            genieSettingsManager.developerMode = b
        }
    }
}