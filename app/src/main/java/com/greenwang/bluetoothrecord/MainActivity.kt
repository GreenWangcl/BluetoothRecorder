package com.greenwang.bluetoothrecord

import android.Manifest.permission
import android.app.AlertDialog
import android.content.*
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val rxPermissions by lazy { RxPermissions(this) }
    private var startScoRecord = false
    private var stopScoRecord = false
    private var audioFile: File? = null

    private val socAudioStateReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onSocStateChanged(
                    intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                            == AudioManager.SCO_AUDIO_STATE_CONNECTED
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv_dir.setText("录音文件位置：/sdcard/Android/data/$packageName/files/audio")
        btn_record.setOnClickListener {
            btn_record.isEnabled = false
            rxPermissions.request(permission.WRITE_EXTERNAL_STORAGE, permission.RECORD_AUDIO)
                .subscribe { granted ->
                    if (granted) {
                        startRecord()
                    } else {
                        btn_record.isEnabled = true
                        startSetting()
                    }
                }
        }
        btn_play.setOnClickListener {
            audioFile?.run {
                btn_play.isEnabled = false
                play(absolutePath)
            } ?: toast("没有可播放的音频文件")
        }

        registerReceiver(
            socAudioStateReceiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        )
    }

    override fun onDestroy() {
        unregisterReceiver(socAudioStateReceiver)
        super.onDestroy()
    }

    private fun startSetting() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            intent.action = Settings.ACTION_APPLICATION_SETTINGS
            startActivity(intent)
        }
    }

    private fun startRecord() {
        (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.run {
            when {
                switch_sco.isChecked && !isBluetoothScoOn -> {
                    startScoRecord = true
                    startBluetoothSco()
                }
                !switch_sco.isChecked && isBluetoothScoOn -> {
                    stopScoRecord = true
                    stopBluetoothSco()
                }
                else -> {
                    doRecord()
                }
            }
        } ?: toast("无法录音")
    }

    private fun onSocStateChanged(enable: Boolean) {
        if (startScoRecord && enable) {
            startScoRecord = false
            doRecord()
        }
        if (stopScoRecord && !enable) {
            stopScoRecord = false
            doRecord()
        }
    }

    private fun doRecord() {
        val audioManager = (getSystemService(Context.AUDIO_SERVICE) as AudioManager)

        toast("开始录音（蓝牙声源 ${if (audioManager.isBluetoothScoOn) "开启" else "关闭"}）")
        btn_record.isEnabled = true

        val dir = getExternalFilesDir("audio")
        if (dir == null) {
            toast("无法保存录音文件")
            return
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().time)

        val recorder = MediaRecorder()
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(8000)
        val filePath = "${dir.absolutePath}${File.separator}$timestamp.mp3"
        recorder.setOutputFile(filePath)

        try {
            recorder.prepare()
        } catch (e: IOException) {
            audioManager.stopBluetoothSco()
            toast("录音失败")
            return
        }
        try {
            recorder.start()
        } catch (e: IOException) {
            audioManager.stopBluetoothSco()
            recorder.stop()
            recorder.release()
            toast("录音失败")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Alert")
            .setMessage("Recording...")
            .setCancelable(false)
            .setNeutralButton("Stop") { dialog, _ ->
                audioManager.stopBluetoothSco()
                recorder.stop()
                recorder.release()
                audioFile = File(filePath)
                toast("录音结束")
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun play(path: String) {
        val player = MediaPlayer()
        player.setOnCompletionListener {
            btn_play.isEnabled = true
            toast("播放结束")
        }
        try {
            player.setDataSource(path)
            player.prepare()
            player.start()
            toast("正在播放: $path")
        } catch (e: Exception) {
            btn_play.isEnabled = true
            toast("播放失败")
            player.release()
            player.stop()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
