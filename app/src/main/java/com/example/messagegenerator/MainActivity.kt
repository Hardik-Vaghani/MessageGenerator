package com.example.messagegenerator

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.messagegenerator.generator.Message
import com.example.messagegenerator.generator.companyMessages
import com.example.messagegenerator.generator.generateMessagesInChunks
import com.example.messagegenerator.generator.generateMobileNumbersForMultipleCountries
import com.example.messagegenerator.generator.generateUsernamesWithCompanies
import com.example.messagegenerator.generator.hardikMessages
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.messagegenerator.databinding.ActivityMainBinding
import com.example.messagegenerator.generator.MessageGenerator
import com.example.messagegenerator.generator.insertMessages
import com.example.messagegenerator.util.SmsDefaultAppHelper.isDefaultSmsApp
import com.example.messagegenerator.util.SmsDefaultAppHelper.registerResultLauncher
import com.example.messagegenerator.util.SmsDefaultAppHelper.requestDefaultSmsApp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.buffer
import okio.sink

class MainActivity : AppCompatActivity() {

    var messagesList: MutableList<Message> = mutableListOf()

    lateinit var binding: ActivityMainBinding

    val generator = MessageGenerator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        registerResultLauncher(this,) { isSetAsDefault ->
            if(!isSetAsDefault) {
                requestDefaultSmsApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val listCompany = generateUsernamesWithCompanies()
        val companyData = Pair(listCompany, companyMessages)
        val listNumber = generateMobileNumbersForMultipleCountries()
        val numberData = Pair(listNumber, hardikMessages)

        // Generate and write usernames to file
        findViewById<TextView>(R.id.tv).apply {

            setOnClickListener {
                Log.v("TAG99", "onResume: clicked", )
                text = "Clicked"
                //promptUserToSaveFile()
//                Log.e("TAG99", "onResume: ${list.size}", )


                var totalMessageCount = 0
                Log.e("TAG99", "onResume: ${listCompany.size} - ${listNumber.size}", )
                //val message = generateMessages(companyData, numberData)
                generateMessagesInChunks(companyData, numberData) { chunk ->
                    // ✅ Save to DB, write to file, log, or send to UI
                    // writeChunkToFile(chunk)
                    totalMessageCount += chunk.size
                    messagesList.addAll(chunk)

                    text  = "${totalMessageCount}"
//                    chunk.forEachIndexed { index, s -> Log.e("TAG99", "onResume: $index - $s",)}

                    lifecycleScope.launch(Dispatchers.IO) {
                        insertMessages(this@MainActivity, chunk)
                    }

                }
                Log.e("TAG99", "onResume: $totalMessageCount", )

                //promptUserToSaveFile()
            }
        }

        binding.btnStart.setOnClickListener {
            var totalGenerated = 0
            generator.startGenerating(companyData, numberData) { chunk ->
                messagesList.addAll(chunk)
                //binding.tv.text= "Generated: ${messagesList.size}"
                binding.tv.text = "Generated: $totalGenerated"

            }
        }

        binding. btnPause.setOnClickListener {
            generator.pause()
        }

        binding.btnResume.setOnClickListener {
            generator.resume()
        }

        binding.btnStop.setOnClickListener {
            generator.stop()
            binding.tv.text = "Stopped at: ${messagesList.size}"
        }
    }

    /*fun generateMessages1(
        companyData: Pair<List<String>, Map<String, List<String>>>,
        numberData: Pair<List<String>, List<String>>
    ): List<Message> {
        val (companySenders, companyMessagesMap) = companyData
        val (numberSenders, numberMessages) = numberData

        val allMessages = mutableListOf<Message>()
        var idCounter = 1

        // Generate messages from company senders
        for (sender in companySenders) {
            val company = sender.substringAfter("-")
            val messages = companyMessagesMap[company] ?: continue

            val countToTake = if (messages.size >= 10) Random.nextInt(10, messages.size + 1) else messages.size
            val selectedMessages = messages.shuffled().take(countToTake)

            for (msg in selectedMessages) {
                allMessages.add(
                    Message(
                        id = idCounter++,
                        sender = sender,
                        messageBody = msg,
                        timestamp = getRandomTimestamp(),
                        messageType = 1,
                        //status = "complete",
                        messageIsRead = 0
                    )
                )

                if (idCounter > 100_000) break
            }

            if (idCounter > 100_000) break
        }

        // Generate messages from number senders
        for (sender in numberSenders) {
            val countToTake = if (numberMessages.size >= 10) Random.nextInt(10, numberMessages.size + 1) else numberMessages.size
            val selectedMessages = numberMessages.shuffled().take(countToTake)

            for (msg in selectedMessages) {
                allMessages.add(
                    Message(
                        id = idCounter++,
                        sender = sender,
                        messageBody = msg,
                        timestamp = getRandomTimestamp(),
                        messageType = 1,
                        //status = "complete",
                        messageIsRead = 0
                    )
                )

                if (idCounter > 200_000) break
            }

            if (idCounter > 200_000) break
        }

        return allMessages
    }

    fun generateMessages(
        companyData: Pair<List<String>, Map<String, List<String>>>,
        numberData: Pair<List<String>, List<String>>
    ): List<Message> {
        val (companySenders, companyMessagesMap) = companyData
        val (numberSenders, numberMessages) = numberData

        val allMessages = mutableListOf<Message>()
        var idCounter = 1

        // Generate messages from company senders
        for (sender in companySenders) {
            val company = sender.substringAfter("-")
            val messages = companyMessagesMap[company] ?: continue

            val countToTake = if (messages.size >= 10) Random.nextInt(10, messages.size + 1) else messages.size
            val selectedMessages = messages.shuffled().take(countToTake)

            for (msg in selectedMessages) {
                allMessages.add(
                    Message(
                        id = idCounter++,
                        sender = sender,
                        messageBody = msg,
                        timestamp = getRandomTimestamp(),
                        messageType = 1,
                        //status = "complete",
                        messageIsRead = 0
                    )
                )
            }
        }

        // Generate messages from number senders
        for (sender in numberSenders) {
            val countToTake = if (numberMessages.size >= 10) Random.nextInt(10, numberMessages.size + 1) else numberMessages.size
            val selectedMessages = numberMessages.shuffled().take(countToTake)

            for (msg in selectedMessages) {
                allMessages.add(
                    Message(
                        id = idCounter++,
                        sender = sender,
                        messageBody = msg,
                        timestamp = getRandomTimestamp(),
                        messageType = 1,
                        //status = "complete",
                        messageIsRead = 0
                    )
                )
            }
        }

        return allMessages
    }
*/



    /*
    
        val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            uri?.let {
                saveUsernamesToUri(it)
            }
        }
    
        fun promptUserToSaveFile() {
            createFileLauncher.launch("usernames.txt")
        }
    
        fun saveUsernamesToUri(uri: Uri) {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                generateUsernamesStreamed { username ->
                    writer.write(username)
                    writer.newLine()
                }
            }
        }
    */



   /* val gson: Gson = GsonBuilder().create()

    fun saveMessagesToUri(uri: Uri, messages: List<Message>) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            val writer = JsonWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
            writer.setIndent("  ") // Pretty print (optional)

            writer.beginArray() // Start of JSON array

            for (message in messages) {
                gson.toJson(message, Message::class.java, writer) // Write each message as JSON
            }

            writer.endArray() // End of JSON array
            writer.close()
        }
    }

    val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            saveMessagesToUri(it, messagesList) // messagesList = your List<Message>
        }
    }

    fun promptUserToSaveMessages() {
        createFileLauncher.launch("messages.json")
    }*/


    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val messageAdapter: JsonAdapter<Message> = moshi.adapter(Message::class.java)

    fun saveMessagesToUri(uri: Uri, messages: List<Message>) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            val bufferedSink = outputStream.sink().buffer()
            val writer = JsonWriter.of(bufferedSink) // ✅ Correct way to create Moshi's JsonWriter
            writer.setIndent("  ")

            writer.beginArray()
            for (message in messages) {
                messageAdapter.toJson(writer, message)
            }
            writer.endArray()

            writer.close() // This also flushes bufferedSink
        }
    }

    val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            saveMessagesToUri(it,messagesList)
        }
    }

    fun promptUserToSaveFile() {
        createFileLauncher.launch("messages.json")
    }

}


