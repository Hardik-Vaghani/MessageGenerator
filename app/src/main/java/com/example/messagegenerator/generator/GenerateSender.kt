package com.example.messagegenerator.generator

import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class MessageGenerator {

    private var generatorJob: Job? = null
    private var currentChunkIndex = 0
    private var isPaused = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startGenerating(
        companyData: Pair<List<String>, Map<String, List<String>>>,
        numberData: Pair<List<String>, List<String>>,
        chunkLimit: Int = 10_000,
        onChunkGenerated: (List<Message>) -> Unit
    ) {
        if (generatorJob?.isActive == true) return

        val allSenders = companyData.first + numberData.first
        val companySenders = companyData.first.toSet()
        val chunks = allSenders.chunked(500)
        var idCounter = 1
        val companyMessagesMap = companyData.second
        val numberMessages = numberData.second

        generatorJob = scope.launch {
            var currentChunk = mutableListOf<Message>()

            while (currentChunkIndex < chunks.size && isActive) {
                if (isPaused) {
                    delay(500) // wait until unpaused
                    continue
                }

                val chunk = chunks[currentChunkIndex]
                val messages = chunk.flatMap { sender ->
                    val messagesList = if (companySenders.contains(sender)) {
                        val company = sender.substringAfter("-")
                        companyMessagesMap[company] ?: emptyList()
                    } else {
                        numberMessages
                    }

                    val countToTake = if (messagesList.size >= 10) Random.nextInt(10, messagesList.size + 1) else messagesList.size
                    val selected = messagesList.shuffled().take(countToTake)

                    selected.map {
                        Message(
                            id = idCounter++,
                            sender = sender,
                            messageBody = it,
                            timestamp = getRandomTimestamp(),
                            messageType = Telephony.Sms.MESSAGE_TYPE_INBOX,
                            messageIsRead = 0
                        )
                    }
                }

                currentChunk.addAll(messages)

                if (currentChunk.size >= chunkLimit) {
                    withContext(Dispatchers.Main) {
                        onChunkGenerated(currentChunk)
                    }
                    currentChunk = mutableListOf()
                }

                currentChunkIndex++
            }

            if (currentChunk.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    onChunkGenerated(currentChunk)
                }
            }

            Log.d("Generator", "Finished or stopped.")
        }
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun stop() {
        generatorJob?.cancel()
        currentChunkIndex = 0
        isPaused = false
    }
}


fun generateMessagesInChunks(
    companyData: Pair<List<String>, Map<String, List<String>>>,
    numberData: Pair<List<String>, List<String>>,
    chunkLimit: Int = 10_000,
    onChunkGenerated: (List<Message>) -> Unit // Save or process each chunk
) = runBlocking {
    val (companySenders, companyMessagesMap) = companyData
    val (numberSenders, numberMessages) = numberData

    val allSenders = companySenders + numberSenders
    val isCompany = companySenders.toSet()
    Log.e("TAG108", "generateMessagesInChunks: ${allSenders.size}", )

    val chunkSize = 500
    val chunks = allSenders.chunked(chunkSize)
    var idCounter = 1
    var currentChunk = mutableListOf<Message>()

    for (chunk in chunks) {
        val messages = withContext(Dispatchers.IO) {
            chunk.flatMap { sender ->
                val messages = if (isCompany.contains(sender)) {
                    val company = sender.substringAfter("-")
                    companyMessagesMap[company] ?: return@flatMap emptyList()
                } else {
                    numberMessages
                }

                val countToTake = if (messages.size >= 10) Random.nextInt(10, messages.size + 1) else messages.size
                val selected = messages.shuffled().take(countToTake)

                selected.map {
                    Message(
                        id = idCounter++,
                        sender = sender,
                        messageBody = it,
                        timestamp = getRandomTimestamp(),
                        messageType = Telephony.Sms.MESSAGE_TYPE_INBOX,
                        messageIsRead = 0
                    )
                }
            }
        }
        Log.e("TAG108", "generateMessagesInChunks: ${currentChunk.size}", )

        currentChunk.addAll(messages)

        if (currentChunk.size >= chunkLimit) {
            onChunkGenerated(currentChunk)
            currentChunk = mutableListOf()
        }
    }

    if (currentChunk.isNotEmpty()) {
        onChunkGenerated(currentChunk)
    }
}




