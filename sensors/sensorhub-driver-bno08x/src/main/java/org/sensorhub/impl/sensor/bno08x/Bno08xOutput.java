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

import net.opengis.swe.v20.*;
import org.sensorhub.api.comm.ICommProvider;
import com.pi4j.io.i2c.*;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.bno08x.Bno08xSensor;
import org.vast.swe.DataInputStreamLI;
import org.vast.swe.DataOutputStreamLI;
import org.vast.swe.helper.GeoPosHelper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;


/**
 * <p>
 * Implementation of the absolute orientation quaternion output
 * </p>
 *
 * @author Alex Robin
 * @since Apr 7, 2016
 */
public class Bno08xOutput extends AbstractSensorOutput<Bno08xSensor>
{
    private final static byte[] READ_QUAT_CMD =
    {
        Bno08xConstants.START_BYTE,
        Bno08xConstants.DATA_READ,
        Bno08xConstants.QUAT_DATA_ADDR,
        0x08
    };

    private final static byte[] ENABLE_MAG_CMD = {
            0x07, // Set Feature Report ID
            0x2E, // Magnetometer Report ID
            0x00, 0x00, 0x20, 0x00, // Report interval (20Hz)
            0x00, 0x00, 0x00, 0x00  // Reserved
    };

    private final static byte[] READ_ACCEL_CMD =
    {
            Bno08xConstants.START_BYTE,       // Start byte
            Bno08xConstants.DATA_READ,        // Read command
            Bno08xConstants.ACCEL_DATA_ADDR,  // Address for Accelerometer data
            0x08                              // Number of bytes to read (typically 8 for XYZ data)
    };

//    private final static byte[] READ_MAG_CMD =
//    {
//            Bno08xConstants.START_BYTE,       // Start byte
//            Bno08xConstants.DATA_READ,        // Read command
//            Bno08xConstants.MAG_DATA_ADDR,    // Address for Magnetometer data
//            0x08                              // Number of bytes to read (typically 8 for XYZ data)
//    };

    private final static byte[] READ_MAG_CMD =
    {
            (byte) 0xFA
    };

    private final static double QUAT_SCALE = 1<<14;
    
    
    DataComponent imuData;
    DataEncoding dataEncoding;
    Timer timer;
    DataInputStreamLI dataIn;
    DataOutputStreamLI dataOut;
    
    int decimFactor = 1;
    int sampleCounter;
    float temp;
    float[] gyro = new float[3];
    float[] accel = new float[3];
    float[] mag = new float[4];
    float[] quat = new float[4];


    public Bno08xOutput(Bno08xSensor parentSensor)
    {
        super("imuData", parentSensor);
    }


    protected void init()
    {
        GeoPosHelper fac = new GeoPosHelper();
        
        // build SWE Common record structure
        imuData = fac.newDataRecord(4);
        imuData.setName(getName());
        imuData.setDefinition("http://sensorml.com/ont/swe/property/ImuData");
        
        String localFrame = parentSensor.getUniqueIdentifier() + "#" + Bno08xSensor.CRS_ID;
                        
        // time stamp
        imuData.addComponent("time", fac.newTimeStampIsoUTC());
        
        // raw inertial measurements
        /*Vector angRate = fac.newAngularVelocityVector(
                SWEHelper.getPropertyUri("AngularRate"),
                localRefFrame,
                "deg/s");
        angRate.setDataType(DataType.FLOAT);
        imuData.addComponent("angRate", angRate);*/
        
//        Vector accel = fac.newAccelerationVector(
//                SWEHelper.getPropertyUri("Acceleration"),
//                localRefFrame,
//                "m/s2");
//        accel.setDataType(DataType.FLOAT);
//        imuData.addComponent("accel", accel);
        
        // integrated measurements
        //fac.newEulerOrientationENU(def)
        Vector quat = fac.newQuatOrientationENU(null);
        quat.setDataType(DataType.FLOAT);
        quat.setLocalFrame(localFrame);
        imuData.addComponent("attitude", quat);
     
        // also generate encoding definition as text block
        dataEncoding = fac.newTextEncoding(",", "\n");        
    }
    

//    /* TODO: only using HV message; add support for HT and ML */
//    private void pollAndSendMeasurement()
//    {
//    	long msgTime = System.currentTimeMillis();
//        System.out.println("msg time: " + msgTime);
//
//        // decode message
//	    try
//        {
//            ByteBuffer resp = parentSensor.sendReadCommand(READ_QUAT_CMD);
//
//            System.out.println("quat resp " + resp);
//
//            // read 4 quaternion components (scalar first)
//            quat[3] = (float)(resp.getShort() / QUAT_SCALE);
//            for (int i=0; i<3; i++)
//                quat[i] = (float)(resp.getShort() / QUAT_SCALE);
//        }
//        catch (IOException e)
//        {
//            // skip measurement if there is a bus error
//            System.out.println("error: " + e);
//            System.out.println("error while sending quat: " + e.getMessage());
//            return;
//        }
//
//        // create and populate datablock
//    	DataBlock dataBlock;
//    	if (latestRecord == null)
//    	    dataBlock = imuData.createDataBlock();
//    	else
//    	    dataBlock = latestRecord.renew();
//
//    	int k = 0;
//        dataBlock.setDoubleValue(k++, msgTime / 1000.);
//        /*for (int i=0; i<3; i++, k++)
//            dataBlock.setFloatValue(k, gyro[i]);
//        for (int i=0; i<3; i++, k++)
//            dataBlock.setFloatValue(k, accel[i]);*/
//        for (int i=0; i<4; i++, k++)
//            dataBlock.setFloatValue(k, quat[i]);
//
//        // update latest record and send event
//        latestRecord = dataBlock;
//        System.out.println("latest record " + latestRecord);
//        latestRecordTime = msgTime;
//        eventHandler.publish(new DataEvent(latestRecordTime, Bno08xOutput.this, dataBlock));
//    }

