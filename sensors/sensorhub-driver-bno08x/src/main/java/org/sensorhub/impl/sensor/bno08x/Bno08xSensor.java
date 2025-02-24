/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.bno08x;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;
import com.pi4j.plugin.pigpio.provider.i2c.PiGpioI2CProvider;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.digital.*;
//import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import net.opengis.sensorml.v20.ClassifierList;
import net.opengis.sensorml.v20.PhysicalSystem;
import net.opengis.sensorml.v20.SpatialFrame;
import net.opengis.sensorml.v20.Term;
import org.sensorhub.api.comm.ICommProvider;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.module.IModuleStateManager;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.vast.sensorML.SMLFactory;
import org.vast.swe.DataInputStreamLI;
import org.vast.swe.DataOutputStreamLI;
import org.vast.swe.SWEHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * <p>
 * Driver for BNO55 IMU
 * </p>
 *
 * @author Alex Robin
 * @since Apr 7, 2016
 */
public class Bno08xSensor extends AbstractSensorModule<Bno08xConfig>
{
    protected final static String CRS_ID = "SENSOR_FRAME";
    
    private final static String STATE_CALIB_DATA = "calib_data";
    
    private final static byte[] READ_CALIB_STAT_CMD =
    {
        Bno08xConstants.START_BYTE,
        Bno08xConstants.DATA_READ,
        Bno08xConstants.CALIB_STAT_ADDR,
        1
    };
    
    private final static byte[] READ_CALIB_DATA_CMD =
    {
        Bno08xConstants.START_BYTE,
        Bno08xConstants.DATA_READ,
        Bno08xConstants.CALIB_ADDR,
        Bno08xConstants.CALIB_SIZE
    };
    
    
    ICommProvider<?> commProvider;
    DataInputStreamLI dataIn;
    DataOutputStreamLI dataOut;
    Bno08xOutput dataInterface;
    byte[] calibData;
    Timer calibTimer;
    private Context pi4j;
    private I2C i2cDevice;
    
    public Bno08xSensor()
    {
    }