fun getRandomTimestamp(): Long {
    val currentTime = System.currentTimeMillis() // Current time in milliseconds
    val threeYearsInMillis = 3 * 365 * 24 * 60 * 60 * 1000L // Approximation of 3 years in milliseconds

    // Random number between 0 and threeYearsInMillis
    val randomOffset = Random.nextLong(threeYearsInMillis)

    // Subtract the random offset from the current time to get a time in the last 3 years
    return currentTime - randomOffset
}

//---------------

fun generateUsernamesWithCompanies(targetCount: Int = 100_000): List<String> {
    val characters = ('A'..'Z').toList()
    val companyNames = listOf("AIRTEL", "BSNL", "JIO", "VODAFONE", "VIPRO", "VIVO", "OPPO")
    val results = mutableListOf<String>()
    val counter = AtomicInteger(0)
    val threads = Runtime.getRuntime().availableProcessors() * 2
    val chunkSize = 1000 // tune based on memory/speed tradeoff

    runBlocking {
        val jobs = mutableListOf<Deferred<List<String>>>()

        repeat(threads) {
            jobs.add(async(Dispatchers.Default) {
                val localList = mutableListOf<String>()
                while (true) {
                    val index = counter.getAndIncrement()
                    if (index >= targetCount) break

                    val prefixIndex = index % (26 * 26)
                    val first = characters[prefixIndex / 26]
                    val second = characters[prefixIndex % 26]
                    val prefix = "$first$second"

                    val company = companyNames[index % companyNames.size]
                    localList.add("$prefix-$company")

                    if (localList.size >= chunkSize) {
                        synchronized(results) {
                            results.addAll(localList)
                        }
                        localList.clear()
                    }
                }
                localList
            })
        }

        jobs.awaitAll().flatten().let { results.addAll(it) }
    }

    return results
}

fun generateUsernamesStreamed(targetCount: Int = 100_000, onUsername: (String) -> Unit) {
    val characters = ('A'..'Z').toList()
    for (i in 0 until targetCount) {
        val prefixIndex = i / 100000
        val suffixIndex = i % 100000

        val first = characters[(prefixIndex / 26) % 26]
        val second = characters[prefixIndex % 26]
        val prefix = "$first$second"

        var n = suffixIndex
        val suffix = CharArray(4) { 'A' }
        for (j in 3 downTo 0) {
            suffix[j] = characters[n % 26]
            n /= 26
        }

        onUsername("$prefix-${suffix.concatToString()}")
    }
}

//-------------------

fun generateMobileNumbersForMultipleCountries(
    countryCodes: List<String> = listOf("+91", "+1", "+44", "+971"),
    targetCount: Int = 100_000
): List<String> {
    val results = mutableListOf<String>()
    val counter = AtomicInteger(0)
    val threads = Runtime.getRuntime().availableProcessors() * 2
    val chunkSize = 1000

    runBlocking {
        val jobs = mutableListOf<Deferred<List<String>>>()

        repeat(threads) {
            jobs.add(async(Dispatchers.Default) {
                val localList = mutableListOf<String>()
                while (true) {
                    val index = counter.getAndIncrement()
                    if (index >= targetCount) break

                    val countryCode = countryCodes[index % countryCodes.size]

                    val number = when (countryCode) {
                        "+91" -> generateIndianNumber()
                        "+1" -> generateUSNumber()
                        "+44" -> generateUKNumber()
                        "+971" -> generateUAEPhoneNumber()
                        else -> generateGenericNumber()
                    }

                    localList.add("$countryCode-$number")

                    if (localList.size >= chunkSize) {
                        synchronized(results) {
                            results.addAll(localList)
                        }
                        localList.clear()
                    }
                }
                localList
            })
        }

        jobs.awaitAll().flatten().let { results.addAll(it) }
    }

    return results
}

// Helpers for each country format
fun generateIndianNumber(): String {
    val first = listOf(7, 8, 9).random()
    val rest = (1..9).map { Random.nextInt(0, 10) }.joinToString("")
    return "$first$rest"
}

