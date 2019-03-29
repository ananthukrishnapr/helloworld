package com.neurosky.algo_sdk_sample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import android.content.res.AssetManager;
import android.app.AlertDialog;

import java.io.OutputStream;
import java.util.Calendar;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

import com.neurosky.AlgoSdk.NskAlgoConfig;
import com.neurosky.AlgoSdk.NskAlgoDataType;
import com.neurosky.AlgoSdk.NskAlgoSdk;
import com.neurosky.AlgoSdk.NskAlgoSignalQuality;
import com.neurosky.AlgoSdk.NskAlgoState;
import com.neurosky.AlgoSdk.NskAlgoType;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;
import com.neurosky.connection.DataType.MindDataType;

import com.androidplot.xy.*;

public class MainActivity extends Activity {

    final String TAG = "MainActivityTag";
    /*static {
        System.loadLibrary("NskAlgoAndroid");
    }*/

    // COMM SDK handles
    private TgStreamReader tgStreamReader;
    private BluetoothAdapter mBluetoothAdapter;

    // canned data variables
    private short raw_data[] = {0};
    private int raw_data_index= 0;

    private Button neurosky_connect_btn;
    private TextView attValue;
    private TextView forced_blink_strength_text;
    private TextView direction_text;
    private TextView state_text;
    private TextView normal_blink_strength_text;
    private TextView sqText;
    private TextView test_textview;
    private NskAlgoSdk nskAlgoSdk;
    long previous_click_time;

    //Additional Bluetooth Connect Variables HC-06
    private final String DEVICE_ADDRESS = "98:D3:31:90:82:9A"; //MAC Address of Bluetooth Module
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private BluetoothDevice device;
    private BluetoothSocket socket;

    private OutputStream outputStream;
    private InputStream inputStream;
    boolean connected = false;
    boolean isRunning = false;
    String command;
    Button bt_connect_btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bt_connect_btn = (Button) findViewById(R.id.bt_connect_btn);

        neurosky_connect_btn = (Button) findViewById(R.id.neurosky_connect_btn);

        neurosky_connect_btn.setEnabled(false);


        final String[] directions = { "FORWARD", "REVERSE", "LEFT", "RIGHT" };


        final CountDownTimer myCountDownTimerObject = new CountDownTimer(12000, 2000)
        {
            int direction_counter = 0;

            public void onTick(long millis)
            {
                isRunning = true;
                direction_text.setText(directions[direction_counter]);
                direction_counter++;

                if(direction_counter == 4)
                {
                    direction_counter = 0;
                }
            }

            public void onFinish()
            {
                isRunning = false;
                state_text.setText("STANDBY");
                direction_text.setText("--");
            }
        };

        nskAlgoSdk = new NskAlgoSdk();

        try {
            // (1) Make sure that the device supports Bluetooth and Bluetooth is on
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Toast.makeText(
                        this,
                        "Please enable your Bluetooth and re-run this program !",
                        Toast.LENGTH_LONG).show();
                //finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "error:" + e.getMessage());
            return;
        }

        attValue = (TextView)this.findViewById(R.id.attText);
        forced_blink_strength_text = (TextView) this.findViewById(R.id.forced_blink_strength_text);
        direction_text = (TextView) this.findViewById(R.id.direction_text);
        state_text = (TextView) this.findViewById(R.id.state_text);
        normal_blink_strength_text = (TextView) this.findViewById(R.id.normal_blink_strength_text);
        test_textview = (TextView) this.findViewById(R.id.test_textview);
        sqText = (TextView)this.findViewById(R.id.sqText);


        int algoTypes = 0;

            algoTypes += NskAlgoType.NSK_ALGO_TYPE_MED.value;

            algoTypes += NskAlgoType.NSK_ALGO_TYPE_ATT.value;

            algoTypes += NskAlgoType.NSK_ALGO_TYPE_BLINK.value;

            algoTypes += NskAlgoType.NSK_ALGO_TYPE_BP.value;


        int ret = nskAlgoSdk.NskAlgoInit(algoTypes, getFilesDir().getAbsolutePath());

        nskAlgoSdk.NskAlgoStart(false);

        state_text.setText("--");

        // HC-06 BLUETOOTH CONNECT BUTTON SHIT