    @Override
    protected void doInit() throws SensorHubException
    {
        super.doInit();
        
        // generate identifiers: use serial number from config or first characters of local ID
        generateUniqueID("urn:bosch:bno08x:", config.serialNumber);
        generateXmlID("BOSCH_BNO08X_", config.serialNumber);
        
        // create main data interface
        dataInterface = new Bno08xOutput(this);
        addOutput(dataInterface, false);
        dataInterface.init();
    }


    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescLock)
        {
            super.updateSensorDescription();
            
            if (!sensorDescription.isSetDescription())
                sensorDescription.setDescription("Bosch BNO08X absolute orientation sensor");
            
            SMLFactory smlFac = new SMLFactory();
            ClassifierList classif = smlFac.newClassifierList();
            sensorDescription.getClassificationList().add(classif);
            Term term;
            
            term = smlFac.newTerm();
            term.setDefinition(SWEHelper.getPropertyUri("Manufacturer"));
            term.setLabel("Manufacturer Name");
            term.setValue("Bosch");
            classif.addClassifier(term);
            
            term = smlFac.newTerm();
            term.setDefinition(SWEHelper.getPropertyUri("ModelNumber"));
            term.setLabel("Model Number");
            term.setValue("bno08x");
            classif.addClassifier(term);
            
            SpatialFrame localRefFrame = smlFac.newSpatialFrame();
            localRefFrame.setId(CRS_ID);
            localRefFrame.setOrigin("Position of Accelerometers (as marked on the plastic box of the device)");
            localRefFrame.addAxis("X", "The X axis is in the plane of the circuit board");
            localRefFrame.addAxis("Y", "The Y axis is in the plane of the circuit board");
            localRefFrame.addAxis("Z", "The Z axis is orthogonal to circuit board, pointing outward from the component face");
            ((PhysicalSystem)sensorDescription).addLocalReferenceFrame(localRefFrame);
        }
    }


    @Override
    protected void doStart() throws SensorHubException
    {
        // init comm provider
        if (commProvider == null)
        {
            var pi4j = Pi4J.newAutoContext();
            I2CProvider i2cProvider = pi4j.provider("linuxfs-i2c");

            I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j)
                        .id("bno08x")
                        .bus(config.i2cBus)
                        .device(config.i2cAddress)
                        .build();

            try {
                i2cDevice = i2cProvider.create(i2cConfig);
                if (i2cDevice == null) {
                    throw new SensorHubException("Failed to initialize I2C device. Device is null.");
                }
                System.out.println("i2cDevice: " + i2cDevice);
            } catch (Exception e) {
                throw new SensorHubException("Error initializing I2C device", e);
            }

//            try
//            {
//                // we need to recreate comm provider here because it can be changed by UI
//                if (config.commSettings == null)
//                    throw new SensorHubException("No communication settings specified");
//
//                var moduleReg = getParentHub().getModuleRegistry();
//                commProvider = (ICommProvider<?>)moduleReg.loadSubModule(config.commSettings, true);
//                commProvider.start();
//            }
//            catch (Exception e)
//            {
//                commProvider = null;
//                throw e;
//            }


//            // connect to comm data streams
//            try
//            {
//                dataIn = new DataInputStreamLI(commProvider.getInputStream());
//                dataOut = new DataOutputStreamLI(commProvider.getOutputStream());
//                getLogger().info("Connected to IMU data stream");
//            }
//            catch (IOException e)
//            {
//                throw new RuntimeException("Error while initializing communications ", e);
//            }

            // send init commands
            try
            {
                reset();
                System.out.println("reset");

                setOperationMode(Bno08xConstants.OPERATION_MODE_CONFIG);
                System.out.println("op mode set");

                setPowerMode(Bno08xConstants.POWER_MODE_NORMAL);
                System.out.println("power mode set");

                setTriggerMode((byte)0);
                System.out.println("trigger mode set");


                // load saved calibration coefs
                if (calibData != null)
                {
                    loadCalibration();
                    System.out.println("calibration loaded");
                }

                setOperationMode(Bno08xConstants.OPERATION_MODE_NDOF);
                System.out.println("operation mode set");

                i2cDevice.write(Bno08xConstants.ENABLE_MAG_CMD);
                System.out.println("Magnetometer enabled?");
            }
            catch (Exception e)
            {
//                commProvider.stop();
//                commProvider = null;
                throw new SensorHubException("Error sending init commands", e);
            }

            // monitor calibration status
            if (getLogger().isTraceEnabled())
            {
                calibTimer = new Timer();
                System.out.println("calib timer");
                calibTimer.schedule(new TimerTask()
                {
                    public void run()
                    {
                        boolean calibOk = showCalibStatus();
                        if (calibOk)
                            cancel();
                    }
                }, 20L, 500L);
            }
        }

