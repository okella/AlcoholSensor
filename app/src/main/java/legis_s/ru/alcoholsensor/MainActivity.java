package legis_s.ru.alcoholsensor;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "LOGDEV";
    private static final int INTERVAL_BETWEEN_BLINKS_MS = 100;
    // GPIO Pin Name
    private static final String GPIO_NAME = "BCM18";
    private SpiDevice spiDevice;
    private Gpio mGpio;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PeripheralManagerService manager = new PeripheralManagerService();
        List<String> portList = manager.getGpioList();
        if (portList.isEmpty()) {
            Log.i(TAG, "No GPIO port available on this device.");
        } else {
            Log.i(TAG, "List of available ports: " + portList);
            try {
                mGpio = manager.openGpio(GPIO_NAME);
                configureInput(mGpio);

            } catch (IOException e) {
                Log.w(TAG, "Unable to access GPIO", e);
            }
        }
        List<String> spiBusList = manager.getSpiBusList();
        Log.i(TAG, "SPI Bus List in the device "+spiBusList.size());

        if (spiBusList.size() <=0)
        {
            Log.i(TAG, "Sorry your device does not support SPI");
            return;
        }

        try {

            Log.i(TAG, "Open SPI Device");
            spiDevice = manager.openSpiDevice(spiBusList.get(0));
            Log.i(TAG, "SPI Device configuration");
            configureSpiDevice(spiDevice);
            mHandler.post(deviceReadThread);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }


    }
    /* By default we have selected channel 0 in the ADC
     * Before reading data we need to write about the channel which we will be reading from.
     * */
    private Runnable deviceReadThread = new Runnable() {
        @Override
        public void run() {
            // Exit Runnable if the GPIO is already closed
            if (spiDevice == null) {
                return;
            }
            try {

              //  Log.i(TAG,"Reading from the SPI");

                byte[] data=new byte[3];
                byte[] response=new byte[3];
                data[0]=1;
                int a2dChannel=0;
                data[1]= (byte) (8 << 4);
                data[2] = 0;

                //full duplex mode
                spiDevice.transfer(data,response,3);

                int a2dVal = 0;
                a2dVal = (response[1]<< 8) & 0b1100000000; //merge data[1] & data[2] to get result
                a2dVal |=  (response[2] & 0xff);

                Log.i(TAG,String.valueOf(a2dVal));
              //  pulseSensor.process(a2dVal);

                mHandler.postDelayed(deviceReadThread, INTERVAL_BETWEEN_BLINKS_MS);
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    };
    public void configureSpiDevice(SpiDevice device) throws IOException {
        // Low clock, leading edge transfer
        device.setMode(SpiDevice.MODE0);
        device.setBitsPerWord(8);          // 8 BPW
        device.setFrequency(1000000);     // 1MHz
        device.setBitJustification(false); // MSB first
    }
    public void configureInput(Gpio gpio) throws IOException {
        // Initialize the pin as an input
        gpio.setDirection(Gpio.DIRECTION_IN);
        // High voltage is considered active
        gpio.setActiveType(Gpio.ACTIVE_HIGH);

        // Register for all state changes
        gpio.setEdgeTriggerType(Gpio.EDGE_BOTH);
        gpio.registerGpioCallback(mGpioCallback);
    }
    private GpioCallback mGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            // Read the active low pin state
            try {
                if (gpio.getValue()) {
                    Log.i(TAG, "Pin is LOW");
                    // Pin is LOW
                } else {
                    // Pin is HIGH
                    Log.i(TAG, "Pin is HIGH");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Continue listening for more interrupts
            return true;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Log.w(TAG, gpio + ": Error event " + error);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        // Begin listening for interrupt events
        try {
            mGpio.registerGpioCallback(mGpioCallback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Interrupt events no longer necessary
        mGpio.unregisterGpioCallback(mGpioCallback);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(deviceReadThread);

        if (mGpio != null) {
            try {
                mGpio.close();
                mGpio = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close GPIO", e);
            }
        }
        if (spiDevice != null) {
            try {
                spiDevice.close();
                spiDevice = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close SPI device", e);
            }
        }
    }
}