        bt_connect_btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v)
            {

                if(BTinit())
                {
                    BTconnect();
                }

            }


        });

        neurosky_connect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                raw_data = new short[512];
                raw_data_index = 0;

                tgStreamReader = new TgStreamReader(mBluetoothAdapter,callback);

                if(tgStreamReader != null && tgStreamReader.isBTConnected()){

                    // Prepare for connecting
                    tgStreamReader.stop();
                    tgStreamReader.close();
                }

                tgStreamReader.connect();
                state_text.setText("STANDBY");
            }
        });

        nskAlgoSdk.setOnSignalQualityListener(new NskAlgoSdk.OnSignalQualityListener() {
            @Override
            public void onSignalQuality(final int level) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String sqStr = NskAlgoSignalQuality.values()[level].toString();
                        sqText.setText(sqStr);

                        if(sqText.getText().toString().equals("NOT DETECTED") || sqText.getText().toString().equals("POOR") || sqText.getText().toString().equals("MEDIUM"))
                        {
                            command = "3";

                            try
                            {
                                outputStream.write(command.getBytes());
                            }
                            catch(IOException e)
                            {
                                e.printStackTrace();
                            }

                            state_text.setText("STANDBY");
                            nskAlgoSdk.setOnAttAlgoIndexListener(null);
                            attValue.setText("--");

                            if(isRunning = true) {
                                myCountDownTimerObject.cancel();
                                direction_text.setText("--");
                            }

                        }

                    }
                });
            }
        });

   /*     nskAlgoSdk.setOnMedAlgoIndexListener(new NskAlgoSdk.OnMedAlgoIndexListener() {
            @Override
            public void onMedAlgoIndex(int value) {
                Log.d(TAG, "NskAlgoMedAlgoIndexListener: Meditation:" + value);
                String medStr = "[" + value + "]";
                final String finalMedStr = medStr;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        medValue.setText(finalMedStr);
                    }
                });
            }
        });   */

            nskAlgoSdk.setOnEyeBlinkDetectionListener(new NskAlgoSdk.OnEyeBlinkDetectionListener() {
                @Override
                public void onEyeBlinkDetect(int strength) {
                    Log.d(TAG, "NskAlgoEyeBlinkDetectionListener: Eye blink detected: " + strength);
                    final int final_strength = strength;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(final_strength >= 95)
                            {
                                forced_blink_strength_text.setText(String.valueOf(final_strength));

                                if(state_text.getText().toString().equals("STANDBY"))
                                {
                                    state_text.setText("COMMAND");
                                }
                            }
                            else
                            {
                                normal_blink_strength_text.setText(String.valueOf(final_strength));

                                if(state_text.getText().toString().equals("RUNNING"))
                                {
                                    long temp = System.currentTimeMillis();

                                    if(previous_click_time != 0)
                                    {
                                        if(temp - previous_click_time < 400)
                                        {
                                            test_textview.setText("DOUBLE BLINK");

                                            command = "3";

                                            try
                                            {
                                                outputStream.write(command.getBytes());
                                            }
                                            catch(IOException e)
                                            {
                                                e.printStackTrace();
                                            }

                                            state_text.setText("STANDBY");
                                            direction_text.setText("--");
                                            attValue.setText("--");
                                        }
                                        else
                                        {
                                            test_textview.setText("NO DOUBLE BLINK");
                                        }
                                    }

                                    previous_click_time = temp;
                                }
                                else if(state_text.getText().toString().equals("COMMAND"))
                                {
                                    long temp = System.currentTimeMillis();

                                    if(previous_click_time != 0)
                                    {
                                        if(temp - previous_click_time < 1000)
                                        {
                                            test_textview.setText("DOUBLE BLINK");

                                            myCountDownTimerObject.cancel();

                                            if(direction_text.getText().toString().equals("FORWARD"))
                                            {
                                                command = "4";
                                            }
                                            else if(direction_text.getText().toString().equals("REVERSE"))
                                            {
                                                command = "5";
                                            }
                                            else if(direction_text.getText().toString().equals("LEFT"))
                                            {
                                                command = "6";
                                            }
                                            else if(direction_text.getText().toString().equals("RIGHT"))
                                            {
                                                command = "7";
                                            }

                                            state_text.setText("FOCUS");
                                        }
                                        else
                                        {
                                            test_textview.setText("NO DOUBLE BLINK");
                                        }
                                    }

                                    previous_click_time = temp;
                                }
                            }
                        }
                    });
                }
            });

        // CODE BELOW IS TEXT CHANGE LISTENER OF STATE TEXT
        state_text.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                if(state_text.getText().toString().equals("COMMAND"))
                {

                    myCountDownTimerObject.start();

                }
                else if(state_text.getText().toString().equals("FOCUS"))
                {

                        nskAlgoSdk.setOnAttAlgoIndexListener(new NskAlgoSdk.OnAttAlgoIndexListener() {
                            @Override
                            public void onAttAlgoIndex(int value) {
                                //Log.d(TAG, "NskAlgoAttAlgoIndexListener: Attention:" + value);
                                String attStr = "[" + value + "]";
                                final String finalAttStr = attStr;

                                final int att_int = value;

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // change UI elements here
                                        attValue.setText(finalAttStr);

                                        if (att_int >= 50 && state_text.getText().toString().equals("FOCUS"))
                                        {

                                            try {
                                                outputStream.write(command.getBytes());
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                            state_text.setText("RUNNING");
                                        }
                                    }

                                });

                            }
                        });


                }
                else if(state_text.getText().toString().equals("RUNNING"))
                {
                    nskAlgoSdk.setOnAttAlgoIndexListener(null);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    } // END OF ONCREATE

    public boolean BTinit()
    {
        boolean found = false;

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null) //Checks if the device supports bluetooth
        {
            Toast.makeText(getApplicationContext(), "Device doesn't support bluetooth", Toast.LENGTH_SHORT).show();
        }

        if(!bluetoothAdapter.isEnabled()) //Checks if bluetooth is enabled. If not, the program will ask permission from the user to enable it
        {
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter,0);

            try
            {
                Thread.sleep(1000);
            }
            catch(InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        if(bondedDevices.isEmpty()) //Checks for paired bluetooth devices
        {
            Toast.makeText(getApplicationContext(), "Please pair the device first", Toast.LENGTH_SHORT).show();
        }
        else
        {
            for(BluetoothDevice iterator : bondedDevices)
            {
                if(iterator.getAddress().equals(DEVICE_ADDRESS))
                {
                    device = iterator;
                    found = true;
                    break;
                }
            }
        }

        return found;
    }

    public boolean BTconnect()
    {
        try
        {
            socket = device.createRfcommSocketToServiceRecord(PORT_UUID); //Creates a socket to handle the outgoing connection
            socket.connect();

            Toast.makeText(getApplicationContext(),
                    "Connection to HC-06 Bluetooth Module successful", Toast.LENGTH_LONG).show();
            connected = true;
            neurosky_connect_btn.setEnabled(true);
        }
        catch(IOException e)
        {
            e.printStackTrace();
            connected = false;
        }

        if(connected)
        {
            try
            {
                outputStream = socket.getOutputStream(); //gets the output stream of the socket
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }

            try
            {
                inputStream = socket.getInputStream(); //gets the input stream of the socket
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        return connected;
    }

    @Override
    public void onBackPressed() {
        nskAlgoSdk.NskAlgoUninit();
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private TgStreamHandler callback = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d(TAG, "connectionStates change to: " + connectionStates);
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    // Do something when connecting
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    // Do something when connected
                    tgStreamReader.start();
                    showToast("Connection to Neurosky Mindwave Mobile successful", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_WORKING:
                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    showToast("Get data time out!", Toast.LENGTH_SHORT);

                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }

                    break;
                case ConnectionStates.STATE_STOPPED:
                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    break;
                case ConnectionStates.STATE_ERROR:
                    break;
                case ConnectionStates.STATE_FAILED:
                    break;
            }
        }

        @Override
        public void onRecordFail(int flag) {
            // You can handle the record error message here
            Log.e(TAG,"onRecordFail: " +flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // You can handle the received data here
            // You can feed the raw data to algo sdk here if necessary.
            //Log.i(TAG,"onDataReceived");
            switch (datatype) {
                case MindDataType.CODE_ATTENTION:
                    short attValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_ATT.value, attValue, 1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    short medValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_MED.value, medValue, 1);
                    break;
                case MindDataType.CODE_POOR_SIGNAL:
                    short pqValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value, pqValue, 1);
                    //sample.setText(String.valueOf(pqValue));
                    break;
                case MindDataType.CODE_RAW:
                    raw_data[raw_data_index++] = (short)data;
                    if (raw_data_index == 512) {
                        nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value, raw_data, raw_data_index);
                        raw_data_index = 0;
                    }
                    break;
                default:
                    break;
            }
        }

    public void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }

        });
    }
};}