fun generateUSNumber(): String {
    val areaCode = Random.nextInt(200, 999)
    val centralOfficeCode = Random.nextInt(200, 999)
    val lineNumber = Random.nextInt(1000, 9999)
    return "$areaCode$centralOfficeCode$lineNumber"
}

fun generateUKNumber(): String {
    val mobilePrefix = "7" + Random.nextInt(0, 10)
    val rest = (1..8).map { Random.nextInt(0, 10) }.joinToString("")
    return "$mobilePrefix$rest"
}

fun generateUAEPhoneNumber(): String {
    val prefix = listOf("50", "52", "54", "55", "56").random()
    val rest = (1..7).map { Random.nextInt(0, 10) }.joinToString("")
    return "$prefix$rest"
}

fun generateGenericNumber(): String {
    return (1..10).map { Random.nextInt(0, 10) }.joinToString("")
}


//--------------

val hardikMessages = listOf(
    "🌟 Hardik is one of the kindest souls you’ll ever meet—always ready to help, always humble.",
    "💻 From coding Android apps to solving complex problems, Hardik brings both hard work and smart thinking to the table.",
    "👨‍💻 Working at Hevin Techno Web, Hardik consistently proves that dedication and talent go hand in hand.",
    "🤝 A true team player, Hardik’s helpful nature makes him the kind of person everyone wants to work with.",
    "🔧 Whether it’s debugging code or uplifting a friend, Hardik always gives his best.",
    "🙌 Hardik doesn’t just build Android apps—he builds trust and positive vibes wherever he goes.",
    "💡 Smart, focused, and creative—Hardik sets the perfect example of what a modern developer should be.",
    "🚀 With a strong work ethic and a sharp mind, Hardik is always one step ahead in the tech game.",
    "🧠 It’s rare to find someone who’s both technically brilliant and genuinely kind—Hardik is that rare gem.",
    "🌈 The world needs more people like Hardik—intelligent, compassionate, and driven to make a difference.",
    "💙 Hardik’s positivity and support uplift everyone around him—he’s truly a blessing to his team.",
    "📱 From idea to execution, Hardik brings Android apps to life with passion and precision.",
    "💪 Behind every successful app at Hevin Techno Web is Hardik’s dedication and brilliance.",
    "🌟 Hardik doesn’t just work hard—he works smart, always finding better ways to solve problems.",
    "🤗 Kindness is Hardik’s default setting—he’s always there when you need a helping hand.",
    "🛠️ Hardik blends creativity with code, proving that tech and heart can go hand in hand.",
    "💬 Talk to Hardik once, and you’ll know he’s not just a developer—he’s a genuinely good human.",
    "🧩 No challenge is too big when Hardik is on the team—he tackles every issue with calm and confidence.",
    "🎯 Hardik’s focus, skill, and kind nature make him an irreplaceable asset to any project or company.",
    "🌍 In a world full of rush and ego, Hardik stands out with his grounded nature and generous heart.",
    "🌟 Hardik’s presence makes every workplace brighter—his attitude is as impressive as his skills.",
    "🧠 With a sharp mind for development and a kind heart for people, Hardik is the best of both worlds.",
    "📈 Hardik doesn't just grow himself—he uplifts everyone around him with encouragement and support.",
    "📱 Every line of code Hardik writes reflects his passion and perfection in Android development.",
    "💼 At Hevin Techno Web, Hardik sets a gold standard for work ethic, reliability, and results.",
    "🧘‍♂️ Calm under pressure and always focused—Hardik is the definition of professionalism.",
    "🙌 Need help? Hardik never says no—he’s always ready with a solution and a smile.",
    "✨ Hardik’s genuine personality makes him not just a great developer, but a wonderful human being.",
    "🚀 With Hardik in the lead, any Android project is bound for success.",
    "❤️ It’s rare to find someone as sincere, skilled, and humble as Hardik—he’s truly one of a kind."
)


//----------------