//        dataInterface.start(commProvider);
        dataInterface.start();
        System.out.println("data interface initialized");
    }
    
    
    protected void reset() throws IOException
    {
        byte[] resetCmd = new byte[] {
            Bno08xConstants.START_BYTE,
            Bno08xConstants.DATA_WRITE,
            Bno08xConstants.SYS_TRIGGER_ADDR,
            1,
            0x20
        };
        
//        sendWriteCommand(resetCmd, false);
        i2cDevice.write(resetCmd);
        try { Thread.sleep(650); }
        catch (InterruptedException e) { }
    }
    
    
    protected void setPowerMode(byte mode) throws IOException
    {
        setMode(Bno08xConstants.POWER_MODE_ADDR, mode);
    }
    
    
    protected void setTriggerMode(byte mode) throws IOException
    {
        setMode(Bno08xConstants.SYS_TRIGGER_ADDR, mode);
    }
    
    
    protected void setOperationMode(byte mode) throws IOException
    {
        setMode(Bno08xConstants.OPERATION_MODE_ADDR, mode);
        try { Thread.sleep(50); }
        catch (InterruptedException e) { }
    }
    
    
    protected void setMode(byte address, byte mode) throws IOException
    {
        // wait for previous command to complete
        try { Thread.sleep(30); }
        catch (InterruptedException e) { }
        
        // build command
        byte[] setModeCmd = new byte[] {
            Bno08xConstants.START_BYTE,
            Bno08xConstants.DATA_WRITE,
            address,
            1,
            mode
        };
        
//        sendWriteCommand(setModeCmd, true);
        i2cDevice.write(setModeCmd);
        
        // wait for mode switch to complete
        try { Thread.sleep(30); }
        catch (InterruptedException e) { }
    }
    
    
    protected boolean showCalibStatus()
    {
        try
        {
            ByteBuffer resp = sendReadCommand(READ_CALIB_STAT_CMD);
            
            // read calib status byte
            byte calStatus = resp.get();
            int sys = (calStatus >> 6) & 0x03;
            int gyro = (calStatus >> 4) & 0x03;
            int accel = (calStatus >> 2) & 0x03;
            int mag = calStatus & 0x03;
            
            getLogger().trace("Calib Status: sys={}, gyro={}, accel={}, mag={}", sys, gyro, accel, mag);
            
            if (sys == 3 && gyro == 3 && accel == 3 && mag == 3)
                return true;
        }
        catch (IOException e)
        {            
        }
        
        return false;
    }
    
    
    /* load calibration data to sensor */
    protected void loadCalibration() throws Exception
    {
        try
        {
            if (calibData.length != Bno08xConstants.CALIB_SIZE)
                throw new IOException("Calibration data must be " + Bno08xConstants.CALIB_SIZE + " bytes");
            
            // build command
            byte[] setCalCmd = new byte[4 + Bno08xConstants.CALIB_SIZE];
            setCalCmd[0] = Bno08xConstants.START_BYTE;
            setCalCmd[1] = Bno08xConstants.DATA_WRITE;
            setCalCmd[2] = Bno08xConstants.CALIB_ADDR;
            setCalCmd[3] = Bno08xConstants.CALIB_SIZE;
            System.arraycopy(calibData, 0, setCalCmd, 4, Bno08xConstants.CALIB_SIZE);
            
//            sendWriteCommand(setCalCmd, true);
            i2cDevice.write(setCalCmd);
            getLogger().debug("Loaded calibration data: {}", Arrays.toString(calibData));
        }
        catch (IOException e)
        {
            throw new SensorHubException("Error loading calibration table", e);
        }
    }
    
    
    /* read calibration data from sensor */
    protected void readCalibration()
    {
        try
        {
            setOperationMode(Bno08xConstants.OPERATION_MODE_CONFIG);
            
            ByteBuffer resp = sendReadCommand(READ_CALIB_DATA_CMD);
            calibData = resp.array();
            getLogger().debug("Read calibration data: {}", Arrays.toString(calibData));
        }
        catch (Exception e)
        {
            getLogger().error("Cannot read calibration data from sensor", e);
        }
    }


    protected synchronized ByteBuffer sendReadCommand(byte[] readCmd) throws IOException {
        // Write the read command to the I2C device
        System.out.println("sendReadCommand");
        i2cDevice.write(readCmd, 0, readCmd.length);

        // Check for ACK response (assuming ACK_BYTE is some predefined constant value for acknowledgment)
        byte[] ackBuffer = new byte[1];
        int bytesRead = i2cDevice.read(ackBuffer, 0, 1);  // Read 1 byte from the device
//        if (bytesRead != 1 || ackBuffer[0] != (Bno08xConstants.ACK_BYTE & 0xFF)) {
//            throw new IOException(String.format("Register Read Error: Expected ACK byte, got %02X", ackBuffer[0]));
//        }
        System.out.println("ackBuffer: " + byteArrayToHex(ackBuffer) + ", bytesRead: " + bytesRead);

        // Read the response length
        byte[] lengthBuffer = new byte[1];
        i2cDevice.read(lengthBuffer, 0, 1);
        int length = lengthBuffer[0] & 0xFF;
//        int length = 0x14;

        System.out.println("lengthBuffer: " + length);

        // Read the actual response data
        byte[] response = new byte[length];
        i2cDevice.read(response, 0, length);

        System.out.println("Raw Response Data: " + byteArrayToHex(response));

        // Wrap the response into a ByteBuffer with LITTLE_ENDIAN byte order
        ByteBuffer buf = ByteBuffer.wrap(response);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf;
    }

    // Helper method to convert byte array to a hex string for easier debugging
    private String byteArrayToHex(byte[] byteArray) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : byteArray) {
            hexString.append(String.format("%02X ", b));
        }
        return hexString.toString().trim();
    }

