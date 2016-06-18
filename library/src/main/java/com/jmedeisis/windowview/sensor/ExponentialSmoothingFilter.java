package com.jmedeisis.windowview.sensor;

/**
 * Performs exponential smoothing with an exponentially-weighted moving average.
 * Analogous to an infinite-impulse-response, single-pole low-pass filter.
 */
public class ExponentialSmoothingFilter implements Filter {

    private float lastValue;
    /**
     * 0-1. See {@link #setSmoothingFactor(float)}.
     */
    private float factor;

    public ExponentialSmoothingFilter(float smoothingFactor, float initialValue) {
        this.factor = smoothingFactor;
        reset(initialValue);
    }

    /**
     * @param factor 0-1. Calculated as dt / (t + dt), where t is the system's time constant and dt
     *               is the sampling period, i.e. the rate that new values are delivered via
     *               {@link #push(float)}.
     *               The closer to 0, the greater the inertia, i.e. the filter responds more slowly
     *               to new input values.
     */
    public void setSmoothingFactor(float factor) {
        this.factor = factor;
    }

    @Override
    public void reset(float value) {
        lastValue = value;
    }

    /**
     * Pushes new sample to filter.
     *
     * @return new smoothed value.
     */
    @Override
    public float push(float value) {
        // do low-pass
        lastValue = lastValue + factor * (value - lastValue);
        return get();
    }

    /**
     * @return smoothed value.
     */
    @Override
    public float get() {
        return lastValue;
    }
}