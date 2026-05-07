package com.example.messagegenerator.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

object SmsDefaultAppHelper {
    private const val TAG = "BASE_TAG" + "SmsDefaultAppHelper"

    private lateinit var roleRequestLauncher: ActivityResultLauncher<Intent>
    private var resultCallback: ((Boolean) -> Unit)? = null

    fun registerResultLauncher(activity: ComponentActivity, callback: (Boolean) -> Unit) {
        resultCallback = callback
        roleRequestLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
                val isDefault = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
                //Log.e(TAG, if(isDefault)"Default SMS app set successfully" else "User canceled setting default SMS app")
                resultCallback?.invoke(isDefault)
            } else {
                val isDefault = isDefaultSmsApp(activity)
                //Log.e(TAG, if(isDefault)"Default SMS app set successfully" else "User canceled setting default SMS app")
                resultCallback?.invoke(isDefault)
            }

            if (result.resultCode == Activity.RESULT_OK) {
                Log.e(TAG, "Default SMS app set successfully")
                Toast.makeText(activity, "PrismApp set as default.", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "User canceled setting default SMS app")
                Toast.makeText(activity, "Please set PrismApp as default.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("InlinedApi")
    fun Activity.requestDefaultSmsApp() {
        Log.e(TAG, "requestDefaultSmsApp: ", )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+ (API 29+)
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    //Toast.makeText(applicationContext, "PrismApp is already the default SMS app.", Toast.LENGTH_SHORT).show()
                } else {
                    val roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    roleRequestLauncher.launch(roleRequestIntent)
                }
            }
        } else { // For Android 7, 8, 9 (API 24-28)
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(applicationContext)
            val myPackage = packageName

            if (defaultSmsPackage != myPackage) {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(intent)
            } else {
                //Toast.makeText(this, "PrismApp is already the default SMS app.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    @SuppressLint("ObsoleteSdkInt")
    fun isDefaultSmsApp(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return Telephony.Sms.getDefaultSmsPackage(activity) == activity.packageName
        }
        return true
    }

//    fun Activity.navigateToSetAsDefaultScreen(){
//        val intent = Intent(this, SetAsDefaultActivity::class.java)
//        startActivity(intent)
//        finish()
//    }
}