//    protected synchronized ByteBuffer sendReadCommand(byte[] readCmd) throws IOException
//    {
//        // flush any pending received data to get into a clean state
//        while (dataIn.available() > 0)
//            dataIn.read();
//
//        dataOut.write(readCmd);
//        dataOut.flush();
//
//        // check for error
//        int b0 = dataIn.read();
//        if (b0 != (Bno08xConstants.ACK_BYTE & 0xFF))
//            throw new IOException(String.format("Register Read Error: %02X", dataIn.read()));
//
//        // read response
//        int length = dataIn.read();
//        byte[] response = new byte[length];
//        dataIn.readFully(response);
//        ByteBuffer buf = ByteBuffer.wrap(response);
//        buf.order(ByteOrder.LITTLE_ENDIAN);
//        return buf;
//    }
    
    
//    protected synchronized void sendWriteCommand(byte[] writeCmd, boolean checkAck) throws IOException
//    {
//        int nAttempts = 0;
//        int maxAttempts = 5;
//        while (nAttempts < maxAttempts)
//        {
//            nAttempts++;
//
//            // flush any pending received data to get into a clean state
//            while (dataIn.available() > 0)
//                dataIn.read();
//
//            // write command to serial port
//            dataOut.write(writeCmd);
//            dataOut.flush();
//
//            // check ACK
//            if (checkAck)
//            {
//                int b0 = dataIn.read();
//                int b1 = dataIn.read();
//
//                if (b0 != (0xEE & 0xFF) || b1 != 0x01)
//                {
//                    String msg = String.format("Register Write Error: 0x%02X 0x%02X (%d)", b0, b1, nAttempts);
//                    if (b1 != 0x07 || nAttempts >= maxAttempts)
//                        throw new IOException(msg);
//                    getLogger().warn(msg);
//                    continue;
//                }
//            }
//
//            return;
//        }
//    }
    

    @Override
    public void loadState(IModuleStateManager loader) throws SensorHubException
    {
        super.loadState(loader);
        
        try
        {
            InputStream is = loader.getAsInputStream(STATE_CALIB_DATA);
            if (is != null)
            {
                calibData = new byte[22];
                int nBytes = is.read(calibData);
                if (nBytes != Bno08xConstants.CALIB_SIZE)
                    throw new IOException("Calibration data must be " + Bno08xConstants.CALIB_SIZE + " bytes");
            }
        }
        catch (Exception e) 
        {
            getLogger().error("Cannot load calibration data", e);
            calibData = null;
        }        
    }


    @Override
    public void saveState(IModuleStateManager saver) throws SensorHubException
    {
        super.saveState(saver);        
        
        if (calibData != null && calibData.length == Bno08xConstants.CALIB_SIZE)
        {
            try
            {
                OutputStream os = saver.getOutputStream(STATE_CALIB_DATA);
                os.write(calibData);
                os.flush();
            }
            catch (IOException e)
            {
                getLogger().error("Cannot save calibration data", e);
            }
        }
    }


    @Override
    protected void doStop() throws SensorHubException
    {
        if (calibTimer != null)
            calibTimer.cancel();
        
        if (dataInterface != null)
            dataInterface.stop();
                        
        if (commProvider != null)
        {
            readCalibration();
            commProvider.stop();
            commProvider = null;
        }
    }
    

    @Override
    public void cleanup() throws SensorHubException
    {       
    }
    
    
    @Override
    public boolean isConnected()
    {
        return (commProvider != null); // TODO also send ID command to check that sensor is really there
    }
}