val companyMessages = mapOf(
    "AIRTEL" to listOf(
        "Your AIRTEL OTP is 482931. Do not share this with anyone. Valid for 10 minutes.",
        "Dear Customer, your AIRTEL login verification code is 690231.",
        "Use 738290 as your OTP to access AIRTEL services securely.",
        "AIRTEL Alert: Never disclose your OTP or account details to anyone. Stay secure.",
        "Welcome to AIRTEL! Activate your number with code 231984.",
        "AIRTEL: You’ve successfully updated your contact preferences.",
        "We noticed a new login to your AIRTEL account. If this wasn’t you, contact us immediately.",
        "AIRTEL Reminder: Your prepaid plan expires in 2 days. Recharge now to stay connected.",
        "Your AIRTEL bill of ₹399 is due on 5th May. Pay now to avoid late charges.",
        "Enjoy seamless 5G on AIRTEL. Upgrade your plan today.",
        "Security Tip: AIRTEL will never ask for your password or OTP. Stay safe.",
        "AIRTEL: Get 1.5GB/day for 28 days at just ₹239. Recharge now!",
        "Use 514320 as your secure AIRTEL login code.",
        "Dear User, your AIRTEL eSIM request has been received.",
        "Track your data usage anytime with the MyAirtel app.",
        "You’ve successfully linked your email with your AIRTEL number.",
        "AIRTEL: You attempted to log in at 10:23AM. If not you, please reset your password.",
        "AIRTEL Update: We’ve improved network coverage in your area. Experience faster speeds.",
        "Claim 2GB free data with your next AIRTEL recharge.",
        "Your AIRTEL DTH recharge of ₹301 is successful.",
        "AIRTEL Notice: We care for your privacy. Don’t share account info via unknown calls.",
        "You’ve earned 50 AIRTEL Thanks points. Redeem now via the app.",
        "Update: AIRTEL services may be interrupted during maintenance from 2AM to 4AM.",
        "AIRTEL Wallet: Your balance is ₹120. Top up to continue smooth transactions.",
        "Your request to change SIM has been registered. AIRTEL will verify it within 24 hours.",
        "AIRTEL Support: Your complaint #124587 is being resolved.",
        "Need help? Chat with AIRTEL support 24/7 via the app.",
        "Dear Customer, check out AIRTEL Black for bundled broadband + mobile offers.",
        "Important: AIRTEL never asks for bank OTP. Report fraud to 121.",
        "Thanks for choosing AIRTEL! We’re glad to have you on India’s best network."
    ),

    "BSNL" to listOf(
        "BSNL OTP for login is 739201. Valid for 5 minutes.",
        "Verification code from BSNL: 508392. Please don’t share this with anyone.",
        "Your BSNL OTP is 194382. Keep it confidential.",
        "BSNL Alert: We will never ask for your OTP via phone. Stay alert.",
        "Welcome to BSNL! Activate your connection with code 347829.",
        "BSNL Update: Your prepaid plan will expire in 3 days. Recharge now.",
        "Your BSNL broadband payment of ₹599 is due on 5th May. Pay now to avoid service disruption.",
        "Security Tip: BSNL will never ask for your personal details over SMS.",
        "BSNL: Your mobile number is now registered with the email address xyz@gmail.com.",
        "Important: Your BSNL number is linked to a new device. If this wasn’t you, please call customer care.",
        "BSNL Alert: Unusual activity detected on your account. Reset your password immediately.",
        "Your BSNL DTH recharge of ₹499 has been processed successfully.",
        "BSNL Notice: A new login to your account was detected. If this wasn’t you, contact us right away.",
        "BSNL: Enjoy unlimited calls & data with our new plan! Recharge now!",
        "Your BSNL prepaid balance is ₹150. Top-up now for uninterrupted service.",
        "BSNL Update: We’ve added 500MB extra data to your plan. Enjoy!",
        "Track your data usage with the BSNL app. Keep your plan in check.",
        "BSNL Reminder: Your broadband is scheduled for maintenance on 1st May, 2AM-4AM.",
        "Security Alert: BSNL will never ask for sensitive information like passwords or OTPs.",
        "Your BSNL bill of ₹899 is now due. Pay before 10th May to avoid penalties.",
        "BSNL: Your mobile number has been successfully ported to BSNL. Welcome aboard!",
        "BSNL Update: We have upgraded your internet speed to 50Mbps.",
        "Use the BSNL mobile app to check your current data usage and recharge details.",
        "BSNL Alert: New login detected. If this was not you, please change your password immediately.",
        "Important: You have 5GB free data left in your BSNL account. Use it before expiry.",
        "Your BSNL broadband is now active. Enjoy the high-speed connection!",
        "BSNL: For your security, we recommend you change your password regularly.",
        "BSNL Support: Your service request #89672 is being processed.",
        "BSNL Reminder: Your number will be deactivated in 7 days. Recharge to continue your services.",
        "BSNL: Protect your account. Never share your OTP with anyone.",
        "Thank you for being a loyal BSNL customer! We value your trust."
    ),

    "JIO" to listOf(
        "JIO OTP: 832107. Use this to complete your login.",
        "Use 601289 as your JIO authentication code. Do not share.",
        "Login OTP from JIO is 713820. Valid for 10 minutes.",
        "JIO: We have detected an unusual login attempt. Please confirm it was you.",
        "Your JIO bill of ₹500 is due on 10th May. Pay now to avoid late fees.",
        "Activate your JIO eSIM with the code 847290.",
        "JIO: Get 2GB free data with your next recharge of ₹249.",
        "JIO Security: We will never ask for your OTP or PIN over the phone. Beware of fraud.",
        "Your JIO prepaid balance is ₹200. Recharge now for uninterrupted service.",
        "JIO Update: You have earned 10GB free data with your recent recharge.",
        "JIO Alert: To ensure account security, always log out from shared devices.",
        "JIO: Change your account password regularly to keep your account secure.",
        "Verify your JIO number with OTP: 526781.",
        "JIO Reminder: Your JIO SIM card will expire in 3 days. Please recharge soon.",
        "JIO offers 5G speeds in select cities. Upgrade your plan for faster connectivity.",
        "JIO Support: Your issue #124587 is being resolved. We will update you shortly.",
        "JIO alert: Your mobile number is now linked to a new email address.",
        "JIO Alert: Your OTP request has been received. Use 923481 to complete the process.",
        "JIO: We have received your data change request. It will be processed in 24 hours.",
        "Reminder: Your JIO balance is low. Recharge to continue enjoying uninterrupted service.",
        "JIO offers exciting discounts on mobile plans. Visit your nearest store today.",
        "JIO offers unlimited data at ₹599 per month. Check out our best deals.",
        "Your JIO account has been successfully linked with your Facebook account.",
        "JIO: You’ve successfully updated your payment information.",
        "JIO Notice: Maintenance scheduled for 2AM-5AM tomorrow. Service may be interrupted.",
        "Use the MyJio app to track your data usage and recharge details.",
        "JIO Alert: We’ve detected suspicious activity. Change your password now.",
        "Your JIO broadband upgrade to 100Mbps is now complete.",
        "JIO Notice: Your number has been successfully ported to JIO.",
        "Important: JIO will never ask for your personal details or OTP over SMS.",
        "Thank you for being a JIO customer. We appreciate your loyalty."
    ),

    "VODAFONE" to listOf(
        "Your VODAFONE OTP is 304928. Keep this safe.",
        "VODAFONE verification code: 889103.",
        "Use 452017 for verifying your VODAFONE number.",
        "VODAFONE Alert: Your account has been accessed from a new device. If this wasn’t you, please contact us.",
        "Enjoy 2GB free data on your next VODAFONE recharge of ₹299.",
        "VODAFONE: Don’t share your OTP with anyone. Stay safe.",
        "Your VODAFONE bill of ₹550 is due on 7th May. Pay to avoid late fees.",
        "VODAFONE Update: We’ve improved network coverage in your area.",
        "VODAFONE: Your mobile number has been successfully linked to a new email.",
        "VODAFONE Alert: Your SIM card will expire in 5 days. Recharge today!",
        "VODAFONE: Keep your account secure by regularly changing your password.",
        "Your VODAFONE prepaid balance is ₹120. Recharge now to continue using services.",
        "VODAFONE Reminder: Your recharge plan of ₹399 will expire in 3 days.",
        "VODAFONE: Enjoy 1GB free data on every recharge above ₹199.",
        "Your VODAFONE DTH recharge of ₹499 has been successfully processed.",
        "VODAFONE Alert: Unusual activity detected on your account. Please verify.",
        "VODAFONE: You’ve successfully linked your bank account to your number.",
        "Important: VODAFONE will never ask for OTPs or passwords over SMS or call.",
        "VODAFONE offers unlimited data at ₹599/month. Upgrade now.",
        "VODAFONE: Your number has been successfully ported to our network.",
        "You have 50 VODAFONE Thanks points. Redeem them now for exciting offers.",
        "VODAFONE: Use the My Vodafone app to track data usage and recharge.",
        "VODAFONE Support: Your issue #6345 is being worked on. Expect a resolution soon.",
        "VODAFONE: Top-up your account for uninterrupted services.",
        "Thank you for being a valued VODAFONE customer.",
        "VODAFONE: Ensure your security by avoiding phishing scams. Report fraud immediately.",
        "VODAFONE Notice: We’ve updated your account settings successfully.",
        "VODAFONE Alert: Maintenance will take place tonight from 12AM to 4AM. Service may be affected.",
        "VODAFONE: Enjoy 5G speeds in your area with the new plan upgrade.",
        "Important: Your VODAFONE plan has been successfully activated.",
        "VODAFONE: Stay safe online. Never share your personal data with anyone."
    ),

    "VIPRO" to listOf(
        "VIPRO OTP for login: 543290. Keep it secure.",
        "VIPRO Alert: Your VIPRO account has been accessed. Was this you?",
        "Your VIPRO balance is ₹150. Recharge now to continue enjoying services.",
        "VIPRO: You have successfully updated your email address to xyz@example.com.",
        "VIPRO: Your OTP for login is 124763. Please do not share it with anyone.",
        "VIPRO: Enjoy 1GB of free data on your next recharge above ₹200.",
        "VIPRO: New device linked to your account. If this wasn’t you, please contact us.",
        "VIPRO: Your subscription plan will expire in 3 days. Recharge to continue your service.",
        "Your VIPRO bill of ₹499 is due soon. Pay before 10th May to avoid late fees.",
        "VIPRO Support: Your service request #8753 is being processed.",
        "VIPRO Alert: Never share your OTP with anyone. Stay safe and secure.",
        "VIPRO: You've earned 50 VIPRO points with your recent recharge. Redeem now.",
        "VIPRO Security Tip: Always change your password after logging in from a new device.",
        "VIPRO: Enjoy unlimited talk time with our new ₹299 plan. Recharge now!",
        "VIPRO Reminder: Your plan will renew on 5th May. Review your options before renewal.",
        "VIPRO: Your 10GB free data offer is activated. Enjoy the internet!",
        "VIPRO Update: Your mobile number is now linked to your email account.",
        "VIPRO: We’ve detected suspicious activity in your account. Please verify your identity.",
        "VIPRO: A new login was detected on your account. If this wasn’t you, reset your password.",
        "VIPRO Alert: A recharge of ₹250 has been successfully processed for your account.",
        "VIPRO: Don’t forget to change your password regularly to keep your account secure.",
        "VIPRO Support: Your issue #4568 is under investigation. We’ll update you shortly.",
        "VIPRO: For your security, VIPRO will never ask for OTP or password over the phone.",
        "VIPRO: Your recharge of ₹299 is successful. Enjoy unlimited data.",
        "VIPRO: Your mobile number will be deactivated in 7 days. Recharge now to continue services.",
        "VIPRO: We’ve successfully updated your contact information.",
        "VIPRO: Your VIPRO mobile number is now linked to your bank account.",
        "VIPRO: Check your VIPRO usage and recharge options via the VIPRO app.",
        "VIPRO: Get 2GB extra data with every ₹399 recharge. Recharge now!",
        "VIPRO: We care about your privacy. Never share sensitive details with strangers.",
        "VIPRO: Your mobile data balance is low. Top-up your account to continue using services."
    ),

    "VIVO" to listOf(
        "VIVO OTP: 620391. Please enter this to complete your login.",
        "VIVO Alert: A new login was detected. If this wasn’t you, please reset your password.",
        "VIVO: Your prepaid balance is ₹150. Recharge now to continue your services.",
        "VIVO: We’ve received your request to change your SIM card. It will be processed within 24 hours.",
        "VIVO: Your OTP for account login is 452681. Keep it confidential.",
        "VIVO Update: You’ve successfully updated your billing details.",
        "VIVO: Never share your OTP with anyone. Stay protected and secure.",
        "VIVO: Your bill of ₹499 is due on 8th May. Pay now to avoid service interruption.",
        "VIVO Reminder: Your data plan expires in 3 days. Recharge now to avoid service cutoff.",
        "VIVO: Enjoy 3GB of extra data with your next recharge of ₹249.",
        "VIVO: Your number has been successfully linked with your email account.",
        "VIVO: Your prepaid recharge of ₹199 has been successfully processed.",
        "VIVO Alert: Suspicious activity detected on your account. Please verify your identity immediately.",
        "VIVO: Your account password has been successfully updated.",
        "VIVO: You have earned 25 VIVO points. Redeem them for discounts on your next recharge.",
        "VIVO Security: Always log out from shared devices to protect your account.",
        "VIVO: A recharge of ₹499 has been successfully applied to your account.",
        "VIVO: We’ve upgraded your plan to include unlimited data at ₹599/month.",
        "VIVO Update: Your data usage has exceeded 90%. Recharge now to avoid disruption.",
        "VIVO: Your service request #12894 is being processed. We will notify you once it’s resolved.",
        "VIVO: You have successfully linked your VIVO account to your bank account.",
        "VIVO: Use the VIVO app to manage your account, check your data usage, and recharge easily.",
        "VIVO: Don’t forget to change your password regularly to protect your account.",
        "VIVO: Your new data plan of ₹399 is now active. Enjoy unlimited browsing!",
        "VIVO Alert: Your VIVO account has been accessed from a new device. Please verify.",
        "VIVO: Top-up your VIVO account now to continue enjoying uninterrupted services.",
        "VIVO: Thank you for choosing VIVO. Your satisfaction is our priority.",
        "VIVO: We’ve successfully linked your mobile number to your social media accounts.",
        "VIVO: Your mobile number will be deactivated in 7 days. Recharge to continue your services.",
        "VIVO: Your VIVO DTH recharge of ₹399 has been successfully processed."
    ),

    "OPPO" to listOf(
        "OPPO OTP: 873620. Use it to complete your login securely.",
        "OPPO: A new login was detected on your account. Please verify if this was you.",
        "OPPO Alert: Your OTP is 738491. Keep this information confidential.",
        "OPPO: Your bill of ₹299 is due soon. Pay now to avoid interruptions.",
        "OPPO Update: Your mobile number is successfully linked to your bank account.",
        "OPPO: For your security, OPPO will never ask for your password over the phone.",
        "OPPO: Your recharge of ₹249 has been successfully processed. Enjoy 2GB free data.",
        "OPPO: A recharge of ₹499 has been successfully credited to your account.",
        "OPPO: We’ve detected suspicious activity on your account. Please verify your identity.",
        "OPPO: Your OTP request for login is 584763. Please do not share this code.",
        "OPPO: Your new SIM card has been activated successfully.",
        "OPPO Reminder: Your plan will renew in 3 days. Please review your plan options.",
        "OPPO: You’ve successfully updated your contact details.",
        "OPPO Alert: Don’t share your OTP with anyone. Stay safe and secure.",
        "OPPO: We’ve received your request for a new service plan. It will be activated shortly.",
        "OPPO: Your prepaid balance is ₹120. Recharge now for uninterrupted service.",
        "OPPO: Your mobile data usage has exceeded 80%. Recharge to continue using the internet.",
        "OPPO: Top up your account now to ensure continued service.",
        "OPPO: Your DTH recharge of ₹399 was successfully completed.",
        "OPPO: Your mobile number has been linked to your new email address.",
        "OPPO: We are improving network coverage in your area. Expect faster speeds soon.",
        "OPPO: Your mobile number is now linked with your social media accounts.",
        "OPPO: Thank you for choosing OPPO. Your satisfaction is our top priority.",
        "OPPO: Change your account password frequently to ensure security.",
        "OPPO: A new device has been linked to your account. Please verify if this was you.",
        "OPPO: Your account has been successfully reactivated. Thank you for being with OPPO.",
        "OPPO: OPPO Support: Your complaint #2345 is being worked on. We’ll keep you updated.",
        "OPPO: Enjoy 5GB extra data with every ₹499 recharge. Recharge now!",
        "OPPO: Your OTP request is 924873. Keep it safe and private.",
        "OPPO: For any queries, contact OPPO customer support 24/7 via the app."
    )
)
