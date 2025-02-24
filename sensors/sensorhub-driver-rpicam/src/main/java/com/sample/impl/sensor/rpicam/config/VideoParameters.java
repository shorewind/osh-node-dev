package com.sample.impl.sensor.rpicam.config;

import org.sensorhub.api.config.DisplayInfo;

public class VideoParameters {

    @DisplayInfo.Required
    @DisplayInfo(desc = "Width of video frames")
    public int videoFrameWidth = 1640;

    @DisplayInfo.Required
    @DisplayInfo(desc = "Height of video frames")
    public int videoFrameHeight = 1232;

    @DisplayInfo.Required
    @DisplayInfo(desc = "Rate of video frames")
    public int videoFrameRate = 30;

}
