package com.example.nikhil.backgroundservice;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity implements SensorEventListener {


    // View Parameters For All
    TextView lat_view, lng_view, acc_view, brk_view, bmp_view, turn_view;

    // Location Parameters
    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 1000 * 60;
    private static final float LOCATION_DISTANCE = 10f;
    Location lastLocation = null;
    private String startingLocation, endingLocation;
    Location mLastLocation;
    String lat, lng;

    // Sensors Declaration
    private Sensor accSensor;
    private Sensor gyroSensor;
    private Sensor magSensor;
    private Sensor orientationSensor;
    private Sensor rotationVectorSensor;
    private SensorManager SM;

    // Driving Behaviour Default Parameters
    private int sharpLeftTurnCounter = 0, sharpRightTurnCounter = 0, sharpBumpCounter = 0, sharpBreaksCounter = 0, sharpAccelerationCounter = 0;
    private boolean watchSharpTurn = false, watchSharpBump = false, watchSharpBreak = false, watchSharpAcceleration = false;
    private float sharpTurnThresholdValue = 6, sharpBumpThresholdValue = 3, sharpBreakThresholdValue = 6, sharpAccelerationThresholdValue = 4;
    public boolean eventCounter = false;
    public boolean tripStarted = true;

    // Calls and Messages
    private Context ctx;
    private TelephonyManager tm;
    private Integer incomingCall = 0, outGoingCall = 0, missedCall = 0, msgReceived = 0;
    long lastTsSt, lastTsBrk, lastTsBmp, lastTsAcc;
    private CallStateListener callStateListener;
    private OutgoingReceiver outgoingReceiver;
    private boolean ring = false, callRecieved = false;
    //private SMSBroadcastReceiver smsBroadcastReceiver;


    // Azimuth , Roll, Pitch
    public double rvX = 0, Azimuth = 0, Pitch = 0, Roll = 0;
    public int accX = 0, accY = 0, accZ = 0;
    private int xp = 0, yp = 0, zp = 0, xr = 0, yr = 0, zr = 0, xa = 0, ya = 0, za = 0;
    int[] azimuthBuff = new int[100];
    int azimuthBuffIndex = 0;

    // JSON Objects and Arrays
    private JSONArray locationArr = new JSONArray();
    private JSONArray orientArr = new JSONArray();
    JSONObject orientSensorLogger = new JSONObject();
    JSONObject localData = new JSONObject();
    JSONObject accSensorLogger = new JSONObject();
    JSONObject magSensorLogger = new JSONObject();
    JSONObject gyroSensorLogger = new JSONObject();

    // Date Time Variables
    SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm");
    String currDate = dateFormat.format(new Date());


    // Misc. Default values
    String mPackageName;
    private String  mobileNumber;
    boolean b = false, trackerFlag = false;
    private float speed = 0;
    private float[] speedArr = new float[200];
    public float distanceTravelled = 0;
    float lastDistance = 0;
    float avgSpeed = 0;
    float maxSpeed = 0;
    String startTime, endTime;
    ActivityManager mActivityManager;
    private int previousAddress = 0;
    private int bufferList = 50, settlement = 0;
    public static final String PREFS_NAME = "NativeStorage";

    // Files variables
    public String fileDir;

    JSONObject finalData = new JSONObject();
    private String localDataFileExt = ".txt";
    private String localDataFileName = "user" + localDataFileExt;
    private String accelerometerSensorLoggerFilename = "acc.log" + localDataFileExt;
    private String gyroscopeSensorLoggerFilename = "gyro.log" + localDataFileExt;
    private String magnetometerSensorLoggerFilename = "mag.log" + localDataFileExt;
    private String localDataDir = "com.liv.nikhil";
    private String logFileDir = localDataDir + "/";
    private String localDataRoot = Environment.getExternalStorageDirectory().getPath() + "/";
    File directoryToZip = new File(localDataRoot + localDataDir + "/" + currDate);
    List<File> fileList = new ArrayList<File>();


    //////////////////////////////   Location Service Code /////////////////////////////////////////

    private class LocationListener implements android.location.LocationListener {

        public LocationListener(String provider) {
            mLastLocation = new Location(provider);
            Log.d("Location", " mLastLocation line160 " + mLastLocation);
        }

        @Override
        public void onLocationChanged(Location loc) {
            try {

                mLastLocation.set(loc);
                Log.d("Location", " mLastLocation line169 " + mLastLocation);

                lat = Double.toString(loc.getLatitude());
                lng = Double.toString(loc.getLongitude());
                Log.d("Location", " line172 lat: " + lat + " , lng: " + lng);
                lat_view.setText(lat);
                lng_view.setText(lng);

                new SendRequest().execute("foo", "bar");


                if(lastLocation == null) {
                    lastLocation = loc;
                }

                lastDistance = loc.distanceTo(lastLocation); // Distance in meters
                lastDistance = lastDistance / 1000f; // To convert distance in kms.
                distanceTravelled = distanceTravelled + lastDistance;

                speed = (lastDistance * 1000 * 60 * 60) / (LOCATION_INTERVAL);

                Log.d("Speed", speed + " , last Location: " + lastLocation);

                if(lastLocation != null && speed >= 0) {
                    if(!trackerFlag) {
                        startingLocation = Double.toString( mLastLocation.getLatitude() ) + "," + Double.toString( mLastLocation.getLongitude() );
                        startTime = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                        trackerFlag = true;

                        distanceTravelled = 0;
                        sharpBreaksCounter = 0;
                        sharpAccelerationCounter = 0;
                        sharpLeftTurnCounter = 0;
                        sharpBumpCounter = 0;
                        sharpRightTurnCounter = 0;
                        avgSpeed = 0;
                        endTime = "";
                        endingLocation = "";
                        maxSpeed = 0;
                        missedCall = 0;
                        incomingCall = 0;
                        outGoingCall = 0;

                    }
                    else {
                        locationArr.put(lastLocation);

                        for(int a = 0; a < 200; a++) {
                            speedArr[a] = speed;
                        }
                        if(speed > maxSpeed) {
                            maxSpeed = speed;
                        }
                        long timeInMillis = new Date().getTime();
                        JSONObject speedObj = new JSONObject();
                        speedObj.put("speed", speed);
                        speedObj.put("timestamp", timeInMillis);

                        try {
                            File root = new File(localDataRoot, logFileDir + currDate);
                            File filepath;
                            // if external memory exists and folder with name com.livechek.drivetell
                            if (!root.exists()) {
                                root.mkdirs(); // this will create folder.
                            }
                            Log.d("dateFormat", dateFormat + "");
                            filepath = new File(root, "speed" + localDataFileExt); // file path to save
                            FileWriter writer = new FileWriter(filepath, true);

                            writer.append(speedObj.toString() + ",");
                            writer.flush();
                            writer.close();
                        }
                        catch (IOException e) {
                            Log.e("DT Error", e + "");
                            e.printStackTrace();
                        }

                        for(int i=0; i <= speedArr.length; i++) {
                            avgSpeed = avgSpeed + speedArr[i];
                        }
                        avgSpeed = avgSpeed / speedArr.length;

                        Log.d("Logistics", "" + distanceTravelled + ", " + speed + ", " + avgSpeed + ", " + maxSpeed);

                    }
                }
                // else {
                //     trackerFlag = false;
                // }

                if(trackerFlag && lastDistance < 1) {
                    trackerFlag = false;
                    tripStarted = false;
                }
                lastLocation.set(loc);

            }
            catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    }

    public class SendRequest extends AsyncTask<String, Void,String> {

        protected void onPreExecute() {}
        @Override
        protected String doInBackground(String... strings) {
            try {
                mobileNumber = "919991814802";
                JSONObject location = new JSONObject();
                location.put("lat", lat);
                location.put("lng", lng);
                Log.d("Location", " location line160:  " + location);
                URL url = new URL("https://api.dev.livechek.com/users/" + mobileNumber);
                Log.d("Location", " url line105:  " + url);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(location.toString());
                writer.flush();
                writer.close();

                int responseCode=conn.getResponseCode();

                if (responseCode == HttpsURLConnection.HTTP_OK) {

                    BufferedReader in=new BufferedReader(
                            new InputStreamReader(
                                    conn.getInputStream()));
                    StringBuffer sb = new StringBuffer("");
                    String line;

                    while((line = in.readLine()) != null) {

                        sb.append(line);
                        break;
                    }

                    in.close();
                    return sb.toString();

                }
                else {
                    return new String("false : "+responseCode);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return new String(e.getMessage());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return new String(e.getMessage());
            } catch (ProtocolException e) {
                e.printStackTrace();
                return new String(e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                return new String(e.getMessage());
            } catch (JSONException e) {
                e.printStackTrace();
                return new String(e.getMessage());
            }
        }
        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(getApplicationContext(), result,
                    Toast.LENGTH_LONG).show();
        }
    }

    android.location.LocationListener[] mLocationListeners = new android.location.LocationListener[] {
            new LocationListener(LocationManager.PASSIVE_PROVIDER),
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    /////////////////////////////// Phone calls and Messages Record ////////////////////////////////

    /**
     * Listener to detect incoming calls.
     */
    private class CallStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    // called when someone is ringing to this phone

                    Toast.makeText(ctx,
                            "Incoming: "+incomingNumber,
                            Toast.LENGTH_LONG).show();
                    ring = true;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    callRecieved = true;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if(ring== true && callRecieved == false) {
                        missedCall += 1;
                    }
                    if(ring == true && callRecieved == true) {
                        incomingCall += 1;
                    }
            }
        }
    }

    /**
     * Broadcast receiver to detect the outgoing calls.
     */
    public class OutgoingReceiver extends BroadcastReceiver {
        public OutgoingReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

            Toast.makeText(ctx,
                    "Outgoing: "+number,
                    Toast.LENGTH_LONG).show();
            outGoingCall += 1;
        }

    }