    private void pollAndSendMeasurement()
    {
        long msgTime = System.currentTimeMillis();
        System.out.println("Polling sensor at time: " + msgTime);

        try {
            ByteBuffer resp = parentSensor.sendReadCommand(READ_MAG_CMD);

            // Print raw response for debugging
            System.out.print("Raw Magnetometer Data: ");
            while (resp.hasRemaining()) {
                System.out.printf("0x%02X ", resp.get());
            }
            System.out.println();

            // Reset buffer position to read again
            resp.rewind();

            // Parse magnetometer data (3 axes)
            for (int i = 0; i < 3; i++) {
                mag[i] = (float) (resp.getShort() / 16.0); // Scale factor based on datasheet
            }

            System.out.println("Magnetometer Data: X=" + mag[0] + " µT, Y=" + mag[1] + " µT, Z=" + mag[2] + " µT");
        } catch (IOException e) {
            System.err.println("I/O Error while reading magnetometer: " + e.getMessage());
            e.printStackTrace();
        }
//        try
//        {
//            // Send read command
//            ByteBuffer resp = parentSensor.sendReadCommand(READ_MAG_CMD);
//
//            // Print raw response for debugging
//            System.out.print("Raw Response: ");
//            while (resp.hasRemaining()) {
//                System.out.printf("0x%02X ", resp.get());
//            }
//            System.out.println(); // Newline after raw data
//
//            // Reset buffer position to read again
//            resp.rewind();
//
//            // Read 4 quaternion components (scalar first)
//            quat[3] = (float)(resp.getShort() / QUAT_SCALE);
//            for (int i = 0; i < 3; i++)
//                quat[i] = (float)(resp.getShort() / QUAT_SCALE);
//
//            System.out.println("Quaternion Data: [" + quat[0] + ", " + quat[1] + ", " + quat[2] + ", " + quat[3] + "]");
//        }
//        catch (IOException e)
//        {
//            System.err.println("I/O Error while reading quaternion: " + e.getMessage());
//            e.printStackTrace();  // Print full error stack for debugging
//            return;
//        }

        // Create and populate data block
        DataBlock dataBlock;
        if (latestRecord == null)
            dataBlock = imuData.createDataBlock();
        else
            dataBlock = latestRecord.renew();

//        int k = 0;
//        dataBlock.setDoubleValue(k++, msgTime / 1000.);
//        for (int i = 0; i < 4; i++, k++)
//            dataBlock.setFloatValue(k, quat[i]);

        int k = 0;
        dataBlock.setDoubleValue(k++, msgTime / 1000.);
        for (int i = 0; i < 4; i++, k++)
            dataBlock.setFloatValue(k, mag[i]);

        // Update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = msgTime;
        eventHandler.publish(new DataEvent(latestRecordTime, Bno08xOutput.this, dataBlock));

        System.out.println("Published data event.");
    }



    //    protected void start(ICommProvider<?> commProvider)
    protected void start()
    {
        sampleCounter = -1;
//        dataIn = parentSensor.dataIn;
//        dataOut = parentSensor.dataOut;
        
        // start main measurement thread
        TimerTask t = new TimerTask()
        {
            public void run()
            {
                pollAndSendMeasurement();
                System.out.println("polled and measured");
            }
        };
        
        timer = new Timer();
        timer.schedule(t, 0, (long)(getAverageSamplingPeriod()*1000));
    }


    protected void stop()
    {
    	if (timer != null)
    	{
	        timer.cancel();
	        timer = null;
    	}
    }


    @Override
    public double getAverageSamplingPeriod()
    {
    	return 1;
    }


    @Override
    public DataComponent getRecordDescription()
    {
        return imuData;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return dataEncoding;
    }
}