//    public class SMSBroadcastReceiver extends BroadcastReceiver {
//
//        private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
//        private static final String TAG = "SMSBroadcastReceiver";
//
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            Log.i(TAG, "Intent received: " + intent.getAction());
//
//            if (intent.getAction().equals(SMS_RECEIVED)) {
//                msgReceived += 1;
//            }
//        }
//    }

    /**
     * Start calls detection.
     */
    public void startCallListen() {
        try {
            tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);

            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_NEW_OUTGOING_CALL);
            ctx.registerReceiver(outgoingReceiver, intentFilter);
        }
        catch (Exception e) {
            Log.d("Error", e + "");
            e.printStackTrace();
        }

    }

    /**
     * Stop calls detection.
     */
    public void stopCallListen() {
        tm.listen(callStateListener, PhoneStateListener.LISTEN_NONE);
        ctx.unregisterReceiver(outgoingReceiver);
    }

    ////////////////////////////// Trip Start and Stop /////////////////////////////////////////////

    public void startTrip() {
        startCallListen();
        startingLocation = Double.toString( mLastLocation.getLatitude() ) + "," + Double.toString( mLastLocation.getLongitude() );
        startTime = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        trackerFlag = true;

        distanceTravelled = 0;
        sharpBreaksCounter = 0;
        sharpAccelerationCounter = 0;
        sharpLeftTurnCounter = 0;
        sharpBumpCounter = 0;
        sharpRightTurnCounter = 0;
        avgSpeed = 0;
        endTime = "";
        endingLocation = "";
        maxSpeed = 0;
        missedCall = 0;
        incomingCall = 0;
        outGoingCall = 0;
        // Create our Sensor manager
        SM = (SensorManager) getSystemService(SENSOR_SERVICE);
        accSensor = SM.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroSensor = SM.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magSensor = SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        orientationSensor = SM.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        rotationVectorSensor = SM.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        logDataIntoFile(localDataFileName);

        SM.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
        SM.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        SM.registerListener(this, accSensor, 1000000);
        SM.registerListener(this, magSensor,1000000);
        SM.registerListener(this, gyroSensor, 1000000);
    }

    public void stopTrip() {
        endingLocation = Double.toString( mLastLocation.getLatitude() ) + "," + Double.toString( mLastLocation.getLongitude() );
        endTime = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        //stopCallListen();
        // trackerFlag = false;
        tripStarted = true;
        // Creating Zip file
        getAllFiles(directoryToZip, fileList);
        writeZipFile(directoryToZip, fileList);
        getTripFinalFile();

    }



    ////////////////////////////// Main Service Start Code /////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String value = getValue(MainActivity.this, "user", null);

        initializeLocationManager();

        lat_view = (TextView)findViewById(R.id.textView_lat);
        lng_view = (TextView)findViewById(R.id.textView_lng);

        acc_view = (TextView)findViewById(R.id.acc_View);
        brk_view = (TextView)findViewById(R.id.brk_View);
        bmp_view = (TextView)findViewById(R.id.bump_View);
        turn_view = (TextView)findViewById(R.id.turn_View);

        SM = (SensorManager) getSystemService(SENSOR_SERVICE);
        accSensor = SM.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroSensor = SM.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magSensor = SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        orientationSensor = SM.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        rotationVectorSensor = SM.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        logDataIntoFile(localDataFileName);

        SM.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
        SM.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        SM.registerListener(this, accSensor, 1000000);
        SM.registerListener(this, magSensor,1000000);
        SM.registerListener(this, gyroSensor, 1000000);

        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    if(tripStarted == false && trackerFlag == false) {
                        stopTrip();
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        };
        timer.schedule(timerTask, 0, 5000);
//
//        try {
//            JSONObject valueObj = new JSONObject(value);
//            Log.d("Hello", " ==> user Mobile number --> " + valueObj.getString("mobile"));
//            mobileNumber = valueObj.getString("mobile");
//            // JSONObject data = valueObj.getJSONObject("mobile");
//            Log.d("My Location", "mobileNumber ==> " + mobileNumber);
//        }
//        catch (JSONException e) {
//            e.printStackTrace();
//        }


        try {

            mLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, LOCATION_INTERVAL,
                    LOCATION_DISTANCE, mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i("DRIVE", "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d("DRIVE", "network provider does not exist, " + ex.getMessage());
        }


    }

    private void initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    String getValue(Context context, String key, String defaultValue) {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Log.d("CurrDate", ""+settings.getString(key, defaultValue));
        return settings.getString(key, defaultValue);
    }



    ////////////////////////////// Acc, Gyro, Mag, Orientation Sensors Code /////////////////////////////////////////

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        long timeInMillis = (new Date()).getTime() + (sensorEvent.timestamp - System.nanoTime()) / 1000000L;

        if(sensorEvent.sensor == accSensor) {
            Log.d("Sensor", " Inside AccSensor ");
            float accelerometer_X = sensorEvent.values[0];
            float accelerometer_Y = sensorEvent.values[1];
            float accelerometer_Z = sensorEvent.values[2];

            accX = (int) accelerometer_X;
            accY = (int) accelerometer_Y;
            accZ = (int) accelerometer_Z;

            axisTransform();

            accelerometer_X = xa;
            accelerometer_Y = ya;
            accelerometer_Z = za;


            // Sharp Turns Logic

            if(accelerometer_X > sharpTurnThresholdValue && !watchSharpTurn) {
                Log.d("Sensor","Inside SharpTurn");
                watchSharpTurn = true;
                settlement = 10;
                if(!eventCounter) {
                    lastTsSt = timeInMillis;
                    sharpRightTurnCounter ++;
                    paramLocationMapping("sharpTurn",lastTsSt);
                    resetAndLogData();
                } else {
                    sharpTurnThresholdValue += 0.5;
                }
            }
            else if(accelerometer_X < -sharpTurnThresholdValue && !watchSharpTurn ) {
                Log.d("Sensor", "Inside SharpTurn");
                watchSharpTurn = true;
                settlement = 10;
                if(!eventCounter) {
                    sharpLeftTurnCounter ++;
                    paramLocationMapping("sharpTurn",lastTsSt);
                    resetAndLogData();
                } else {
                    sharpTurnThresholdValue += 0.5;
                }
            }
            else if( accelerometer_X < sharpTurnThresholdValue && accelerometer_X > -sharpTurnThresholdValue ) {
                watchSharpTurn = false;
            }

            if (timeInMillis - lastTsSt > 300000 & sharpTurnThresholdValue > 4) {
                sharpTurnThresholdValue -= 1;
            }

            // Sharp Bumps Logic
            if( accelerometer_Z < sharpBumpThresholdValue && !watchSharpBump ) {
                lastTsBmp = timeInMillis;
                watchSharpBump = true;
                settlement = 10;
                if(!eventCounter) {
                    sharpBumpCounter ++;
                    paramLocationMapping("bump", lastTsBmp);
                    resetAndLogData();
                } else {
                    sharpBumpThresholdValue += 0.5;
                }
            }
            else if( accelerometer_Z > sharpBumpThresholdValue ) {
                watchSharpBump = false;
            }

            if (timeInMillis - lastTsBmp > 300000 & sharpBumpThresholdValue > 2) {
                sharpBumpThresholdValue -= 1;
            }

            // Sharp Break Logic
            if( accelerometer_Y < -sharpAccelerationThresholdValue && !watchSharpBreak ) {
                watchSharpBreak = true;
                settlement = 10;
                if(!eventCounter) {
                    lastTsBrk = timeInMillis;
                    paramLocationMapping("break", lastTsBrk);
                    sharpBreaksCounter ++;
                    resetAndLogData();
                } else {
                    sharpBreakThresholdValue += 0.5;
                }
            }

            else if( accelerometer_Y < sharpBreakThresholdValue && accelerometer_Y > -sharpBreakThresholdValue ) {
                watchSharpBreak = false;
            }

            if (timeInMillis - lastTsBrk > 300000 && sharpBreakThresholdValue > 4) {
                sharpBreakThresholdValue -= 1;
            }

            // Sharp Acceleration Logic
            if( accelerometer_Y < -sharpAccelerationThresholdValue && !watchSharpAcceleration ) {
                watchSharpAcceleration = true;
                settlement = 10;
                if(!eventCounter) {
                    lastTsAcc = timeInMillis;
                    sharpAccelerationCounter ++;
                    paramLocationMapping("acc" ,lastTsAcc);
                    resetAndLogData();
                } else {
                    sharpAccelerationThresholdValue += 0.5;
                }
            }
            else if( accelerometer_Y > -sharpAccelerationThresholdValue ) {
                watchSharpAcceleration = false;
            }

            if (timeInMillis - lastTsAcc > 300000 && sharpAccelerationThresholdValue > 3) {
                sharpAccelerationThresholdValue -= 1;
            }

            if( accelerometer_X > 15 ) {
                Log.d("Sensor", "SOS");
            }

            Log.d("Sensor", " Behaviour :  " + sharpAccelerationCounter + ", " + sharpBreaksCounter + ", " + sharpBumpCounter + ", " + sharpLeftTurnCounter + ", " + sharpRightTurnCounter);

            try {
                accSensorLogger.put("X", accelerometer_X);
                accSensorLogger.put("Y", accelerometer_Y);
                accSensorLogger.put("Z", accelerometer_Z);
                accSensorLogger.put("timestamp", timeInMillis);

                try {
                    File root = new File(localDataRoot, logFileDir + currDate);
                    File filepath;

                    if (!root.exists()) {
                        root.mkdirs(); // this will create folder.
                    }

                    filepath = new File(root, currDate + "." + accelerometerSensorLoggerFilename);
                    FileWriter writer = new FileWriter(filepath, true);
                    writer.append(accSensorLogger.toString() + ",");
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
        }

        acc_view.setText(String.valueOf(sharpAccelerationCounter));
        brk_view.setText(String.valueOf(sharpBreaksCounter));
        bmp_view.setText(String.valueOf(sharpBumpCounter));
        turn_view.setText(String.valueOf(sharpLeftTurnCounter + sharpRightTurnCounter));

        if( sensorEvent.sensor == rotationVectorSensor ) {
            rvX = sensorEvent.values[0];
        }

        if( sensorEvent.sensor == orientationSensor ) {
            if( sensorEvent.values[0] > 0 ) {
                Azimuth = 360 - ( 180 - sensorEvent.values[0] );
            } else {
                Azimuth = 360 - sensorEvent.values[0];
            }

            if( sensorEvent.values[1] < 0 ) {
                Pitch = ( 360 + sensorEvent.values[1] );
            } else {
                Pitch = sensorEvent.values[1];
            }

            if( sensorEvent.values[2] < 0 && rvX >= -0.5 ) {
                Roll = 360 + sensorEvent.values[2];
            } else if ( sensorEvent.values[2] < 0 && rvX >= -0.9999 ) {
                Roll = 180 - sensorEvent.values[2];
            }

            if( rvX >= 0.5 ) {
                Roll = 90 + sensorEvent.values[2];
            } else {
                Roll = sensorEvent.values[2];
            }

            // Building Azimuth Buffer to get positive value
            azimuthBuff[azimuthBuffIndex++] = (int) Azimuth;

            azimuthBuffIndex = azimuthBuffIndex % 100;

            Azimuth = Azimuth - getPopularElement(azimuthBuff);
            if (Azimuth < 0)
                Azimuth = 360 + Azimuth;

            try {
                orientSensorLogger.put("Azimuth", Azimuth);
                orientSensorLogger.put("Pitch", Pitch);
                orientSensorLogger.put("Roll", Roll);
                orientSensorLogger.put("timestamp", timeInMillis);
            }
            catch (JSONException e) {
                e.printStackTrace();
            }

            bufferList(orientSensorLogger);
        }

        if( sensorEvent.sensor == magSensor ) {
            float magnetometer_X = sensorEvent.values[0];
            float magnetometer_Y = sensorEvent.values[1];
            float magnetometer_Z = sensorEvent.values[2];

            try {
                magSensorLogger.put("X", magnetometer_X);
                magSensorLogger.put("Y", magnetometer_Y);
                magSensorLogger.put("Z", magnetometer_Z);
                magSensorLogger.put("timestamp", timeInMillis);

                try {
                    File root = new File(localDataRoot, logFileDir + currDate);
                    File filepath;
                    // if external memory exists and folder with name com.livechek.drivetell
                    if (!root.exists()) {
                        root.mkdirs(); // this will create folder.
                    }

                    filepath = new File(root, currDate + "." + magnetometerSensorLoggerFilename); // file path to save
                    FileWriter writer = new FileWriter(filepath, true);

                    writer.append(magSensorLogger.toString() + ",");
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if( sensorEvent.sensor == gyroSensor ) {
            float gyroscope_X = sensorEvent.values[0];
            float gyroscope_Y = sensorEvent.values[1];
            float gyroscope_Z = sensorEvent.values[2];

            try {
                gyroSensorLogger.put("X", gyroscope_X);
                gyroSensorLogger.put("Y", gyroscope_Y);
                gyroSensorLogger.put("Z", gyroscope_Z);

                try {
                    File root = new File(localDataRoot, logFileDir + currDate);
                    File filepath;
                    // if external memory exists and folder with name com.livechek.drivetell
                    if (!root.exists()) {
                        root.mkdirs(); // this will create folder.
                    }
                    Log.d("dateFormat", dateFormat + "");
                    filepath = new File(root, currDate + "." + gyroscopeSensorLoggerFilename); // file path to save
                    FileWriter writer = new FileWriter(filepath, true);

                    writer.append(gyroSensorLogger.toString() + ",");
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    public void resetAndLogData() {
        eventCounter = true;
        resetEventCounter();
        logDataIntoFile(localDataFileName);
    }

    public void resetEventCounter() {
        new android.os.Handler().postDelayed(new Runnable() {
            public void run() {
                if (settlement <= 0) {
                    eventCounter = false;
                    settlement = 0;
                }
                settlement--;

            }
        }, 1000);
    }

    public void bufferList( JSONObject data ) {
        if( previousAddress >= 50 ) {
            orientArr.put(data);
            bufferList += 1;
        }
        else {
            bufferList = 0;
        }
    }

    public int getPopularElement( int[] a ) {
        int count = 1, tempCount;
        int popular = a[0];
        int temp = 0;
        for( int i = 0; i < (a.length - 1); i++ ) {
            temp = a[i];
            tempCount = 0;
            for( int j = 0; j < a.length; j++ ) {
                if( temp == a[j] ) {
                    tempCount ++;
                }
            }
            if( tempCount > count ) {
                popular = temp;
                count = tempCount;
            }
        }
        return popular;
    }

    public void axisTransform() {

        Log.d("Sensor", "Res Degree ---> line286 IN " + Pitch + ", " + Roll + ", " + Azimuth);
        // Convert Azimuth, Pitch and Roll to radians
        Azimuth = Math.toRadians(Azimuth);
        Pitch = Math.toRadians(Pitch);
        Roll = Math.toRadians(Roll);
        Log.d("Sensor", "Res Degree ---> line291 IN " + Pitch + ", " + Roll + ", " + Azimuth);

        // axis of pitch
        xp = accX;
        yp = (int) Math.ceil(( accY * Math.cos(Pitch) - accZ * Math.sin(Pitch) ));
        zp = (int) Math.ceil(( accY * Math.sin(Pitch) - accZ * Math.cos(Pitch) ));

        // axis of roll
        xr = (int) Math.ceil(( zp * Math.sin(Roll) + xp * Math.cos(Roll) ));
        yr = yp;
        zr = (int) Math.ceil(( zp * Math.cos(Roll) - xp * Math.sin(Roll) ));

        // axis of Azimuth
        xa = (int) Math.ceil(( xr * Math.cos(Azimuth) - yr * Math.sin(Azimuth) ));
        ya = (int) Math.ceil(( xr * Math.sin(Azimuth) + yr * Math.cos(Azimuth) ));
        za = zr;

        Log.d("Sensor", "Res ---> line309 OUT " + xa + ", " + ya + ", " + za);
    }



    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    ///////////////////////////////// Log Files for Location of Driving Behaviour /////////////////////////////////

    public void paramLocationMapping(String name, long time) {
        try {
            JSONObject locOfAction = new JSONObject();
            locOfAction.put("name", name);
            locOfAction.put("time", time);
            locOfAction.put("location", lastLocation);

            try {
                File root = new File(localDataRoot, logFileDir + currDate);
                File filepath;
                // if external memory exists and folder with name com.livechek.drivetell
                if (!root.exists()) {
                    root.mkdirs(); // this will create folder.
                }
                Log.d("dateFormat", dateFormat + "");
                filepath = new File(root, currDate + ".loc" + localDataFileExt); // file path to save
                FileWriter writer = new FileWriter(filepath, true);

                writer.append(locOfAction.toString() + ",");
                writer.flush();
                writer.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        catch(JSONException e) {
            e.printStackTrace();
        }
    }

    public void logDataIntoFile(String fileName) {

        try {
            localData.put("sharpTurns", Integer.toString(sharpLeftTurnCounter + sharpRightTurnCounter));
            localData.put("sharpBumps", Integer.toString(sharpBumpCounter));
            localData.put("sharpBreaks", Integer.toString(sharpBreaksCounter));
            localData.put("sharpAccelerations", Integer.toString(sharpAccelerationCounter));
            localData.put("speed", Float.toString(speed));
            localData.put("avgSpeed", Float.toString(avgSpeed));
            localData.put("maxSpeed", Float.toString(maxSpeed));
            localData.put("TripStart", Boolean.toString(trackerFlag));
            localData.put("distance", Float.toString(distanceTravelled));
            localData.put("missedCall", missedCall);
            localData.put("incomingCall", incomingCall);
            localData.put("outGoingCall", outGoingCall);
            localData.put("folder", currDate);

            //localData.put( "startLocation", startingLocation );

            Log.d("DRIVE"," < == > "+ speed + ", " + avgSpeed + ", " + maxSpeed + ", " + trackerFlag);

            try {
                File root = new File(localDataRoot, localDataDir);
                File filepath;
                // if external memory exists and folder with name com.livechek.drivetell
                if (!root.exists()) {
                    root.mkdirs(); // this will create folder.
                }

                filepath = new File(root, fileName); // file path to save
                FileWriter writer = new FileWriter(filepath);

                writer.append(localData.toString());
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();

            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Log.d("OUTPUT: ", localData.toString() );
    }



    //////////////////////////////// Log Files Zipping /////////////////////////////////////////////

    public static void getAllFiles(File dir, List<File> fileList) {
        try {
            File[] files = dir.listFiles();
            for (File file : files) {
                fileList.add(file);
                if (file.isDirectory()) {
                    Log.d("DRIVE", "directory:" + file.getCanonicalPath());
                    getAllFiles(file, fileList);
                } else {
                    Log.d("DRIVE", "     file:" + file.getCanonicalPath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeZipFile(File directoryToZip, List<File> fileList) {
        // Log.d("Inside writeZipFile function < === >", directoryToZip + "");
        try {
            //  Log.d("DRIVE", directoryToZip.getName() + ".zip");
            fileDir = localDataRoot + logFileDir;
            Log.d("DRIVE", "file path is : " + fileDir + directoryToZip.getName() + ".zip");
            FileOutputStream fos = new FileOutputStream(fileDir + directoryToZip.getName() + ".zip");
            //Log.d("DRIVE", fos + "");
            ZipOutputStream zos = new ZipOutputStream(fos);

            for (File file : fileList) {
                if (!file.isDirectory()) { // we only zip files, not directories
                    addToZip(directoryToZip, file, zos);
                }
            }

            zos.close();
            fos.close();
        } catch (FileNotFoundException e) {
            //Log.d("DRIVE", e + " File not found 546");
            e.printStackTrace();
        } catch (IOException e) {
            // Log.d("DRIVE", e + " == 2 549");
            e.printStackTrace();
        }
    }

    public static void addToZip(File directoryToZip, File file, ZipOutputStream zos)
            throws FileNotFoundException, IOException {

        //Log.d("DRIVE", "Yes reached here");
        FileInputStream fis = new FileInputStream(file);

        // we want the zipEntry's path to be a relative path that is relative
        // to the directory being zipped, so chop off the rest of the path
        String zipFilePath = file.getCanonicalPath().substring(directoryToZip.getCanonicalPath().length() + 1,
                file.getCanonicalPath().length());
        Log.d("DRIVE", "Writing '" + zipFilePath + "' to zip file");
        ZipEntry zipEntry = new ZipEntry(zipFilePath);
        zos.putNextEntry(zipEntry);

        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zos.write(bytes, 0, length);
        }

        zos.closeEntry();
        fis.close();
    }


    public void getTripFinalFile() {
        try {
            finalData.put("sharpTurns", Integer.toString(sharpLeftTurnCounter + sharpRightTurnCounter));
            finalData.put("sharpBumps", Integer.toString(sharpBumpCounter));
            finalData.put("sharpBreaks", Integer.toString(sharpBreaksCounter));
            finalData.put("sharpAccelerations", Integer.toString(sharpAccelerationCounter));
            finalData.put("avgSpeed", Float.toString(avgSpeed));
            finalData.put("maxSpeed", Float.toString(maxSpeed));
            finalData.put("distance", Float.toString(distanceTravelled));
            finalData.put("startLocation", startingLocation);
            finalData.put("endingLocation", endingLocation);
            finalData.put("startTime", startTime);
            finalData.put("endTime", endTime);
            finalData.put("missedCall", missedCall);
            finalData.put("incomingCall", incomingCall);
            finalData.put("outGoingCall", outGoingCall);
            finalData.put( "startLocation", startingLocation );
            finalData.put("endLocation", endingLocation);

            try {
                File root = new File(localDataRoot, localDataDir);
                File filepath;
                // if external memory exists and folder with name com.livechek.drivetell
                if (!root.exists()) {
                    root.mkdirs(); // this will create folder.
                }

                filepath = new File(root, currDate + ".txt"); // file path to save
                FileWriter writer = new FileWriter(filepath);

                writer.append(localData.toString());
                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /////////////////////////// Check App running in foreground //////////////////////////////////
//    private void printForegroundTask() {
//        String currentApp = "NULL";
//        try {
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
//                UsageStatsManager usm = (UsageStatsManager) this.getSystemService(Context.USAGE_STATS_SERVICE);
//                long time = System.currentTimeMillis();
//                List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 1000,
//                        time);
//                if (appList != null && appList.size() > 0) {
//                    SortedMap<Long, UsageStats> mySortedMap = new TreeMap<Long, UsageStats>();
//                    for (UsageStats usageStats : appList) {
//                        mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
//                    }
//                    if (mySortedMap != null && !mySortedMap.isEmpty()) {
//                        currentApp = mySortedMap.get(mySortedMap.lastKey()).getPackageName();
//                    }
//                }
//            } else {
//                ActivityManager am = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
//                List<ActivityManager.RunningAppProcessInfo> tasks = am.getRunningAppProcesses();
//                currentApp = tasks.get(0).processName;
//            }
//        } catch (Exception e) {
//            Log.e("DRIVE", "Current App in foreground is: " + e.toString());
//            e.printStackTrace();
//        }
//
//        Log.e("DRIVE", "Current App in foreground is: " + currentApp);
//    }



}
